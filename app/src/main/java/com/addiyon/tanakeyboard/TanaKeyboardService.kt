package com.addiyon.tanakeyboard

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.addiyon.tanakeyboard.composing.WordComposer
import com.addiyon.tanakeyboard.model.EnterAction
import com.addiyon.tanakeyboard.model.NumbersMode
import com.addiyon.tanakeyboard.model.ShiftState
import com.addiyon.tanakeyboard.suggestion.WordDictionary
import com.addiyon.tanakeyboard.suggestion.matchCase
import com.addiyon.tanakeyboard.transliteration.Transliterator
import com.addiyon.tanakeyboard.ui.settings.KeyboardPrefs
import com.addiyon.tanakeyboard.ui.theme.KeyboardPalette

/**
 * Max chips in the Amharic suggestion strip: the live word's readings plus
 * dictionary completions. The strip scrolls horizontally, so this can be
 * generous.
 */
private const val AMHARIC_SUGGESTION_LIMIT = 10

class TanaKeyboardService : InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    // ----------------------------
    // Lifecycle (UNCHANGED)
    // ----------------------------
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    // ----------------------------
    // KEYBOARD STATE
    // ----------------------------

    var isAmharic by mutableStateOf(true)
        private set

    var numbersMode by mutableStateOf(NumbersMode.OFF)
        private set

    val isNumberMode: Boolean
        get() = numbersMode != NumbersMode.OFF

    // The single source of truth for shift/caps-lock. isShiftEnabled below
    // is a derived convenience for callers that only care about "capitalize
    // or not" and don't need to distinguish one-shot shift from caps lock.
    var shiftState by mutableStateOf(ShiftState.OFF)
        private set

    val isShiftEnabled: Boolean
        get() = shiftState != ShiftState.OFF

    // What the Enter key should show and do in the current field, derived from
    // its IME action (see [resolveEnterAction], refreshed per input session in
    // onStartInputView). [editorActionId] is the raw EditorInfo action to fire
    // via performEditorAction when [enterAction] isn't a plain NEWLINE.
    var enterAction by mutableStateOf(EnterAction.NEWLINE)
        private set

    private var editorActionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED

    // Tracked manually instead of relying on Compose's isSystemInDarkTheme(),
    // because an InputMethodService's window doesn't reliably deliver
    // configuration updates into the Compose tree the way an Activity does.
    // We read the current mode on creation and again whenever
    // onConfigurationChanged fires, so the keyboard UI can react to the
    // system dark/light toggle even while it's open.
    var isDarkTheme by mutableStateOf(false)
        private set

    // The selected color palette, read from the same SharedPreferences the
    // settings UI writes. Observable so the hosted keyboard recomposes when
    // it changes. See [refreshTheme].
    var palette by mutableStateOf(KeyboardPalette.CLASSIC)
        private set

    // Registered in onCreate / unregistered in onDestroy. Fires when the user
    // changes the theme in the app (same process -> same prefs instance), so
    // the keyboard recolors live even while it's open (e.g. the in-app Test
    // Keyboard screen). Lifecycle-boundary refreshTheme() calls are the
    // fallback that guarantees correctness regardless.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyboardPrefs.KEY_PALETTE) {
                refreshTheme(resources.configuration)
            }
        }

    /**
     * Up to 3 word completions (Amharic or English, whichever mode is
     * active) for whatever's currently composing, highest-frequency first --
     * empty whenever there's nothing to suggest (buffer empty, dictionary
     * still loading, or no match). Recomputed by [updateSuggestions] after
     * every composer mutation.
     */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    // ----------------------------
    // WORD COMPOSITION
    // ----------------------------
    //
    // One composer per language; only the one matching the current mode is
    // ever fed keystrokes, and every mode transition commits the active one
    // first, so at most one has a non-empty buffer at any time.
    //
    // Both are fed a lambda, not a reference. currentInputConnection
    // changes identity between input sessions (each new field the user
    // taps into gets a fresh one), so we always re-read it at the moment
    // of use -- same reasoning as the KeyboardScreen comment about not
    // capturing an InputConnection at composition time.
    private val amharicComposer = WordComposer(
        inputConnection = { currentInputConnection },
        // The field shows the raw Latin the user types (composingText =
        // identity), while render produces the fidel used for the suggestion
        // strip and for commit -- so "sh" is visible inline and its readings
        // (ስህ, ሽ, …) are offered in the strip. Backspace uses the default
        // one-char step so each typed letter can be cleared individually.
        render = Transliterator::transliterate,
        composingText = { it }
    )

    private val englishComposer = WordComposer(
        inputConnection = { currentInputConnection }
    )

    private val activeComposer: WordComposer
        get() = if (isAmharic) amharicComposer else englishComposer

    // Built in onCreate(), not as property initializers here -- Context
    // isn't safely usable (applicationContext etc.) until attachBaseContext
    // has run, which happens after this class's own construction but
    // before onCreate().
    private lateinit var amharicDictionary: WordDictionary
    private lateinit var englishDictionary: WordDictionary

    /**
     * Re-derives [suggestions] from the active composer's current buffer.
     *
     * Amharic is keyed off the LIVE TRANSLITERATED fidel prefix (exactly
     * what's already shown in the composing region), not the raw Latin
     * buffer -- reversing fidel back to a Latin spelling to look words up
     * would be ambiguous, since the forward transliteration mapping isn't
     * reliably invertible. See
     * [com.addiyon.tanakeyboard.suggestion.WordTrie]'s class doc for the
     * full reasoning.
     *
     * English lookups are lowercased (the dictionary stores every entry
     * lowercase) and the user's typed case pattern is restored on the way
     * out, so "Th" suggests "The", not "the".
     */
    private fun updateSuggestions() {
        if (!::amharicDictionary.isInitialized) {
            suggestions = emptyList()
            return
        }
        suggestions = if (isAmharic) {
            amharicSuggestions()
        } else {
            val typed = englishComposer.display
            englishDictionary.suggestions(typed.lowercase()).map { matchCase(typed, it) }
        }
    }

    /**
     * Amharic suggestions: the live word's own readings first, then dictionary
     * completions of each, as one scrollable strip.
     *
     * The field shows the raw Latin being typed; the strip offers its fidel
     * readings, and [suggestions]`[0]` (the greedy reading) is what space
     * commits in its place. The leading entries are [Transliterator.readings]:
     * the greedy reading, plus the alternate-form and digraph-split readings
     * when the buffer is ambiguous (so "sh" leads with ሽ, ስህ and "s" leads
     * with ስ, ሠ; "r" is unambiguous and leads with just ር). Every reading is
     * then used as a dictionary
     * prefix -- so an ambiguous buffer searches BOTH ሽ… and ስህ… words -- and
     * the completions are appended, deduped, capped at [AMHARIC_SUGGESTION_LIMIT].
     */
    private fun amharicSuggestions(): List<String> {
        val latin = amharicComposer.raw
        if (latin.isEmpty()) return emptyList()

        val readings = Transliterator.readings(latin)
        val completions = readings.flatMap { amharicDictionary.suggestions(it, AMHARIC_SUGGESTION_LIMIT) }
        return (readings + completions).distinct().take(AMHARIC_SUGGESTION_LIMIT)
    }

    /**
     * Re-derives [isDarkTheme] from the system night flag and [palette] from
     * the saved preference, then refreshes the nav-bar tint (which depends on
     * both). Light/dark follows the system; only the color palette is user-
     * selectable, and it themes just the keyboard.
     */
    private fun refreshTheme(configuration: Configuration) {
        palette = KeyboardPrefs.palette(this)
        val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        updateSystemNavigationBar()
    }

    /**
     * Colors the system navigation bar area beneath the keyboard (the strip
     * that hosts the "hide keyboard" / "switch input method" affordances)
     * to match the keyboard's own tray color, and flips the icon color to
     * match. Without the icon-appearance part, those icons stay a fixed
     * light/white color regardless of background, so they can disappear
     * against a light tray.
     *
     * Also disables the automatic contrast scrim Android draws over the
     * navigation bar (API 29+) -- otherwise the system overlays its own
     * translucent tint on top of whatever color we set, which throws the
     * match off again.
     */
    private fun updateSystemNavigationBar() {
        val color = palette.tray(isDarkTheme)

        window?.window?.let { imeWindow ->
            imeWindow.navigationBarColor = color.toArgb()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imeWindow.isNavigationBarContrastEnforced = false
            }

            // true = dark icons for a light background, false = light icons
            // for a dark background.
            WindowInsetsControllerCompat(imeWindow, imeWindow.decorView)
                .isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    /**
     * Opens the app's UI from the keyboard, jumping straight to the given
     * screen. Themes and the typing guide each have their own Activity so
     * their screen IS the first frame -- no brief flash of [MainActivity]'s
     * home on the way there; everything else goes through [MainActivity].
     * Needs NEW_TASK because we're launching from a Service context, not an
     * Activity.
     */
    fun openAppScreen(screen: String) {
        val target = when (screen) {
            MainActivity.SCREEN_THEMES -> ThemesActivity::class.java
            MainActivity.SCREEN_GUIDE -> ManualActivity::class.java
            else -> MainActivity::class.java
        }
        val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target == MainActivity::class.java) {
            intent.putExtra(MainActivity.EXTRA_OPEN_SCREEN, screen)
        }
        startActivity(intent)
    }

    /** AI assist entry point from the suggestion toolbar. Not wired up yet. */
    fun onAiAction() {
        // TODO: hook up AI feature.
    }

    /** Clipboard entry point from the suggestion toolbar. Not wired up yet. */
    fun onClipboardAction() {
        // TODO: hook up clipboard panel.
    }

    /**
     * Opens the standalone [FeedbackActivity] from the keyboard toolbar's
     * feedback icon (which used to pop an in-keyboard bottom sheet). Launched
     * from a Service context, so it needs NEW_TASK.
     */
    fun openFeedbackScreen() {
        val intent = Intent(this, FeedbackActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    fun toggleLanguage() {
        activeComposer.commit()
        isAmharic = !isAmharic
        if (!isAmharic && numbersMode == NumbersMode.GEEZ_NUMBERS) {
            numbersMode = NumbersMode.NUMBERS
        }
        updateSuggestions()
    }

    /**
     * Toggles between the letter layout (Amharic or English, whichever is
     * active) and the numbers/symbols page. Flushes the composer first, for
     * the same reason [toggleLanguage] does -- a composing word belongs
     * to the mode it started in, and numbers/symbols are never part of one.
     *
     * Always lands on [NumbersMode.NUMBERS] from a letter layout, and always
     * exits all the way to [NumbersMode.OFF] from EITHER numeric page -- so
     * "ABC" returns straight to letters from the second symbols page too,
     * without having to step back through the first page.
     */
    fun toggleNumberMode() {
        activeComposer.commit()
        numbersMode = if (numbersMode == NumbersMode.OFF) NumbersMode.NUMBERS else NumbersMode.OFF
        updateSuggestions()
    }

    fun toggleSymbolsPage() {
        numbersMode = when (numbersMode) {
            NumbersMode.NUMBERS -> if (isAmharic) NumbersMode.GEEZ_NUMBERS else NumbersMode.SYMBOLS
            NumbersMode.GEEZ_NUMBERS -> NumbersMode.SYMBOLS
            NumbersMode.SYMBOLS -> NumbersMode.NUMBERS
            NumbersMode.OFF -> NumbersMode.OFF
        }
    }

    /**
     * Cycles the shift key: OFF -> SHIFT -> CAPS_LOCK -> OFF.
     * Tapping shift once capitalizes the next letter only; tapping it again
     * before typing anything locks caps on until shift is tapped a third
     * time.
     */
    fun toggleShift() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.SHIFT
            ShiftState.SHIFT -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    /**
     * Called after a character key commits its output. One-shot SHIFT
     * consumes itself and returns to OFF; CAPS_LOCK is left untouched since
     * it should keep capitalizing until explicitly turned off.
     */
    fun consumeShiftAfterCharacter() {
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
        }
    }

    fun resetShift() {
        shiftState = ShiftState.OFF
    }

    // ----------------------------
    // KEY HANDLERS (called from the UI)
    // ----------------------------
    //
    // Everything the UI does now goes through these methods rather than
    // poking currentInputConnection directly. Two reasons:
    //
    //   1. In Amharic mode the buffer is stateful -- a single keypress no
    //      longer maps to a single commitText, and only the service can
    //      keep that state consistent.
    //   2. Even for the "trivial" keys (space, enter, delete), routing
    //      through here means the composer gets a chance to flush its
    //      buffer at the right boundary before the raw action fires.

    /**
     * A character key was pressed. [latin] is the base spelling the key
     * carries (e.g. "S" for the S key). We resolve shift here so callers
     * (the UI) don't need to know about the composer or shift state.
     *
     * On the letter layouts, both languages compose: Amharic because a
     * keypress is ambiguous until the syllable ends, English so the current
     * word stays replaceable by a tapped suggestion. Word-terminating keys
     * ("." and ",", the only non-word keys on either letter layout) and
     * everything on the numeric pages commit directly -- flushing the
     * composer first, so "hello" + "." lands as "hello." rather than
     * swallowing the dot into the word buffer (where it could never match
     * a dictionary entry). In Amharic mode the punctuation itself is
     * transliterated on the way out ("," -> ፣, "." -> ።) -- the same
     * [Transliterator] call the key's corner preview shows, so the two
     * can't disagree.
     */
    fun onCharacter(latin: String) {
        val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

        when {
            isNumberMode -> currentInputConnection?.commitText(output, 1)
            !isWordCharacter(output) -> {
                activeComposer.commit()
                val text = if (isAmharic) Transliterator.transliterate(output) else output
                currentInputConnection?.commitText(text, 1)
            }
            isAmharic -> amharicComposer.onCharacter(output)
            else -> englishComposer.onCharacter(output)
        }

        consumeShiftAfterCharacter()
        updateSuggestions()
    }

    /**
     * Whether [output] belongs inside a composing word rather than
     * terminating one. Letters in both languages; apostrophe (English
     * contractions, and the SERA spelling of the glottal አ family) and
     * backtick (SERA pharyngeal ዐ) also count -- neither is on a letter
     * layout today, but if one is ever added it must feed the composer,
     * not chop the word.
     */
    private fun isWordCharacter(output: String) =
        output.all { it.isLetter() || it == '\'' || it == '`' }

    /**
     * Backspace pressed. Try to shrink the active composing buffer first --
     * one full rendered character at a time, which in Amharic can be a
     * multi-Latin-char span (so "she" -> ሸ, backspace -> nothing). If the
     * buffer is empty, fall back to deleting a character from the text
     * field itself.
     */
    fun onDelete() {
        if (activeComposer.onBackspace()) {
            updateSuggestions()
            return
        }
        val ic = currentInputConnection
        if (ic != null) {
            // If the user has a selection, backspace should delete the whole
            // selection. deleteSurroundingText ignores the selection and would
            // instead delete a character just before it, leaving the selected
            // text untouched -- so replace the selection with empty text
            // (commitText on a selection removes it). Only fall back to
            // deleting one preceding character when nothing is selected.
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        updateSuggestions()
    }

    fun commitText(text: String) {
        activeComposer.commit()
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Space commits any in-flight word first, then inserts a space.
     *
     * [WordComposer.commit] replaces the composing region with the rendered
     * word: for Amharic that swaps the underlined Latin for the greedy fidel
     * reading (the same as suggestions[0]), so space picks the default reading
     * and a tap is only needed for a NON-default one; for English it just
     * finalizes the composed word. With no word in flight it's a plain space.
     */
    fun onSpace() {
        activeComposer.commit()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
    }

    /**
     * Enter commits the current word first (so a form submission sees the
     * completed word, not a half-composed one), then either runs the field's
     * IME action (search/go/send/next/...) via [performEditorAction] or, for a
     * plain/multi-line field, sends a literal newline key event. Which one is
     * decided by [enterAction], resolved for the current field in
     * [onStartInputView].
     */
    fun onEnter() {
        activeComposer.commit()
        val ic = currentInputConnection
        if (enterAction == EnterAction.NEWLINE) {
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            ic?.performEditorAction(editorActionId)
        }
        updateSuggestions()
    }

    /**
     * Resolves how the Enter key should present and behave for the current
     * field from its `EditorInfo`. A multi-line field, or one that opts out of
     * an enter action (`IME_FLAG_NO_ENTER_ACTION`), gets a plain newline;
     * otherwise the declared IME action (GO/SEARCH/SEND/NEXT/PREVIOUS/DONE)
     * drives both the key's icon and what Enter fires.
     */
    private fun resolveEnterAction(editorInfo: EditorInfo?) {
        if (editorInfo == null) {
            enterAction = EnterAction.NEWLINE
            editorActionId = EditorInfo.IME_ACTION_UNSPECIFIED
            return
        }
        val imeOptions = editorInfo.imeOptions
        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        val multiline = editorInfo.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0

        editorActionId = actionId
        val declaredAction = when (actionId) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            else -> EnterAction.NEWLINE
        }
        // An explicitly declared IME action wins over the multi-line flag.
        // Some single-line search boxes (e.g. Reddit's) set the multi-line
        // input flag yet still declare IME_ACTION_SEARCH; the old
        // `multiline || noEnterAction -> NEWLINE` order swallowed the action
        // and inserted a literal newline, so search was impossible. Now the
        // multi-line flag only forces a newline when the field declares NO
        // action of its own. IME_FLAG_NO_ENTER_ACTION still opts out entirely.
        enterAction = when {
            noEnterAction -> EnterAction.NEWLINE
            declaredAction != EnterAction.NEWLINE -> declaredAction
            multiline -> EnterAction.NEWLINE
            else -> EnterAction.NEWLINE
        }
    }

    /**
     * A suggestion chip was tapped: swap the current composing text for the
     * full suggested word and clear the strip.
     */
    fun onSuggestionTapped(word: String) {
        activeComposer.commitSuggestion(word)
        updateSuggestions()
    }

    // ----------------------------
    // IME LIFECYCLE
    // ----------------------------

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        updateSystemNavigationBar()

        return TanaKeyboardView(this)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        refreshTheme(resources.configuration)
        KeyboardPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)

        amharicDictionary = WordDictionary(this, "amharic_words.dat")
        englishDictionary = WordDictionary(this, "english_words.dat")
        // Parsing the dictionary lines (~182k Amharic, ~47k English) happens
        // off the main thread; if the user starts typing before a load
        // finishes, suggestions just start appearing once loadAsync's
        // callback lands (main thread, per WordDictionary's contract).
        amharicDictionary.loadAsync { updateSuggestions() }
        englishDictionary.loadAsync { updateSuggestions() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshTheme(newConfig)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        // A new input session means a new InputConnection -- any half-typed
        // word we were composing belongs to a field that's no longer
        // ours. Drop it silently rather than trying to commit into the
        // wrong destination.
        amharicComposer.reset()
        englishComposer.reset()
        // The Enter key adapts to this field's IME action (search/go/send/...).
        resolveEnterAction(editorInfo)
        updateSuggestions()

        // Catch any theme change that happened while the keyboard was hidden,
        // and make sure the nav bar strip is colored correctly every time
        // the keyboard becomes visible again.
        refreshTheme(resources.configuration)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * The framework calls this whenever the cursor or selection changes in
     * the target field -- both when WE change it (by pushing composing text)
     * and when the USER changes it (by tapping somewhere else). If the new
     * cursor is inside the composing region the framework is tracking, the
     * movement is consistent with our own edits and we ignore it. If the
     * cursor has landed outside that region, the user has visibly walked
     * away from the word we were composing, so we freeze it in place and
     * drop the buffer -- otherwise the next keystroke would keep rewriting
     * a region that's no longer near the caret.
     *
     * candidatesStart / candidatesEnd are the framework's view of the
     * current composing region; both are -1 when nothing is being composed.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            candidatesStart, candidatesEnd
        )

        if (!activeComposer.isComposing) return

        // A selection (start != end) inside our region also counts as "the
        // user took over" -- we don't support composing across a selection.
        val cursorInsideComposing = newSelStart == newSelEnd &&
                candidatesStart >= 0 &&
                newSelStart in candidatesStart..candidatesEnd

        if (!cursorInsideComposing) {
            activeComposer.abandon()
            updateSuggestions()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Field is going away -- finalize the composing region IN PLACE (no
        // new text inserted). Using commit()/commitText here duplicated the
        // word, because the framework also finalizes the still-active
        // composing region as the session ends. See WordComposer.finish().
        activeComposer.finish()
        updateSuggestions()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        KeyboardPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}