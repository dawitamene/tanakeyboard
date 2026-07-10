package com.addiyon.keyboard

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.StrictMode
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.addiyon.keyboard.composing.WordComposer
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.emoji.EmojiBackspace
import com.addiyon.keyboard.emoji.EmojiRepository
import com.addiyon.keyboard.emoji.RecentEmojiStore
import com.addiyon.keyboard.emoji.SkinToneStore
import com.addiyon.keyboard.suggestion.WordDictionary
import com.addiyon.keyboard.suggestion.WordTrie
import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.suggestion.matchCase
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.voice.VoiceComposer
import com.addiyon.keyboard.voice.VoiceErrorKind
import com.addiyon.keyboard.voice.VoiceInputController
import com.addiyon.keyboard.voice.VoiceUiState
import com.addiyon.keyboard.voice.isVoiceMode

/**
 * Max chips in the Amharic suggestion strip: the live word's readings plus
 * dictionary completions. The strip scrolls horizontally, so this can be
 * generous.
 */
private const val AMHARIC_SUGGESTION_LIMIT = 10

/**
 * English strip capacity: exact-prefix completions first, then up to
 * [ENGLISH_FUZZY_LIMIT] typo corrections appended below them.
 */
private const val ENGLISH_EXACT_LIMIT = 3
private const val ENGLISH_FUZZY_LIMIT = 2
private const val ENGLISH_SUGGESTION_LIMIT = ENGLISH_EXACT_LIMIT + ENGLISH_FUZZY_LIMIT

/**
 * English fuzzy corrections below this raw dictionary frequency are dropped so
 * a typo maps to a reasonably common word, not an obscure 1-edit neighbour.
 * The English asset carries real OpenSubtitles counts (up to ~28M); ~500 keeps
 * roughly the top 10% of words, a good "is this a real correction" cutoff.
 *
 * The Amharic asset has NO real frequencies (a shorter-word-ranks-higher
 * heuristic, all values <= 950), so an absolute gate is meaningless there --
 * its noise is instead controlled by the strict fidel cost model (only a
 * same-family vowel substitution is in budget), so it uses no gate.
 */
private const val ENGLISH_FUZZY_MIN_FREQUENCY = 500

/**
 * How far back to scan for an already-typed word when the user resumes typing
 * at the end of it (see [AddiyonKeyboardService.resumeEnglishWordIfAny]). Longer
 * than any word we'd complete against, so the whole prefix is always captured.
 */
private const val MAX_RESUME_PREFIX = 48

/**
 * Max fidel reading length for fuzzy suggestions. Beyond this the Damerau-
 * Levenshtein trie walk is expensive and the results are less useful (long
 * words are less likely to need typo correction). Exact-prefix completions
 * still run at any length.
 */
private const val MAX_FUZZY_READING_LENGTH = 12

/**
 * Length-scaled edit budget for fuzzy matching: none for buffers too short to
 * disambiguate, one edit for typical words, two for long ones (where a double
 * typo is plausible without exploding false positives).
 */
private fun fuzzyEditBudget(length: Int): Int = when {
    length <= 2 -> 0
    length <= 6 -> 1
    else -> 2
}

/**
 * Script-aware substitution cost for the Amharic fuzzy pass: a wrong vowel on
 * the right consonant (ይ↔ያ) is a cheap edit, a wrong consonant is expensive --
 * see [AmharicTable.fidelSubstitutionCost].
 */
private val AMHARIC_FIDEL_COST =
    WordTrie.SubstitutionCost(AmharicTable::fidelSubstitutionCost)

class AddiyonKeyboardService : InputMethodService(),
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

    // ----------------------------
    // VOICE INPUT
    // ----------------------------

    var voiceUiState by mutableStateOf<VoiceUiState>(VoiceUiState.Idle)
        private set

    // Created lazily on first use (needs a Context, not available at
    // construction time -- same reasoning as amharicDictionary above) and
    // reused across taps within one input session; torn down in
    // onFinishInputView/onDestroy.
    private var voiceInputController: VoiceInputController? = null

    private var pendingVoiceStartAfterPermission = false

    // Reconciles the in-flight utterance with the field's composing region;
    // see VoiceComposer for the dictation model.
    private val voiceComposer = VoiceComposer()

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

    // Whether the optional Latin digit row renders above the top letter row
    // on the letter layouts. Observable (like [palette]) so the hosted
    // keyboard recomposes live when the user flips it in Preferences.
    var showNumberRow by mutableStateOf(false)
        private set

    var vibrateOnKeypress by mutableStateOf(false)
        private set

    var soundOnKeypress by mutableStateOf(false)
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
            if (key == KeyboardPrefs.KEY_NUMBER_ROW) {
                refreshNumberRow()
            }
            if (key == KeyboardPrefs.KEY_VIBRATE || key == KeyboardPrefs.KEY_SOUND) {
                refreshFeedbackPrefs()
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

    /**
     * The Amharic in-progress word's raw Latin, mirrored out-of-field for
     * [com.addiyon.keyboard.ui.BufferPreviewStrip] (its fidel reading
     * isn't duplicated here -- it's already the first suggestion chip).
     * Empty ("") whenever nothing is composing, which is how the strip knows
     * to hide itself. Kept in lockstep with [amharicComposer] by
     * [updateSuggestions].
     */
    var amharicBufferLatin by mutableStateOf("")
        private set

    /**
     * True while the emoji picker panel replaces the toolbar + key rows.
     * Opened from the toolbar's emoji icon; closed by its ABC key, any mode
     * transition, or a new input session ([onStartInputView]).
     */
    var showEmojiPanel by mutableStateOf(false)
        private set

    /**
     * The emoji search query. Null = browsing (or panel closed); non-null =
     * search mode is up, showing the query row + results + the ENGLISH key
     * rows, whose keypresses are diverted into this string by the guards at
     * the top of [onCharacter]/[onDelete]/[onSpace]/[onEnter] -- the IME
     * can't use a TextField to serve itself.
     */
    var emojiSearchQuery by mutableStateOf<String?>(null)
        private set

    /**
     * base emoji -> the skin-tone variant the user last picked, mirrored
     * from [skinToneStore] so the grid cells can observe it. A state MAP,
     * not a state of a map: changing one base recomposes only cells reading
     * that key.
     */
    val selectedSkinTones = mutableStateMapOf<String, String>()

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
    // Bounded LRU of fidel -> raw Latin for Amharic words committed this
    // session, so the caret can walk back to an earlier word and resume
    // typing it -- see [resumeAmharicWordIfAny]. Declared before
    // [amharicComposer] since its onCommit callback captures this.
    private val amharicCommitHistory = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>) = size > 200
    }

    private val amharicComposer = WordComposer(
        inputConnection = { currentInputConnection },
        // render produces the fidel used for the suggestion strip and
        // commit. composesInline = false: nothing is written to the field
        // while typing -- see WordComposer's "WHY AMHARIC COMPOSES
        // OUT-OF-FIELD" doc. Backspace uses the default one-char step so
        // each typed letter can be cleared individually.
        render = Transliterator::transliterate,
        composesInline = false,
        onCommit = { rawLatin, fidel -> amharicCommitHistory[fidel] = rawLatin }
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
    lateinit var emojiRepository: EmojiRepository
        private set
    private lateinit var recentEmojiStore: RecentEmojiStore
    private lateinit var skinToneStore: SkinToneStore

    /**
     * Re-derives [suggestions] from the active composer's current buffer.
     *
     * Amharic is keyed off the LIVE TRANSLITERATED fidel prefix (exactly
     * what's already shown in the composing region), not the raw Latin
     * buffer -- reversing fidel back to a Latin spelling to look words up
     * would be ambiguous, since the forward transliteration mapping isn't
     * reliably invertible. See
     * [com.addiyon.keyboard.suggestion.WordTrie]'s class doc for the
     * full reasoning.
     *
     * English lookups are lowercased (the dictionary stores every entry
     * lowercase) and the user's typed case pattern is restored on the way
     * out, so "Th" suggests "The", not "the".
     */
    private fun updateSuggestions() {
        val latinBuffer = if (isAmharic && amharicComposer.isComposing) {
            amharicComposer.raw
        } else {
            ""
        }
        if (amharicBufferLatin != latinBuffer) {
            amharicBufferLatin = latinBuffer
        }

        if (!::amharicDictionary.isInitialized) {
            publishSuggestions(emptyList())
            return
        }
        publishSuggestions(if (isAmharic) {
            amharicSuggestions(amharicBufferLatin)
        } else {
            englishSuggestions()
        })
    }

    private fun publishSuggestions(value: List<String>) {
        if (suggestions != value) suggestions = value
    }

    /**
     * English suggestions: exact-prefix completions first, then typo/near-miss
     * corrections ([WordTrie.fuzzySuggestions]) appended below and gated by
     * [ENGLISH_FUZZY_MIN_FREQUENCY], so "informtion" still surfaces "information"
     * without an empty strip. Corrections are display-only -- space still
     * commits the literal buffer -- and the user's typed case is restored on
     * the way out via [matchCase].
     */
    private fun englishSuggestions(): List<String> {
        val typed = englishComposer.display
        if (typed.isEmpty()) return emptyList()

        val key = typed.lowercase()
        val exact = englishDictionary.suggestions(key, ENGLISH_EXACT_LIMIT)
        val merged = ArrayList<String>(ENGLISH_SUGGESTION_LIMIT)
        for (word in exact) {
            if (word !in merged) merged.add(word)
        }

        if (merged.size < ENGLISH_EXACT_LIMIT) {
            val fuzzy = englishDictionary.fuzzySuggestions(
                key,
                fuzzyEditBudget(key.length),
                ENGLISH_FUZZY_LIMIT
            )
            for (match in fuzzy) {
                if (match.frequency >= ENGLISH_FUZZY_MIN_FREQUENCY && match.word !in merged) {
                    merged.add(match.word)
                    if (merged.size >= ENGLISH_SUGGESTION_LIMIT) break
                }
            }
        }

        return merged.map { matchCase(typed, it) }
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
    private fun amharicSuggestions(latin: String): List<String> {
        if (latin.isEmpty()) return emptyList()

        val readings = Transliterator.readings(latin)
        val merged = LinkedHashSet<String>(AMHARIC_SUGGESTION_LIMIT + readings.size)
        for (reading in readings) merged.add(reading)

        for (reading in readings) {
            val completions = amharicDictionary.suggestions(reading, AMHARIC_SUGGESTION_LIMIT)
            for (word in completions) {
                merged.add(word)
                if (merged.size > AMHARIC_SUGGESTION_LIMIT) break
            }
            if (merged.size > AMHARIC_SUGGESTION_LIMIT) break
        }

        if (merged.size <= AMHARIC_SUGGESTION_LIMIT &&
            readings.any { it.length <= MAX_FUZZY_READING_LENGTH }
        ) {
            val fuzzy = ArrayList<WordTrie.FuzzyMatch>(AMHARIC_SUGGESTION_LIMIT)
            for (reading in readings) {
                if (reading.length > MAX_FUZZY_READING_LENGTH) continue
                fuzzy += amharicDictionary.fuzzySuggestions(
                    reading,
                    fuzzyEditBudget(reading.length),
                    AMHARIC_SUGGESTION_LIMIT,
                    AMHARIC_FIDEL_COST,
                    insertCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                    deleteCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                )
            }
            fuzzy.sortWith(compareBy({ it.editDistance }, { -it.frequency }))
            for (match in fuzzy) {
                merged.add(match.word)
                if (merged.size > AMHARIC_SUGGESTION_LIMIT) break
            }
        }

        return merged.asSequence().take(AMHARIC_SUGGESTION_LIMIT).toList()
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

    /** Re-derives [showNumberRow] from the saved preference. */
    private fun refreshNumberRow() {
        showNumberRow = KeyboardPrefs.numberRow(this)
    }

    private fun refreshFeedbackPrefs() {
        vibrateOnKeypress = KeyboardPrefs.vibrateOnKeypress(this)
        soundOnKeypress = KeyboardPrefs.soundOnKeypress(this)
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
     * Mic button on the suggestion toolbar. Tapping while listening pauses
     * dictation (the in-flight utterance is finalized in place, so nothing
     * the user saw is lost); otherwise starts/resumes it -- requesting
     * RECORD_AUDIO first via [VoicePermissionActivity] if it isn't already
     * granted (an InputMethodService can't request permissions itself).
     * Language follows the keyboard's current mode: "am-ET" in Amharic,
     * "en-US" in English.
     */
    fun onVoiceInput() {
        if (voiceUiState is VoiceUiState.Listening) {
            voiceInputController?.stop()
            finalizeVoiceComposing()
            voiceUiState = VoiceUiState.Paused
            return
        }

        startVoiceRecognition()
    }

    /** Back arrow in the voice toolbar: leave voice mode entirely. */
    fun exitVoiceMode() {
        voiceInputController?.stop()
        finalizeVoiceComposing()
        resetVoiceUi()
        updateSuggestions()
    }

    private fun startVoiceRecognition() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            voiceUiState = VoiceUiState.PermissionRequired
            pendingVoiceStartAfterPermission = true
            val intent = Intent(this, VoicePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
            return
        }

        // Flush any half-typed word first: the WordComposer and voice must
        // never both own a composing region in the field.
        activeComposer.commit()
        voiceComposer.reset()
        // Set Listening BEFORE start(): an unavailable recognizer fails
        // synchronously through onVoiceFatalError, which must win.
        voiceUiState = VoiceUiState.Listening
        voiceController().start(if (isAmharic) "am-ET" else "en-US")
    }

    private fun voiceController(): VoiceInputController =
        voiceInputController ?: VoiceInputController(
            context = this,
            onPartial = { text -> onVoicePartialResult(text) },
            onFinal = { text -> onVoiceFinalResult(text) },
            onFatalError = { kind -> onVoiceFatalError(kind) }
        ).also { voiceInputController = it }

    /**
     * Streams the latest refinement of the in-flight utterance into the
     * field's composing region -- each push atomically replaces the previous
     * one, so the text updates in place as recognition refines it (the
     * Gboard model). The char before the cursor is read once per utterance
     * (when the region opens); after that the region itself is the anchor.
     */
    private fun onVoicePartialResult(text: String) {
        if (voiceUiState !is VoiceUiState.Listening) return
        val ic = currentInputConnection ?: return
        val charBefore = if (voiceComposer.isComposing) null
        else ic.getTextBeforeCursor(1, 0)?.lastOrNull()
        voiceComposer.updatePartial(text, charBefore)?.let { ic.setComposingText(it, 1) }
    }

    /**
     * Replaces the composing region with the utterance's final text.
     * commitText atomically swaps out an active composing region, so no
     * explicit finishComposingText is needed on this path.
     */
    private fun onVoiceFinalResult(text: String) {
        if (voiceUiState !is VoiceUiState.Listening) return
        val ic = currentInputConnection ?: return
        val charBefore = if (voiceComposer.isComposing) null
        else ic.getTextBeforeCursor(1, 0)?.lastOrNull()
        val commit = voiceComposer.finalize(text, charBefore) ?: return

        ic.beginBatchEdit()
        if (commit.deleteSpaceBefore) {
            // Spoken punctuation after "word ": clear the composing region
            // (if one is live) so the delete hits the space, then commit.
            ic.setComposingText("", 1)
            ic.deleteSurroundingText(1, 0)
        }
        ic.commitText(commit.text, 1)
        ic.endBatchEdit()
    }

    private fun onVoiceFatalError(kind: VoiceErrorKind) {
        finalizeVoiceComposing()
        voiceUiState = if (kind == VoiceErrorKind.TOO_MANY_REQUESTS) {
            VoiceUiState.Paused
        } else {
            VoiceUiState.Unavailable(kind.userMessage)
        }
        Toast.makeText(this, kind.userMessage, Toast.LENGTH_SHORT).show()
    }

    /**
     * Locks whatever the composing region currently shows into the field
     * (never commitText here -- the framework auto-finalizes a live region
     * when the session ends, and committing again would duplicate the text;
     * see [WordComposer.finish] for the same lesson). Safe no-op when no
     * utterance is live.
     */
    private fun finalizeVoiceComposing() {
        if (!voiceComposer.isComposing) return
        currentInputConnection?.finishComposingText()
        voiceComposer.onFinalizedExternally()
    }

    private fun resetVoiceUi() {
        voiceComposer.reset()
        pendingVoiceStartAfterPermission = false
        voiceUiState = VoiceUiState.Idle
    }

    private fun leaveVoiceModeForKeyboardInput() {
        if (!voiceUiState.isVoiceMode) return
        // stop() first so in-flight recognizer callbacks are stale before we
        // close the region; the pressed key's own edits then land after it.
        voiceInputController?.stop()
        finalizeVoiceComposing()
        resetVoiceUi()
    }

    private fun maybeStartPendingVoiceAfterPermission() {
        if (!pendingVoiceStartAfterPermission) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            pendingVoiceStartAfterPermission = false
            startVoiceRecognition()
        }
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

    /**
     * Opens the emoji picker panel. Commits the composing word first, for
     * the same reason [toggleNumberMode] does -- an emoji must land AFTER
     * the word, never inside a composing region (this also empties
     * [amharicBufferLatin], so the preview strip never coexists with the
     * panel). The repository load is a safe no-op if the sequential startup
     * chain already started it; the panel shows a loading state until
     * [EmojiRepository.isReady].
     */
    fun openEmojiPanel() {
        leaveVoiceModeForKeyboardInput()
        activeComposer.commit()
        updateSuggestions()
        emojiRepository.loadAsync()
        showEmojiPanel = true
    }

    fun closeEmojiPanel() {
        showEmojiPanel = false
        emojiSearchQuery = null
    }

    /** Enters emoji search mode (query row + English key rows). */
    fun openEmojiSearch() {
        emojiSearchQuery = ""
    }

    /** Leaves search mode back to the browse panel. */
    fun closeEmojiSearch() {
        emojiSearchQuery = null
    }

    /** The search query row's clear (x) button: empty the query, stay in search. */
    fun clearEmojiSearchQuery() {
        if (emojiSearchQuery != null) emojiSearchQuery = ""
    }

    /**
     * An emoji cell was tapped in the picker. The active composer is already
     * flushed (see [openEmojiPanel]) and stays empty while the panel is up,
     * so this writes straight to the field -- deliberately NOT [commitText],
     * whose composer flush and voice-mode exit are dead weight here. Every
     * commit path (grid tap, tone popup, search result, search enter) lands
     * here, so recents recording is centralized.
     */
    fun commitEmoji(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
        recentEmojiStore.recordUse(emoji)
    }

    /**
     * The recents list frozen for one panel-open: the panel snapshots this
     * once per open (its composition lifetime), so committing an emoji never
     * reorders the grid under the user's finger mid-session.
     */
    fun recentEmojiSnapshot(): List<String> = recentEmojiStore.snapshot()

    /**
     * A tone was picked in the long-press popup: remember it (persisted, and
     * mirrored into [selectedSkinTones] so the cell recomposes to show it).
     * Picking the base (yellow) clears the preference. The caller commits
     * the picked emoji separately via [commitEmoji].
     */
    fun setSkinTone(base: String, variant: String) {
        skinToneStore.set(base, variant)
        if (variant == base) selectedSkinTones.remove(base)
        else selectedSkinTones[base] = variant
    }

    fun toggleLanguage() {
        leaveVoiceModeForKeyboardInput()
        closeEmojiPanel()
        activeComposer.commit()
        isAmharic = !isAmharic
        if (!isAmharic && numbersMode == NumbersMode.GEEZ_NUMBERS) {
            numbersMode = NumbersMode.NUMBERS
        }
        // Safe no-op if this dictionary's sequential startup load already
        // started it -- covers switching languages before that load reaches
        // the newly-active one.
        (if (isAmharic) amharicDictionary else englishDictionary).loadAsync { updateSuggestions() }
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
        leaveVoiceModeForKeyboardInput()
        closeEmojiPanel()
        activeComposer.commit()
        numbersMode = if (numbersMode == NumbersMode.OFF) NumbersMode.NUMBERS else NumbersMode.OFF
        updateSuggestions()
    }

    fun toggleSymbolsPage() {
        leaveVoiceModeForKeyboardInput()
        numbersMode = when (numbersMode) {
            NumbersMode.NUMBERS -> if (isAmharic) NumbersMode.GEEZ_NUMBERS else NumbersMode.SYMBOLS
            NumbersMode.GEEZ_NUMBERS -> NumbersMode.SYMBOLS
            NumbersMode.SYMBOLS -> NumbersMode.MORE_SYMBOLS
            NumbersMode.MORE_SYMBOLS -> NumbersMode.NUMBERS
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
        leaveVoiceModeForKeyboardInput()
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
     * keypress is ambiguous until the syllable ends (out-of-field -- see the
     * preview strip fed by [amharicBufferLatin] -- so
     * nothing touches the target field until commit), English so the current
     * word stays replaceable by a tapped suggestion (inline, underlined, in
     * the field). Word-terminating keys
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
        // Emoji search intercepts the real English key rows: keystrokes build
        // the query instead of touching the field. Same shift resolution as
        // the normal path so the query looks like what was typed (search
        // itself lowercases).
        emojiSearchQuery?.let { query ->
            if (showEmojiPanel) {
                emojiSearchQuery =
                    query + (if (isShiftEnabled) latin.uppercase() else latin.lowercase())
                consumeShiftAfterCharacter()
                return
            }
        }
        leaveVoiceModeForKeyboardInput()
        val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

        when {
            isNumberMode -> currentInputConnection?.commitText(output, 1)
            !isWordCharacter(output) -> {
                activeComposer.commit()
                val text = if (isAmharic) Transliterator.transliterate(output) else output
                currentInputConnection?.commitText(text, 1)
            }
            isAmharic -> {
                if (!amharicComposer.isComposing) resumeAmharicWordIfAny()
                amharicComposer.onCharacter(output)
            }
            else -> {
                if (!englishComposer.isComposing) resumeEnglishWordIfAny()
                englishComposer.onCharacter(output)
            }
        }

        consumeShiftAfterCharacter()
        updateSuggestions()
    }

    /**
     * When the caret sits at the end of an already-typed English word and the
     * user starts typing again, adopt that word into the composer so the new
     * keystrokes EXTEND it rather than starting a fresh one. Without this,
     * typing "infor", moving away, then coming back and typing "mation" would
     * offer completions of "mation" instead of "information".
     *
     * We turn the trailing run of word characters before the caret into the
     * composing region (delete it, re-insert it as composing text via
     * [WordComposer.resume]) and seed the buffer with it. Guards:
     *
     *  - English only. The Amharic buffer is romanized Latin behind the fidel
     *    shown in the field, and reversing fidel back to that Latin isn't
     *    reliable (see WordTrie's class doc), so there's nothing safe to adopt.
     *  - Skip if a word character immediately FOLLOWS the caret -- the user is
     *    editing mid-word, and adopting only the prefix would strand the
     *    suffix. Better to leave that alone.
     */
    private fun resumeEnglishWordIfAny() {
        val ic = currentInputConnection ?: return

        val after = ic.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && isWordCharacter(after[0].toString())) return

        val before = ic.getTextBeforeCursor(MAX_RESUME_PREFIX, 0) ?: return
        val prefix = before.takeLastWhile { isWordCharacter(it.toString()) }.toString()
        if (prefix.isEmpty()) return

        ic.beginBatchEdit()
        ic.deleteSurroundingText(prefix.length, 0)
        englishComposer.resume(prefix)
        ic.endBatchEdit()
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

    /** Whether [c] is part of the Ethiopic (Ge'ez/Fidel) Unicode block. */
    private fun isFidelWordChar(c: Char) = c in 'ሀ'..'፿'

    /**
     * Amharic counterpart to [resumeEnglishWordIfAny]. The field holds fidel,
     * not the raw Latin the composer needs, and reverse-transliterating fidel
     * in general is ambiguous (see WordTrie's class doc) -- so this only
     * resumes a word this *session* actually committed, via
     * [amharicCommitHistory], which remembers the raw Latin for exactly the
     * fidel it produced. Older or pasted fidel simply falls through to
     * starting a fresh word -- a limitation, not a corruption risk.
     */
    private fun resumeAmharicWordIfAny() {
        val ic = currentInputConnection ?: return

        val after = ic.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && isFidelWordChar(after[0])) return

        val before = ic.getTextBeforeCursor(MAX_RESUME_PREFIX, 0)?.toString() ?: return
        val fidelWord = before.takeLastWhile { isFidelWordChar(it) }
        if (fidelWord.isEmpty()) return

        val rawLatin = amharicCommitHistory[fidelWord] ?: return
        ic.beginBatchEdit()
        // Ethiopic is entirely within the BMP, so one Char == one code unit;
        // deleteSurroundingText's length is in UTF-16 code units.
        ic.deleteSurroundingText(fidelWord.length, 0)
        amharicComposer.resume(rawLatin)
        ic.endBatchEdit()
    }

    /**
     * Backspace pressed. Try to shrink the active composing buffer first --
     * one full rendered character at a time, which in Amharic can be a
     * multi-Latin-char span (so "she" -> ሸ, backspace -> nothing). If the
     * buffer is empty, fall back to deleting a character from the text
     * field itself.
     */
    fun onDelete() {
        // In emoji search, backspace edits the query, not the field. (The
        // browse panel's own backspace key runs with a null query, so it
        // falls through to real field deletion below.)
        emojiSearchQuery?.let { query ->
            if (showEmojiPanel) {
                if (query.isNotEmpty()) emojiSearchQuery = query.dropLast(1)
                return
            }
        }
        leaveVoiceModeForKeyboardInput()
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
                // Delete a whole emoji cluster, not one UTF-16 unit -- a
                // naive (1, 0) would strand half a surrogate pair or behead
                // a ZWJ sequence joint by joint. For plain BMP text the
                // cluster length is 1, same as before. 32 units of context
                // covers the longest real sequences (a family is 11, the
                // two-tone handshake 15).
                val before = ic.getTextBeforeCursor(32, 0)
                val cluster = EmojiBackspace.lastClusterLength(before ?: "")
                ic.deleteSurroundingText(cluster.coerceAtLeast(1), 0)
            }
        }
        updateSuggestions()
    }

    fun commitText(text: String) {
        leaveVoiceModeForKeyboardInput()
        activeComposer.commit()
        currentInputConnection?.commitText(text, 1)
    }

    /**
     * Space commits any in-flight word first, then inserts a space.
     *
     * [WordComposer.commit] inserts the rendered word: for Amharic that's the
     * first time anything from this word reaches the field at all, landing
     * as the greedy fidel reading (the same as suggestions[0]) -- so space
     * picks the default reading and a tap is only needed for a NON-default
     * one -- and the preview strip disappears; for English it finalizes the
     * inline composed word. With no word in flight it's a plain space.
     */
    fun onSpace() {
        // CLDR annotations are multi-word ("red heart"), so space belongs to
        // the emoji search query, not the field.
        emojiSearchQuery?.let { query ->
            if (showEmojiPanel) {
                emojiSearchQuery = "$query "
                return
            }
        }
        leaveVoiceModeForKeyboardInput()
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
        // In emoji search, enter commits the top result (with its remembered
        // skin tone) -- it never submits the field's IME action.
        emojiSearchQuery?.let { query ->
            if (showEmojiPanel) {
                emojiRepository.data?.search(query)?.firstOrNull()?.let {
                    commitEmoji(selectedSkinTones[it.base] ?: it.base)
                }
                return
            }
        }
        leaveVoiceModeForKeyboardInput()
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
        leaveVoiceModeForKeyboardInput()
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

        return AddiyonKeyboardView(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug-only: surfaces accidental main-thread disk/network work in
            // Logcat during development, without affecting release builds.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        refreshTheme(resources.configuration)
        refreshNumberRow()
        refreshFeedbackPrefs()
        KeyboardPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)

        amharicDictionary = WordDictionary(this, "amharic_words.dat")
        englishDictionary = WordDictionary(this, "english_words.dat")
        emojiRepository = EmojiRepository(this)
        // Both stores decode lazily on first use, and the prefs file is
        // already loaded in memory by the theme/number-row reads above, so
        // neither adds startup work here. The tone mirror seeds eagerly:
        // it's a handful of entries and the grid reads it on first open.
        recentEmojiStore = RecentEmojiStore(
            load = { KeyboardPrefs.recentEmojis(this) },
            save = { KeyboardPrefs.setRecentEmojis(this, it) }
        )
        skinToneStore = SkinToneStore(
            load = { KeyboardPrefs.emojiSkinTones(this) },
            save = { KeyboardPrefs.setEmojiSkinTones(this, it) }
        )
        selectedSkinTones.putAll(skinToneStore.all())
        // Parsing the dictionary lines (~182k Amharic, ~250k English) happens
        // off the main thread; if the user starts typing before a load
        // finishes, suggestions just start appearing once loadAsync's
        // callback lands (main thread, per WordDictionary's contract).
        //
        // Only the *active* language loads at startup, and the other loads
        // only after it finishes (never both in parallel) -- two simultaneous
        // background-thread allocation storms building ~200k-node tries is
        // exactly the kind of GC pressure that stalls the main thread on
        // low-RAM devices. The inactive one loads lazily on first language
        // switch if the user gets there before the sequential load would have.
        val activeDictionary = if (isAmharic) amharicDictionary else englishDictionary
        val inactiveDictionary = if (isAmharic) englishDictionary else amharicDictionary
        activeDictionary.loadAsync {
            updateSuggestions()
            inactiveDictionary.loadAsync {
                updateSuggestions()
                // Emoji data last: it doesn't gate typing, and keeping the
                // loads sequential avoids overlapping allocation storms.
                emojiRepository.loadAsync()
            }
        }
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
        voiceComposer.reset()
        // A new session starts on the keyboard, not a stale emoji panel.
        closeEmojiPanel()
        // The Enter key adapts to this field's IME action (search/go/send/...).
        resolveEnterAction(editorInfo)
        updateSuggestions()

        // Catch any theme change that happened while the keyboard was hidden,
        // and make sure the nav bar strip is colored correctly every time
        // the keyboard becomes visible again.
        refreshTheme(resources.configuration)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        maybeStartPendingVoiceAfterPermission()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        maybeStartPendingVoiceAfterPermission()
    }

    /**
     * The framework calls this whenever the cursor or selection changes in
     * the target field -- both when WE change it (by pushing composing text)
     * and when the USER changes it (by tapping somewhere else). If the new
     * cursor is inside the composing region the framework is tracking, the
     * movement is consistent with our own edits and we ignore it. If the
     * cursor has landed outside that region, the user has visibly walked
     * away from the word we were composing, so we abandon it -- for English
     * that freezes the underlined text in place; for Amharic there's no
     * composing region (nothing was written to the field), so it's a pure
     * buffer discard -- otherwise the next keystroke would keep rewriting a
     * region that's no longer near the caret.
     *
     * candidatesStart / candidatesEnd are the framework's view of the
     * current composing region; both are -1 when nothing is being composed
     * (always true for Amharic, since it never opens one -- so any selection
     * change while its buffer is non-empty is treated as tap-away).
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

        // A selection (start != end) inside our region also counts as "the
        // user took over" -- we don't support composing across a selection.
        val cursorInsideComposing = newSelStart == newSelEnd &&
                candidatesStart >= 0 &&
                newSelStart in candidatesStart..candidatesEnd

        // Voice dictation in flight: a deliberate cursor move finalizes the
        // utterance where it was showing and restarts recognition cleanly at
        // the new position (our own setComposingText pushes land INSIDE the
        // region, so they don't trip this).
        if (voiceComposer.isComposing) {
            if (!cursorInsideComposing) {
                finalizeVoiceComposing()
                voiceInputController?.restartSession()
            }
            return
        }

        if (!activeComposer.isComposing) return

        if (!cursorInsideComposing) {
            activeComposer.abandon()
            updateSuggestions()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Field is going away without an explicit commit. For English,
        // finalize the composing region IN PLACE (no new text inserted) --
        // using commit()/commitText here duplicated the word, because the
        // framework also finalizes the still-active composing region as the
        // session ends. For Amharic there's nothing in the field to
        // finalize, so this discards the uncommitted buffer instead. See
        // WordComposer.finish().
        activeComposer.finish()
        updateSuggestions()
        voiceInputController?.stop()
        finalizeVoiceComposing()
        resetVoiceUi()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        KeyboardPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        voiceInputController?.stop()
        resetVoiceUi()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
