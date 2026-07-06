package com.addiyon.tanakeyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
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
import com.addiyon.tanakeyboard.model.NumbersMode
import com.addiyon.tanakeyboard.model.ShiftState
import com.addiyon.tanakeyboard.suggestion.WordDictionary
import com.addiyon.tanakeyboard.suggestion.matchCase
import com.addiyon.tanakeyboard.transliteration.Transliterator
import com.addiyon.tanakeyboard.ui.theme.KeyboardColors

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

    var isAmharic by mutableStateOf(false)
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

    // Tracked manually instead of relying on Compose's isSystemInDarkTheme(),
    // because an InputMethodService's window doesn't reliably deliver
    // configuration updates into the Compose tree the way an Activity does.
    // We read the current mode on creation and again whenever
    // onConfigurationChanged fires, so the keyboard UI can react to the
    // system dark/light toggle even while it's open.
    var isDarkTheme by mutableStateOf(false)
        private set

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
        render = Transliterator::transliterate,
        lastUnitStart = Transliterator::lastUnitStart
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
            amharicDictionary.suggestions(amharicComposer.display)
        } else {
            val typed = englishComposer.display
            englishDictionary.suggestions(typed.lowercase()).map { matchCase(typed, it) }
        }
    }

    private fun updateDarkThemeFromConfiguration(configuration: Configuration) {
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
        val color = if (isDarkTheme) KeyboardColors.trayDark else KeyboardColors.trayLight

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

    fun toggleLanguage() {
        // Any half-typed word belongs to the language it was started in --
        // flush it before switching (i.e. while activeComposer still points
        // at the outgoing language), so the user doesn't end up with an
        // orphaned partial word in the wrong pipeline.
        activeComposer.commit()
        isAmharic = !isAmharic
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

    /**
     * Toggles between the two numeric pages ("123" <-> "=\<"). Only ever
     * called from a key that's rendered on one of those pages, so the
     * [NumbersMode.OFF] branch is unreachable in practice -- kept so the
     * `when` stays exhaustive. No composer to flush: a composing word
     * can't exist while already in a numeric mode.
     */
    fun toggleSymbolsPage() {
        numbersMode = when (numbersMode) {
            NumbersMode.NUMBERS -> NumbersMode.SYMBOLS
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
     * on the English layout ("." and ",") and everything on the numeric
     * pages commit directly -- flushing the composer first, so "hello" +
     * "." lands as "hello." rather than swallowing the dot into the word
     * buffer (where it could never match a dictionary entry).
     */
    fun onCharacter(latin: String) {
        val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

        when {
            isNumberMode -> currentInputConnection?.commitText(output, 1)
            isAmharic -> amharicComposer.onCharacter(output)
            output.all { it.isLetter() || it == '\'' } -> englishComposer.onCharacter(output)
            else -> {
                englishComposer.commit()
                currentInputConnection?.commitText(output, 1)
            }
        }

        consumeShiftAfterCharacter()
        updateSuggestions()
    }

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
        currentInputConnection?.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    /**
     * Space commits any in-flight word first, then inserts a space.
     * Committing first is what lets the space actually terminate the word
     * rather than getting swallowed into the composing region.
     */
    fun onSpace() {
        activeComposer.commit()
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
    }

    /**
     * Enter commits the current word (so a form submission sees the
     * completed word, not a half-composed one) and then dispatches
     * the actual key event.
     */
    fun onEnter() {
        activeComposer.commit()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
        updateSuggestions()
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
        updateDarkThemeFromConfiguration(resources.configuration)

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
        updateDarkThemeFromConfiguration(newConfig)
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
        updateSuggestions()

        // Catch any theme change that happened while the keyboard was hidden,
        // and make sure the nav bar strip is colored correctly every time
        // the keyboard becomes visible again.
        updateDarkThemeFromConfiguration(resources.configuration)
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
        // Field is going away -- lock whatever we have into it before the
        // InputConnection dies.
        activeComposer.commit()
        updateSuggestions()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}