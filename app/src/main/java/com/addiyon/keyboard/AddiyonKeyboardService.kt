package com.addiyon.keyboard

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.addiyon.keyboard.composing.ResumableWord
import com.addiyon.keyboard.composing.WordComposer
import com.addiyon.keyboard.composing.isComposerTextImmediatelyBeforeCursor
import com.addiyon.keyboard.composing.isSelectionAtComposingEnd
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.model.onShiftTap
import com.addiyon.keyboard.emoji.EmojiBackspace
import com.addiyon.keyboard.emoji.EmojiRepository
import com.addiyon.keyboard.emoji.RecentEmojiStore
import com.addiyon.keyboard.emoji.SkinToneStore
import com.addiyon.keyboard.suggestion.AmharicPrefixCompletion
import com.addiyon.keyboard.suggestion.AmharicCommitPolicy
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.EmailChip
import com.addiyon.keyboard.suggestion.EmailSuggestions
import com.addiyon.keyboard.suggestion.NgramContext
import com.addiyon.keyboard.suggestion.SQLiteDictionary
import com.addiyon.keyboard.suggestion.SQLiteLanguageStore
import com.addiyon.keyboard.suggestion.SQLiteNgramModel
import com.addiyon.keyboard.suggestion.SubstitutionCost
import com.addiyon.keyboard.suggestion.Suggestion
import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.AmharicWordReverser
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import com.addiyon.keyboard.suggestion.matchCase
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.util.MemoryProbe
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.voice.VoiceComposer
import com.addiyon.keyboard.voice.VoiceErrorKind
import com.addiyon.keyboard.voice.VoiceInputController
import com.addiyon.keyboard.voice.VoiceUiState
import com.addiyon.keyboard.voice.isVoiceMode
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Max chips in the Amharic suggestion strip: the live word's readings plus
 * dictionary completions. The strip scrolls horizontally, so this can be
 * generous.
 */
private const val AMHARIC_SUGGESTION_LIMIT = 10

/**
 * Max next-word prediction chips shown when the Amharic buffer is empty
 * (right after a commit): pure bigram/trigram predictions from the words
 * before the cursor, zero keystrokes typed.
 */
private const val NEXT_WORD_LIMIT = 6

/**
 * English strip capacity: exact-prefix completions first, then up to
 * [ENGLISH_FUZZY_LIMIT] typo corrections appended below them.
 */
private const val ENGLISH_EXACT_LIMIT = 3
private const val ENGLISH_FUZZY_LIMIT = 2
private const val ENGLISH_SUGGESTION_LIMIT = ENGLISH_EXACT_LIMIT + ENGLISH_FUZZY_LIMIT
private const val LOW_RAM_IDLE_RELEASE_MS = 20_000L

/**
 * Candidate pool pulled from the trie for the English completion strip: the
 * top [ENGLISH_COMPLETION_POOL] prefix matches by frequency, from which the
 * n-gram context reorder ([CandidateRanker.rankByContext]) picks the
 * [ENGLISH_EXACT_LIMIT] shown. Larger than the visible count so a
 * context-predicted continuation ranked below the top few by raw frequency can
 * still surface; the trie's best-first search keeps this cheap.
 */
private const val ENGLISH_COMPLETION_POOL = 24

/**
 * Next-word successors pulled from the English model when building the
 * per-word context boost map -- enough to cover the model's stored per-context
 * fan-out (bigram cap 8), so any predicted continuation that is also a valid
 * completion of what's typed can collect its boost.
 */
private const val ENGLISH_NGRAM_CONTEXT_LIMIT = 10

/**
 * Per-char lowercase fold for English n-gram keys. Mirrors [WordDictionary]'s
 * default `Char::lowercaseChar` keying and `tools/build_english_dict.py`'s
 * sort, so a context/candidate word folds to the exact key the model's vocab
 * and the boost map are keyed by (whole-string `lowercase()` can diverge for a
 * few special-cased code points).
 */
private fun englishFold(word: String): String =
    buildString(word.length) { for (c in word) append(c.lowercaseChar()) }

/**
 * Text-field variations where English sentence auto-capitalization is
 * suppressed (a stray capital would be wrong or annoying): passwords, email
 * addresses, URIs, and filter/search-style fields. This deny-list is the
 * primary gate now that auto-capitalization defaults ON for text fields
 * (see [resolveAutoCap]).
 */
private val NO_AUTOCAP_VARIATIONS = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_URI,
    InputType.TYPE_TEXT_VARIATION_FILTER,
)

/** Characters of context read for sentence-start detection -- enough to see
 *  past any realistic run of trailing spaces to the terminator. See
 *  [SentenceCase]. */
private const val SENTENCE_LOOKBEHIND = 16

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
 * Max fidel reading length for fuzzy suggestions. Beyond this the Damerau-
 * Levenshtein trie walk is expensive and the results are less useful (long
 * words are less likely to need typo correction). Exact-prefix completions
 * still run at any length.
 */
private const val MAX_FUZZY_READING_LENGTH = 12

/**
 * How many candidate readings get a fuzzy pass when the strip underfills.
 * Readings are rank-ordered (greedy/most-plausible first) and each fuzzy
 * call is a bounded-edit-distance trie walk -- running it for all ~48
 * readings took >150ms per keystroke on a desktop JVM (visibly worse on a
 * phone) exactly in the type-then-clear scenario the strip underfills in.
 * The top few readings carry virtually all real correction value.
 */
private const val MAX_FUZZY_READINGS = 6

/** LRU capacity for per-word suggestion memoization -- see [amharicSuggestionCache]. */
private const val SUGGESTION_CACHE_SIZE = 64

/** LRU capacity for the fidel -> raw-Latin history of words committed this
 *  session -- see [AddiyonKeyboardService.amharicCommitHistory]. */
private const val COMMIT_HISTORY_SIZE = 64

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
    SubstitutionCost(AmharicTable::fidelSubstitutionCost)

class AddiyonKeyboardService : InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    // ----------------------------
    // Lifecycle (UNCHANGED)
    // ----------------------------
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    companion object {
        @Volatile
        var currentInstance: AddiyonKeyboardService? = null
    }

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

    /**
     * System-reported low-RAM flag (`isLowRamDevice`) OR a host with < 1 GB
     * total RAM. Used to gate RAM-expensive features (currently: the per-
     * keystroke fuzzy pass in [amharicSuggestions] / [englishSuggestions]) so
     * a 1 GB device doesn't have to run them. Captured at construction; the
     * service is created fresh per IME session so we don't need a ContentObserver.
     */
    var isLowRam: Boolean = false
        private set
    var isEmergencyMode by mutableStateOf(false)
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
    internal val editorGateway = EditorGateway(
        connectionProvider = { currentInputConnection }
    )

    // What the Enter key should show and do in the current field, derived from
    // its IME action (see [resolveEnterAction], refreshed per input session in
    // onStartInputView). [editorActionId] is the raw EditorInfo action to fire
    // via performEditorAction when [enterAction] isn't a plain NEWLINE.
    var enterAction by mutableStateOf(EnterAction.NEWLINE)
        private set

    private var editorActionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED

    // Whether the current field accepts English auto-capitalization (a text
    // field that isn't a password/email/URI). Recomputed per input session in
    // onStartInputView; consulted by maybeAutoCapitalize.
    private var fieldAllowsAutoCap = false

    // Whether the current field takes an email address. Observable because
    // the letter layouts' comma key re-labels itself "@" in email fields
    // (see KeyRow). Recomputed per input session in onStartInputView.
    var isEmailField by mutableStateOf(false)
        private set

    var isPrivateField by mutableStateOf(false)
        private set

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

    // The user's "Keyboard height" multiplier, read from the same
    // SharedPreferences the settings slider writes. Observable (like
    // [showNumberRow]) so the hosted keyboard recomposes -- and resizes --
    // live when the user drags the slider. See [refreshKeyboardHeightScale].
    var keyboardHeightScale by mutableStateOf(KEYBOARD_HEIGHT_SCALE_DEFAULT)
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
            safeApply {
                if (key == KeyboardPrefs.KEY_PALETTE) {
                    refreshTheme(resources.configuration)
                }
                if (key == KeyboardPrefs.KEY_NUMBER_ROW) {
                    refreshNumberRow()
                }
                if (key == KeyboardPrefs.KEY_KEYBOARD_HEIGHT_SCALE) {
                    refreshKeyboardHeightScale()
                }
                if (key == KeyboardPrefs.KEY_VIBRATE || key == KeyboardPrefs.KEY_SOUND) {
                    refreshFeedbackPrefs()
                }
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
     * Email-domain suggestion chips shown in place of [suggestions] while
     * the user types in an email-typed field. Each chip has a [EmailChip.display]
     * label (what the chip shows -- the domain suffix only) and an
     * [EmailChip.commit] payload (what gets written to the field on tap --
     * the typed local part plus the suffix). Empty outside email fields and
     * while nothing is being composed (we only show chips once the user has
     * typed at least one character of the email token).
     */
    var emailSuggestions by mutableStateOf<List<EmailChip>>(emptyList())
        private set

    /**
     * True when [suggestions] holds next-word PREDICTIONS (empty Amharic
     * buffer, context from the field) rather than completions of a word
     * being typed. The strip renders prediction chips without the chip-0
     * "space commits this" highlight, because with nothing composing, space
     * just inserts a space.
     */
    var suggestionsArePredictions by mutableStateOf(false)
        private set

    /**
     * True while a language-toggle triggered dictionary reload is in flight
     * (the new language's dictionary is still being copied/opened on the
     * background thread). The suggestion strip animates a small spinner in
     * this window so the user sees progress instead of an empty strip.
     */
    var isLanguageSwitching by mutableStateOf(false)
        private set
    private var languageLoadGeneration = 0L

    /**
     * True while the emoji picker panel replaces the toolbar + key rows.
     * Opened from the toolbar's emoji icon; closed by its ABC key, any mode
     * transition, or a new input session ([onStartInputView]).
     */
    var showEmojiPanel by mutableStateOf(false)
        private set

    /**
     * The emoji search field's state (text + cursor/selection). Null =
     * browsing (or panel closed); non-null = search mode is up, showing the
     * query row + results + the ENGLISH key rows, whose keypresses are
     * diverted into this value by the guards at the top of
     * [onCharacter]/[onDelete]/[onSpace]/[onEnter] -- the IME can't summon
     * itself to serve its own TextField, so the search field is a real
     * (focused, cursor-bearing) BasicTextField whose EDITS all come from
     * those guards or from direct touch (tap to move the cursor, drag to
     * select). A full TextFieldValue rather than a String so keystrokes
     * insert at the cursor, not blindly at the end.
     */
    var emojiSearchField by mutableStateOf<TextFieldValue?>(null)
        private set

    /** The emoji search query text; null iff not in search mode. */
    val emojiSearchQuery: String?
        get() = emojiSearchField?.text

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
    private val amharicComposer = WordComposer(
        inputConnection = { currentInputConnection },
        // commitTransform picks the word that lands in the field on space/
        // enter/exit-of-a-resumed-word: the top-RANKED transliteration
        // candidate (dictionary-exact match promoted over the structurally
        // greedy one -- see CandidateRanker), falling back to the plain
        // greedy reading when the dictionary isn't loaded yet or ranking
        // finds nothing. Recomputed from the CURRENT buffer at every commit
        // site (WordComposer never caches it), so it can't go stale relative
        // to what's in the buffer.
        // Backspace uses the default one-char step so each typed letter can
        // be cleared individually.
        commitTransform = { raw -> topAmharicCandidate(raw) },
        // Every committed word is remembered fidel -> raw Latin, so the caret
        // can walk back onto it and resume typing it -- see
        // [amharicCommitHistory] / [maybeResumeWordAtCursor].
        onCommit = { raw, display ->
            if (!isPrivateField && display.isNotEmpty()) amharicCommitHistory[display] = raw
        },
        editor = editorGateway
    )

    /**
     * Fidel display form -> the raw Latin buffer that committed it, for words
     * committed this session. Reverse-transliterating fidel in general is
     * ambiguous, but a word we composed ourselves we already have the Latin
     * for -- this is what lets [maybeResumeWordAtCursor] adopt a committed
     * word back into composition. [AmharicWordReverser] (round-trip verified)
     * covers words outside the history: chip-committed words, earlier
     * sessions, pasted text. LRU-capped.
     */
    private val amharicCommitHistory =
        object : LinkedHashMap<String, String>(COMMIT_HISTORY_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > COMMIT_HISTORY_SIZE
        }

    private val englishComposer = WordComposer(
        inputConnection = { currentInputConnection },
        editor = editorGateway
    )

    private val activeComposer: WordComposer
        get() = if (isEmailField) englishComposer else if (isAmharic) amharicComposer else englishComposer

    // Built in onCreate(), not as property initializers here -- Context
    // isn't safely usable (applicationContext etc.) until attachBaseContext
    // has run, which happens after this class's own construction but
    // before onCreate().
    private lateinit var amharicDictionary: SQLiteDictionary
    private lateinit var englishDictionary: SQLiteDictionary
    private lateinit var amharicNgrams: SQLiteNgramModel
    private lateinit var englishNgrams: SQLiteNgramModel
    private lateinit var amharicStore: SQLiteLanguageStore
    private lateinit var englishStore: SQLiteLanguageStore
    lateinit var emojiRepository: EmojiRepository
        private set
    private lateinit var recentEmojiStore: RecentEmojiStore
    private lateinit var skinToneStore: SkinToneStore

    /**
     * Re-derives [suggestions] from the active composer's current buffer.
     *
     * Amharic keeps the raw Latin in the composing region, then generates
     * multiple fidel readings from that buffer and checks each reading against
     * the dictionary. Prefix-only readings stay alive for completions without
     * being shown as standalone words.
     *
     * English lookups are lowercased (the dictionary stores every entry
     * lowercase) and the user's typed case pattern is restored on the way
     * out, so "Th" suggests "The", not "the".
     */
    private fun updateSuggestions() {
        safeApply {
            if (!suggestionRefreshGate.requestRefresh()) return@safeApply
            if (isPrivateField) {
                invalidateSuggestionWork()
                publishSuggestions(emptyList())
                emailSuggestions = emptyList()
                return@safeApply
            }
            if (!::amharicDictionary.isInitialized) {
                invalidateSuggestionWork()
                publishSuggestions(emptyList())
                return@safeApply
            }
            // Email fields have their own suggestion pipeline: domain-suffix chips
            // (@gmail.com / .com / ...) instead of dictionary completions. Typing
            // is routed through englishComposer in onCharacter regardless of the
            // user's current language mode, so englishComposer.raw is the email
            // token regardless of isAmharic. Empty composer -> empty chips ->
            // the strip falls back to the toolbar icons (we only show chips once
            // the user has typed >=1 char of the email token).
            if (isEmailField) {
                invalidateSuggestionWork()
                val token = if (englishComposer.isComposing) englishComposer.raw else ""
                publishEmailSuggestions(EmailSuggestions.emailChipsFor(token))
                return@safeApply
            }
            // Non-email field: clear any email chips that may have lingered from
            // a previous input session before publishing word suggestions.
            if (emailSuggestions.isNotEmpty()) emailSuggestions = emptyList()
            if (isAmharic) {
                val latinBuffer = if (amharicComposer.isComposing) amharicComposer.raw else ""
                val context = captureNgramContext(NgramContext.AMHARIC, amharicComposer)
                if (latinBuffer.isEmpty()) {
                    invalidateSuggestionWork()
                    composingNgramBoost = null
                    composingPredictionCasing = emptyMap()
                    amharicSuggestionCache.clear()
                    amharicCommitCandidateCache.clear()
                    publishSuggestions(emptyList())
                    schedulePredictionComputation(
                        amharic = true,
                        context = context,
                        limit = if (isLowRam) 3 else NEXT_WORD_LIMIT
                    )
                } else {
                    scheduleSuggestionComputation(
                        raw = latinBuffer,
                        amharic = true,
                        context = context,
                    )
                }
            } else {
                val typed = if (englishComposer.isComposing) englishComposer.raw else ""
                val context = captureNgramContext(NgramContext.ENGLISH, englishComposer)
                if (typed.isEmpty()) {
                    invalidateSuggestionWork()
                    composingNgramBoost = null
                    composingPredictionCasing = emptyMap()
                    publishSuggestions(emptyList())
                    schedulePredictionComputation(
                        amharic = false,
                        context = context,
                        limit = if (isLowRam) 3 else NEXT_WORD_LIMIT
                    )
                } else {
                    scheduleSuggestionComputation(
                        raw = typed,
                        amharic = false,
                        context = context,
                    )
                }
            }
        }
    }

    private val suggestionMainHandler = Handler(Looper.getMainLooper())
    private val idleReleaseHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable {
        if (!isLowRam) return@Runnable
        languageLoadGeneration += 1
        isLanguageSwitching = false
        if (isAmharic && ::amharicStore.isInitialized) {
            amharicStore.release()
        } else if (::englishStore.isInitialized) {
            englishStore.release()
        }
    }
    private val suggestionExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "AddiyonSuggestions") },
    )
    private var suggestionGeneration = 0L

    private fun invalidateSuggestionWork() {
        suggestionGeneration += 1
        suggestionExecutor.queue.clear()
    }

    private fun enterEmergencyMode() {
        suggestionMainHandler.post {
            if (isEmergencyMode) return@post
            isEmergencyMode = true
            invalidateSuggestionWork()
            publishSuggestions(emptyList())
            emailSuggestions = emptyList()
            showEmojiPanel = false
            emojiSearchField = null
            amharicSuggestionCache.clear()
            amharicCommitCandidateCache.clear()
            if (::emojiRepository.isInitialized) emojiRepository.release()
            if (::amharicStore.isInitialized) amharicStore.release()
            if (::englishStore.isInitialized) englishStore.release()
        }
    }

    private fun scheduleSuggestionComputation(
        raw: String,
        amharic: Boolean,
        context: NgramContext.Context,
    ) {
        val generation = ++suggestionGeneration
        val lowRam = isLowRam
        val ngramModel = ngramModelFor(amharic)
        suggestionExecutor.queue.clear()
        suggestionExecutor.execute {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {
            }
            val predictionLimit = if (amharic) {
                if (lowRam) 4 else AMHARIC_SUGGESTION_LIMIT
            } else {
                if (lowRam) 4 else ENGLISH_NGRAM_CONTEXT_LIMIT
            }
            val predictions = try {
                ngramModel?.let { predictionsFor(it, context, predictionLimit) }.orEmpty()
            } catch (_: RuntimeException) {
                emptyList()
            }
            val ngramNext = predictions.associate {
                (if (amharic) EthiopicNormalizer.normalize(it.word) else englishFold(it.word)) to
                    it.weight
            }
            val predictionCasing = if (amharic) {
                emptyMap()
            } else {
                predictions
                    .filter { it.word != it.word.lowercase() }
                    .associate { englishFold(it.word) to it.word }
            }
            val computed = try {
                if (amharic) {
                    amharicSuggestions(raw, ngramNext, lowRam)
                } else {
                    englishSuggestions(raw, ngramNext, predictionCasing, lowRam)
                }
            } catch (_: RuntimeException) {
                emptyList()
            }
            suggestionMainHandler.post {
                if (generation != suggestionGeneration) return@post
                if (isAmharic != amharic || isEmailField) return@post
                val currentRaw = if (amharic) amharicComposer.raw else englishComposer.raw
                if (currentRaw != raw) return@post
                composingNgramBoost = ngramNext
                composingPredictionCasing = predictionCasing
                publishSuggestions(computed)
            }
        }
    }

    private fun schedulePredictionComputation(
        amharic: Boolean,
        context: NgramContext.Context,
        limit: Int,
    ) {
        val generation = ++suggestionGeneration
        val ngramModel = ngramModelFor(amharic)
        suggestionExecutor.queue.clear()
        suggestionExecutor.execute {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {
            }
            val predictions = try {
                ngramModel?.let { predictionsFor(it, context, limit) }
                    .orEmpty()
                    .map { it.word }
            } catch (_: RuntimeException) {
                emptyList()
            }
            suggestionMainHandler.post {
                if (generation != suggestionGeneration) return@post
                if (isAmharic != amharic || isEmailField || activeComposer.isComposing) return@post
                publishSuggestions(predictions, arePredictions = true)
            }
        }
    }

    private fun ngramModelFor(amharic: Boolean): SQLiteNgramModel? =
        if (amharic) {
            if (::amharicNgrams.isInitialized) amharicNgrams else null
        } else {
            if (::englishNgrams.isInitialized) englishNgrams else null
        }

    /**
     * Per-word caches. While a word is composing, the committed text before
     * the composing region cannot change (any outside edit moves the cursor,
     * which abandons the composition), so the n-gram context -- and with it
     * the whole latin-buffer -> suggestions mapping -- is stable for the
     * word's lifetime. [composingNgramBoost] (shared by both languages -- only
     * the active one ever reads it) avoids re-fetching `getTextBeforeCursor` (a
     * synchronous binder round-trip to the editor app) on every keystroke;
     * [amharicSuggestionCache] makes retyping a state we've already ranked --
     * most importantly BACKSPACING back through the prefixes just typed -- a
     * lookup instead of a fresh transliterate + trie-walk pass. Both reset at
     * every word boundary.
     */
    private var composingNgramBoost: Map<String, Int>? = null

    /**
     * Per-word map from a predicted next word's folded key to its context
     * proper-noun casing (e.g. "york" -> "York"), so an English completion of a
     * proper noun is shown capitalized to match the prediction after the same
     * context. Empty when the context predicts nothing capitalized. Cached and
     * cleared alongside [composingNgramBoost] (English-only; Amharic leaves it
     * empty).
     */
    private var composingPredictionCasing: Map<String, String> = emptyMap()
    private val amharicSuggestionCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<String>>(SUGGESTION_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) =
                size > SUGGESTION_CACHE_SIZE
        }
    )
    private val amharicCommitCandidateCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(SUGGESTION_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > SUGGESTION_CACHE_SIZE
        }
    )

    /**
     * Bigram/trigram next-word predictions for the words preceding the cursor,
     * read from the field via [contextReader] and looked up in [ngrams]; empty
     * until the model loads or when the field gives no context. While
     * composing, the raw composing region sits immediately before the cursor
     * and would read as a hard boundary, so it's stripped from the tail first
     * ([composer]'s raw buffer).
     */
    private fun captureNgramContext(
        contextReader: NgramContext,
        composer: WordComposer,
    ): NgramContext.Context {
        return safeRun(NgramContext.EMPTY) {
            val raw = if (composer.isComposing) composer.raw else ""
            val before = editorGateway
                .textBeforeCursor(NgramContext.WINDOW + raw.length)
                ?.value
                ?: return@safeRun NgramContext.EMPTY
            val field = if (raw.isNotEmpty() && before.endsWith(raw)) {
                before.subSequence(0, before.length - raw.length)
            } else {
                before
            }
            contextReader.extract(field)
        }
    }

    private fun predictionsFor(
        ngrams: SQLiteNgramModel,
        context: NgramContext.Context,
        limit: Int,
    ): List<SQLiteNgramModel.Prediction> =
        safeRun(emptyList()) {
            val prev1 = context.prev1 ?: return@safeRun emptyList()
            if (!ngrams.isReady) return@safeRun emptyList()
            ngrams.predict(context.prev2, prev1, limit)
        }

    /**
     * The reading that lands in the field when the current Amharic word is
     * committed: the best exact dictionary reading if one exists, else the
     * structurally greedy reading. Longer completions remain tap-only.
     */
    private fun topAmharicCandidate(raw: String): String {
        return safeRun(Transliterator.transliterate(raw)) {
            AmharicCommitPolicy.resolve(raw, amharicCommitCandidateCache[raw])
        }
    }

    private fun publishSuggestions(value: List<String>, arePredictions: Boolean = false) {
        safeApply {
            if (suggestions != value) suggestions = value
            val predictions = arePredictions && value.isNotEmpty()
            if (suggestionsArePredictions != predictions) suggestionsArePredictions = predictions
        }
    }

    /**
     * Push the email-domain chip list into the [emailSuggestions] state and
     * ensure the regular word-suggestion list is empty (the strip picks
     * email chips when this list is non-empty -- see SuggestionBar). Idempotent.
     */
    private fun publishEmailSuggestions(value: List<EmailChip>) {
        safeApply {
            if (emailSuggestions != value) emailSuggestions = value
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (suggestionsArePredictions) suggestionsArePredictions = false
        }
    }

    /**
     * English suggestions: exact-prefix completions first, then typo/near-miss
     * corrections ([WordTrie.fuzzySuggestions]) appended below and gated by
     * [ENGLISH_FUZZY_MIN_FREQUENCY], so "informtion" still surfaces "information"
     * without an empty strip. Corrections are display-only -- space still
     * commits the literal buffer -- and the user's typed case is restored on
     * the way out via [matchCase].
     *
     * The exact completions are reordered by an n-gram context nudge
     * ([CandidateRanker.rankByContext]): a completion the model predicts to
     * follow the previous word(s) rises within the frequency-ranked pool, so
     * after "I " typing "lo" biases "love"/"look" over an equally common but
     * unpredicted "lot". Computed once per composing word via
     * [composingNgramBoost].
     */
    private fun englishSuggestions(
        typed: String,
        ngramNext: Map<String, Int>,
        casing: Map<String, String>,
        lowRam: Boolean,
    ): List<String> {
        return safeRun(emptyList()) {
            if (typed.isEmpty()) return@safeRun emptyList()

            val key = typed.lowercase()

            val pool = englishDictionary.suggestionEntries(key, ENGLISH_COMPLETION_POOL)
                .map { CandidateRanker.DictionaryWord(it.word, it.frequency) }
            val merged = ArrayList<String>(ENGLISH_SUGGESTION_LIMIT)
            for (word in CandidateRanker.rankByContext(pool, ngramNext, ::englishFold, ENGLISH_EXACT_LIMIT)) {
                // Swap in the context proper-noun casing ("york" -> "York") when the
                // model predicts this word capitalized after the same context.
                val cased = casing[englishFold(word)] ?: word
                if (cased !in merged) merged.add(cased)
            }

            if (merged.size < ENGLISH_EXACT_LIMIT && !lowRam) {
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

            merged.map { matchCase(typed, it) }
        }
    }

    /**
     * Amharic suggestions are scored from exact dictionary readings,
     * prefix completions, the current literal fallback, and fuzzy matches.
     */
    private fun amharicSuggestions(
        latin: String,
        ngramNext: Map<String, Int>,
        lowRam: Boolean,
    ): List<String> {
        return safeRun(emptyList()) {
            if (latin.isEmpty()) return@safeRun emptyList()
            if (amharicDictionary.isReady) {
                amharicSuggestionCache[latin]?.let { return@safeRun it }
            }

            val candidateReadings = Transliterator.candidateReadings(latin)
            val readings = candidateReadings.map { it.text }
            val readingFrequencies = amharicDictionary.frequenciesOf(readings)
            val visibleReadings = readings
            // Structural split readings: kept for completions/quirk chips, but not
            // allowed to win the default over the natural greedy reading.
            val quirkReadings = candidateReadings.filter { it.isQuirk }.map { it.text }.toSet()
            val commitCandidate = CandidateRanker.bestCommitCandidate(
                readings,
                readingFrequencies::get,
                quirkReadings = quirkReadings,
                preferGreedy = Transliterator.hasExplicitFamilySelection(latin),
            ) ?: Transliterator.transliterate(latin)
            amharicCommitCandidateCache[latin] = commitCandidate
            val directCompletions = amharicDictionary.suggestionEntriesForPrefixes(
                readings.distinct(),
                AMHARIC_SUGGESTION_LIMIT,
            )
            // The preferred vowel alternate is offered as a secondary chip -- but
            // only when it is a MULTI-character dictionary word (ቤት for "bet").
            // Pinning it on every second keystroke is pure noise otherwise: ሌ for
            // "le" / ቤ for "be" tell the user nothing (and single fidels sneak
            // into the dictionary as corpus tokenizer artifacts, so the word
            // check alone doesn't catch them; bare-vowel alternates like ኣ even
            // fold to the same word as the greedy አ). A suppressed alternate is
            // still a candidate reading, so its completions (ሌላ, ቤቶች, ...)
            // surface through the completion tier as before -- the pin is all
            // that's dropped. While the dictionary is still loading there is no
            // word signal; keep the old always-pin behavior for that brief window.
            val preferredAlternate = (
                Transliterator.vowelAlternateReading(latin)
                    ?: Transliterator.bareVowelAlternateReading(latin)
                )?.takeIf {
                    it.length > 1 && (!amharicDictionary.isReady || readingFrequencies.containsKey(it))
                }
            val completionCache = HashMap<String, List<CandidateRanker.DictionaryWord>>()
            for ((prefix, entries) in directCompletions) {
                completionCache[prefix] = entries.map {
                    CandidateRanker.DictionaryWord(it.word, it.frequency)
                }
            }
            val dictionaryLookup = { prefix: String, limit: Int ->
                amharicDictionary.suggestionEntries(prefix, limit).map {
                    CandidateRanker.DictionaryWord(it.word, it.frequency)
                }
            }
            // Direct dictionary completions first; when they don't fill the strip,
            // synthesize the rest by stripping a productive prefix (የ-, በ-, ...)
            // and completing the remainder from stems -- see
            // [AmharicPrefixCompletion] for why synthesized forms are discounted.
            val completionsForPrefix = { prefix: String, limit: Int ->
                completionCache.getOrPut(prefix) {
                    val direct = dictionaryLookup(prefix, limit)
                    if (direct.size >= limit) direct
                    else direct + AmharicPrefixCompletion.complete(
                        prefix, limit - direct.size, direct, dictionaryLookup
                    )
                }
            }

            // Context-aware nudge: candidates the n-gram model predicts to
            // follow the previous words get a small within-tier boost. Computed
            // once per composing word -- see [composingNgramBoost].
            val ranked = CandidateRanker.rankAmharic(
                readings = readings,
                limit = AMHARIC_SUGGESTION_LIMIT,
                frequencyOf = readingFrequencies::get,
                completionsForPrefix = completionsForPrefix,
                visibleReadings = visibleReadings,
                quirkReadings = quirkReadings,
                ngramNext = ngramNext,
                preferGreedy = Transliterator.hasExplicitFamilySelection(latin)
            )
            if (ranked.size >= AMHARIC_SUGGESTION_LIMIT ||
                readings.none { it.length <= MAX_FUZZY_READING_LENGTH }
            ) {
                return@safeRun pinPreferredAlternate(ranked, preferredAlternate).also {
                    if (amharicDictionary.isReady) amharicSuggestionCache[latin] = it
                }
            }

            // Fuzzy pass, bounded three ways to keep the worst keystroke cheap:
            // only the top [MAX_FUZZY_READINGS] readings (rank order -- the rest
            // are deep alternates that almost never contribute a correction),
            // the full 2-edit budget only for the TOP reading (an alternate
            // reading is already a variation; giving all of them 2 edits is
            // what made long non-word buffers freeze), and stop as soon as the
            // strip's worth of matches is gathered. On [isLowRam] devices we
            // skip the fuzzy pass entirely -- typo correction is a "nice to
            // have" that doesn't justify an in-Kotlin DP over a bounded SQL
            // candidate set on every keystroke at 1 GB.
            if (lowRam) {
                return@safeRun pinPreferredAlternate(ranked, preferredAlternate).also {
                    if (amharicDictionary.isReady) amharicSuggestionCache[latin] = it
                }
            }
            val fuzzy = ArrayList<CandidateRanker.FuzzyWord>(AMHARIC_SUGGESTION_LIMIT)
            var fuzzyReadings = 0
            for (reading in readings) {
                if (reading.length > MAX_FUZZY_READING_LENGTH) continue
                if (fuzzyReadings >= MAX_FUZZY_READINGS || fuzzy.size >= AMHARIC_SUGGESTION_LIMIT) break
                val budget = fuzzyEditBudget(reading.length)
                    .coerceAtMost(if (fuzzyReadings == 0) 2 else 1)
                fuzzyReadings++
                for (match in amharicDictionary.fuzzySuggestions(
                    reading,
                    budget,
                    AMHARIC_SUGGESTION_LIMIT,
                    AMHARIC_FIDEL_COST,
                    insertCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                    deleteCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                )) {
                    fuzzy += CandidateRanker.FuzzyWord(match.word, match.frequency, match.editDistance)
                }
            }

            pinPreferredAlternate(
                CandidateRanker.rankAmharic(
                    readings = readings,
                    limit = AMHARIC_SUGGESTION_LIMIT,
                    frequencyOf = readingFrequencies::get,
                    completionsForPrefix = completionsForPrefix,
                    visibleReadings = visibleReadings,
                    fuzzyWords = fuzzy,
                    quirkReadings = quirkReadings,
                    ngramNext = ngramNext,
                    preferGreedy = Transliterator.hasExplicitFamilySelection(latin)
                ),
                preferredAlternate
            ).also {
                if (amharicDictionary.isReady) amharicSuggestionCache[latin] = it
            }
        }
    }

    /**
     * Force the preferred alternate to sit directly behind the default reading.
     * Left in place when it is already the default, and a no-op when there is no
     * alternate. Result is re-capped to the limit.
     */
    private fun pinPreferredAlternate(
        ranked: List<String>,
        preferredAlternate: String?
    ): List<String> {
        return safeRun(ranked) {
            if (preferredAlternate == null || ranked.firstOrNull() == preferredAlternate) return@safeRun ranked
            val pinned = ArrayList<String>(ranked.size + 1)
            pinned.addAll(ranked)
            pinned.remove(preferredAlternate)
            pinned.add(minOf(1, pinned.size), preferredAlternate)
            pinned.take(AMHARIC_SUGGESTION_LIMIT)
        }
    }

    /**
     * Re-derives [isDarkTheme] from the system night flag and [palette] from
     * the saved preference, then refreshes the nav-bar tint (which depends on
     * both). Light/dark follows the system; only the color palette is user-
     * selectable, and it themes just the keyboard.
     */
    private fun refreshTheme(configuration: Configuration) {
        safeApply {
            palette = KeyboardPrefs.palette(this)
            val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            updateSystemNavigationBar()
        }
    }

    /** Re-derives [showNumberRow] from the saved preference. */
    private fun refreshNumberRow() {
        safeApply {
            showNumberRow = KeyboardPrefs.numberRow(this)
        }
    }

    /** Re-derives [keyboardHeightScale] from the saved preference. */
    private fun refreshKeyboardHeightScale() {
        safeApply {
            keyboardHeightScale = KeyboardPrefs.keyboardHeightScale(this)
        }
    }

    private fun refreshFeedbackPrefs() {
        safeApply {
            vibrateOnKeypress = KeyboardPrefs.vibrateOnKeypress(this)
            soundOnKeypress = KeyboardPrefs.soundOnKeypress(this)
        }
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
        safeApply {
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
        safeApply {
            val target = when (screen) {
                MainActivity.SCREEN_THEMES -> ThemesActivity::class.java
                MainActivity.SCREEN_GUIDE -> ManualActivity::class.java
                else -> MainActivity::class.java
            }
            val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (target == MainActivity::class.java) {
                intent.putExtra(MainActivity.EXTRA_OPEN_SCREEN, screen)
            }
            ExternalActions.start(this, intent, "Unable to open Tana Keyboard.")
        }
    }

    /** AI assist entry point from the suggestion toolbar. Not wired up yet. */
    fun onAiAction() {
        safeApply {
            // TODO: hook up AI feature.
        }
    }

    /** Clipboard entry point from the suggestion toolbar. Not wired up yet. */
    fun onClipboardAction() {
        safeApply {
            // TODO: hook up clipboard panel.
        }
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
        safeApply {
            if (isPrivateField) return@safeApply
            if (voiceUiState is VoiceUiState.Listening) {
                voiceInputController?.stop()
                finalizeVoiceComposing()
                voiceUiState = VoiceUiState.Paused
                return@safeApply
            }

            startVoiceRecognition()
        }
    }

    /** Back arrow in the voice toolbar: leave voice mode entirely. */
    fun exitVoiceMode() {
        safeApply {
            voiceInputController?.stop()
            finalizeVoiceComposing()
            resetVoiceUi()
            updateSuggestions()
        }
    }

    private fun startVoiceRecognition() {
        safeApply {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                voiceUiState = VoiceUiState.PermissionRequired
                pendingVoiceStartAfterPermission = true
                val intent = Intent(this, VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!ExternalActions.start(
                        this,
                        intent,
                        "Unable to open microphone permission."
                    )
                ) {
                    pendingVoiceStartAfterPermission = false
                    voiceUiState = VoiceUiState.Unavailable(
                        VoiceErrorKind.PERMISSION.userMessage
                    )
                }
                return@safeApply
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
    }

    private fun voiceController(): VoiceInputController =
        safeRun(voiceInputController ?: VoiceInputController(
            context = this,
            onPartial = { text -> safeApply { onVoicePartialResult(text) } },
            onFinal = { text -> safeApply { onVoiceFinalResult(text) } },
            onFatalError = { kind -> safeApply { onVoiceFatalError(kind) } }
        )) {
            voiceInputController ?: VoiceInputController(
                context = this,
                onPartial = { text -> safeApply { onVoicePartialResult(text) } },
                onFinal = { text -> safeApply { onVoiceFinalResult(text) } },
                onFatalError = { kind -> safeApply { onVoiceFatalError(kind) } }
            ).also { voiceInputController = it }
        }

    /**
     * Streams the latest refinement of the in-flight utterance into the
     * field's composing region -- each push atomically replaces the previous
     * one, so the text updates in place as recognition refines it (the
     * Gboard model). The char before the cursor is read once per utterance
     * (when the region opens); after that the region itself is the anchor.
     */
    private fun onVoicePartialResult(text: String) {
        safeApply {
            if (voiceUiState !is VoiceUiState.Listening) return@safeApply
            val charBefore = if (voiceComposer.isComposing) null
            else editorGateway.textBeforeCursor(1, optional = false)?.value?.lastOrNull()
            voiceComposer.updatePartial(text, charBefore)?.let { partial ->
                if (!editorGateway.setComposingText(partial)) {
                    stopVoiceAfterEditorFailure()
                }
            }
        }
    }

    /**
     * Replaces the composing region with the utterance's final text.
     * commitText atomically swaps out an active composing region, so no
     * explicit finishComposingText is needed on this path.
     */
    private fun onVoiceFinalResult(text: String) {
        safeApply {
            if (voiceUiState !is VoiceUiState.Listening) return@safeApply
            val charBefore = if (voiceComposer.isComposing) null
            else editorGateway.textBeforeCursor(1, optional = false)?.value?.lastOrNull()
            val commit = voiceComposer.finalize(text, charBefore) ?: return@safeApply

            if (!editorGateway.commitText(commit.text)) {
                editorGateway.finishComposingText()
                stopVoiceAfterEditorFailure()
            }
        }
    }

    private fun stopVoiceAfterEditorFailure() {
        voiceInputController?.stop()
        voiceComposer.reset()
        voiceUiState = VoiceUiState.Unavailable(VoiceErrorKind.UNKNOWN.userMessage)
    }

    private fun onVoiceFatalError(kind: VoiceErrorKind) {
        safeApply {
            finalizeVoiceComposing()
            voiceUiState = if (kind == VoiceErrorKind.TOO_MANY_REQUESTS) {
                VoiceUiState.Paused
            } else {
                VoiceUiState.Unavailable(kind.userMessage)
            }
            try {
                Toast.makeText(this, kind.userMessage, Toast.LENGTH_SHORT).show()
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "onVoiceFatalError Toast OOM")
            } catch (t: Throwable) {
                SafeLog.e(t, "onVoiceFatalError Toast")
            }
        }
    }

    /**
     * Locks whatever the composing region currently shows into the field
     * (never commitText here -- the framework auto-finalizes a live region
     * when the session ends, and committing again would duplicate the text;
     * see [WordComposer.finish] for the same lesson). Safe no-op when no
     * utterance is live.
     */
    private fun finalizeVoiceComposing() {
        safeApply {
            if (!voiceComposer.isComposing) return@safeApply
            editorGateway.finishComposingText()
            voiceComposer.onFinalizedExternally()
        }
    }

    private fun resetVoiceUi() {
        safeApply {
            voiceComposer.reset()
            pendingVoiceStartAfterPermission = false
            voiceUiState = VoiceUiState.Idle
        }
    }

    private fun leaveVoiceModeForKeyboardInput() {
        safeApply {
            if (!voiceUiState.isVoiceMode) return@safeApply
            // stop() first so in-flight recognizer callbacks are stale before we
            // close the region; the pressed key's own edits then land after it.
            voiceInputController?.stop()
            finalizeVoiceComposing()
            resetVoiceUi()
        }
    }

    private fun maybeStartPendingVoiceAfterPermission() {
        safeApply {
            if (!pendingVoiceStartAfterPermission) return@safeApply
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                pendingVoiceStartAfterPermission = false
                startVoiceRecognition()
            }
        }
    }

    /**
     * Opens the standalone [FeedbackActivity] from the keyboard toolbar's
     * feedback icon (which used to pop an in-keyboard bottom sheet). Launched
     * from a Service context, so it needs NEW_TASK.
     */
    fun openFeedbackScreen() {
        safeApply {
            val intent = Intent(this, FeedbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ExternalActions.start(this, intent, "Unable to open feedback.")
        }
    }

    /**
     * Opens the emoji picker panel. Commits the composing word first, for
     * the same reason [toggleNumberMode] does -- an emoji must land AFTER
     * the word, never inside a composing region. The repository load is a
     * safe no-op if the sequential startup chain already started it; the
     * panel shows a loading state until [EmojiRepository.isReady].
     */
    fun openEmojiPanel() {
        safeApply {
            if (isEmergencyMode) return@safeApply
            leaveVoiceModeForKeyboardInput()
            activeComposer.commit()
            updateSuggestions()
            emojiRepository.loadAsync()
            showEmojiPanel = true
        }
    }

    fun closeEmojiPanel() {
        safeApply {
            showEmojiPanel = false
            emojiSearchField = null
            if (isLowRam && ::emojiRepository.isInitialized) {
                emojiRepository.release()
            }
        }
    }

    /** Enters emoji search mode (query row + English key rows). */
    fun openEmojiSearch() {
        safeApply {
            emojiSearchField = TextFieldValue()
        }
    }

    /** Leaves search mode back to the browse panel. */
    fun closeEmojiSearch() {
        safeApply {
            emojiSearchField = null
        }
    }

    /** The search query row's clear (x) button: empty the query, stay in search. */
    fun clearEmojiSearchQuery() {
        safeApply {
            if (emojiSearchField != null) emojiSearchField = TextFieldValue()
        }
    }

    /**
     * The search BasicTextField's onValueChange: the only edits it can
     * originate itself are touch-driven (cursor moves, selection drags) --
     * text edits come through the key guards, which write [emojiSearchField]
     * directly.
     */
    fun updateEmojiSearchField(value: TextFieldValue) {
        safeApply {
            if (emojiSearchField != null) emojiSearchField = value
        }
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
        safeApply {
            editorGateway.commitText(emoji)
            recentEmojiStore.recordUse(emoji)
        }
    }

    /**
     * The recents list frozen for one panel-open: the panel snapshots this
     * once per open (its composition lifetime), so committing an emoji never
     * reorders the grid under the user's finger mid-session.
     */
    fun recentEmojiSnapshot(): List<String> = safeRun(emptyList()) {
        recentEmojiStore.snapshot()
    }

    /**
     * A tone was picked in the long-press popup: remember it (persisted, and
     * mirrored into [selectedSkinTones] so the cell recomposes to show it).
     * Picking the base (yellow) clears the preference. The caller commits
     * the picked emoji separately via [commitEmoji].
     */
    fun setSkinTone(base: String, variant: String) {
        safeApply {
            skinToneStore.set(base, variant)
            if (variant == base) selectedSkinTones.remove(base)
            else selectedSkinTones[base] = variant
        }
    }

    fun toggleLanguage() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            activeComposer.commit()
            // Flush per-word caches BEFORE flipping -- the cache key folds
            // per-language, and the ranked results would otherwise leak across
            // a toggle (English prefix "th" ranked with Amharic boosts, etc.).
            amharicSuggestionCache.clear()
            amharicCommitCandidateCache.clear()
            composingNgramBoost = null
            composingPredictionCasing = emptyMap()
            // Release the previously-active dictionary+ngram and load the
            // new one. On a low-RAM device this is the point where the OS
            // would have killed us under the old in-memory trie design; the
            // page-cached SQLite approach makes the swap cheap.
            val priorActive = if (isAmharic) amharicDictionary else englishDictionary
            val priorActiveNgrams = if (isAmharic) amharicNgrams else englishNgrams
            isAmharic = !isAmharic
            KeyboardPrefs.setAmharicMode(this, isAmharic)
            if (!isAmharic && numbersMode == NumbersMode.GEEZ_NUMBERS) {
                numbersMode = NumbersMode.NUMBERS
            }
            priorActive.release()
            priorActiveNgrams.release()
            ensureActiveLanguageStoreLoaded("after_toggle_language")
            suggestions = emptyList()
            suggestionsArePredictions = false
            updateSuggestions()
            MemoryProbe.snapshot("after_toggle_language_sync")
        }
    }

    private fun ensureActiveLanguageStoreLoaded(snapshotPrefix: String) {
        if (!::amharicDictionary.isInitialized) return
        if (isEmergencyMode) return
        val dictionary = if (isAmharic) amharicDictionary else englishDictionary
        val ngrams = if (isAmharic) amharicNgrams else englishNgrams
        if (dictionary.isReady && ngrams.isReady) {
            isLanguageSwitching = false
            return
        }
        val targetAmharic = isAmharic
        val loadGeneration = ++languageLoadGeneration
        isLanguageSwitching = true
        dictionary.loadAsync dictionaryReady@{
            if (loadGeneration != languageLoadGeneration || targetAmharic != isAmharic) {
                return@dictionaryReady
            }
            MemoryProbe.snapshot("${snapshotPrefix}_dictionary")
            isLanguageSwitching = false
            updateSuggestions()
            ngrams.loadAsync ngramsReady@{
                if (loadGeneration != languageLoadGeneration || targetAmharic != isAmharic) {
                    return@ngramsReady
                }
                MemoryProbe.snapshot("${snapshotPrefix}_ngrams")
                updateSuggestions()
            }
        }
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
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            activeComposer.commit()
            numbersMode = if (numbersMode == NumbersMode.OFF) NumbersMode.NUMBERS else NumbersMode.OFF
            updateSuggestions()
        }
    }

    fun toggleSymbolsPage() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            numbersMode = when (numbersMode) {
                NumbersMode.NUMBERS -> if (isAmharic) NumbersMode.GEEZ_NUMBERS else NumbersMode.SYMBOLS
                NumbersMode.GEEZ_NUMBERS -> NumbersMode.SYMBOLS
                NumbersMode.SYMBOLS -> NumbersMode.MORE_SYMBOLS
                NumbersMode.MORE_SYMBOLS -> NumbersMode.NUMBERS
                // The keypad's "*#(" key: exit to the full numbers/symbols page
                // (the keypad itself carries no symbols). The NUMBERS page's
                // "1234" key ([openKeypad]) is the way back in.
                NumbersMode.KEYPAD -> NumbersMode.NUMBERS
                NumbersMode.OFF -> NumbersMode.OFF
            }
        }
    }

    /**
     * The "1234" key on the NUMBERS page: shows the phone-style keypad
     * ([NumbersMode.KEYPAD]). No composer flush needed -- reaching the
     * NUMBERS page already committed any in-flight word -- but flushing is
     * harmless and keeps this safe if the key ever moves to a letter layout.
     */
    fun openKeypad() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            activeComposer.commit()
            numbersMode = NumbersMode.KEYPAD
            updateSuggestions()
        }
    }

    /**
     * Shift key tapped. A single tap toggles the one-shot SHIFT on/off; a
     * quick double tap engages CAPS_LOCK; a tap while caps-locked releases
     * it -- see [ShiftState.onShiftTap] for the full transition table. The
     * double-tap window is the platform's own double-tap timeout.
     */
    fun toggleShift() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            val now = SystemClock.uptimeMillis()
            val isDoubleTap = now - lastShiftTapUptimeMs <= ViewConfiguration.getDoubleTapTimeout()
            lastShiftTapUptimeMs = now
            shiftState = shiftState.onShiftTap(isDoubleTap)
        }
    }

    // Uptime of the most recent shift tap, for double-tap-to-caps-lock
    // detection. Zeroed when a character consumes shift, so shift-letter-shift
    // inside the window reads as two separate taps, not a double tap.
    private var lastShiftTapUptimeMs = 0L

    /**
     * Called after a character key commits its output. One-shot SHIFT
     * consumes itself and returns to OFF; CAPS_LOCK is left untouched since
     * it should keep capitalizing until explicitly turned off.
     */
    fun consumeShiftAfterCharacter() {
        safeApply {
            lastShiftTapUptimeMs = 0L
            if (shiftState == ShiftState.SHIFT) {
                shiftState = ShiftState.OFF
            }
        }
    }

    fun resetShift() {
        safeApply {
            shiftState = ShiftState.OFF
        }
    }

    /**
     * Numeric fields (number, phone, date/time input classes) get the
     * phone-style keypad automatically, Gboard-style; leaving them drops any
     * lingering keypad back to the letter layout, so a keypad engaged for
     * (or in) one field never leaks into an ordinary text field. The other
     * numeric pages are left alone -- they were the user's own choice.
     * Called per input session.
     */
    private fun resolveKeypadMode(editorInfo: EditorInfo?) {
        safeApply {
            val inputClass = (editorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
            val numericField = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            if (numericField) {
                numbersMode = NumbersMode.KEYPAD
            } else if (numbersMode == NumbersMode.KEYPAD) {
                numbersMode = NumbersMode.OFF
            }
        }
    }

    /**
     * Determines whether the current field accepts English
     * auto-capitalization. Default-ON for ordinary text fields (the way
     * Gboard/SwiftKey behave), because most editors never set
     * [InputType.TYPE_TEXT_FLAG_CAP_SENTENCES], so gating on that opt-in flag
     * left sentence capitalization off almost everywhere. Instead the field
     * only needs to be a text-class field ([InputType.TYPE_CLASS_TEXT]) whose
     * variation isn't in [NO_AUTOCAP_VARIATIONS] (password/email/URI/filter --
     * the fields where a stray capital is wrong or annoying). Also flags email
     * fields ([isEmailField]) from the same variation bits. Called per input
     * session.
     */
    private fun resolveAutoCap(editorInfo: EditorInfo?) {
        safeApply {
            val inputType = editorInfo?.inputType ?: 0
            // Default-ON for ordinary text fields (Gboard/SwiftKey behavior),
            // rather than only when the editor opts in via
            // TYPE_TEXT_FLAG_CAP_SENTENCES -- most apps never set that flag, so
            // gating on it left sentence capitalization off almost everywhere. The
            // NO_AUTOCAP_VARIATIONS deny-list (password/email/URI/filter) plus the
            // text-class check are what keep a stray capital out of the fields that
            // shouldn't get one.
            fieldAllowsAutoCap = InputTypePolicy.allowsAutoCap(inputType)
            isEmailField = InputTypePolicy.isEmailInputType(inputType)
            isPrivateField = InputTypePolicy.isPrivateInputType(inputType)
        }
    }

    /**
     * Capitalizes the first letter of a new sentence in English text fields by
     * arming one-shot [ShiftState.SHIFT], so the next letter comes out
     * capitalized and then reverts (and, via [matchCase], the suggestion strip
     * capitalizes there too). No-op in Amharic (Ge'ez has no case; shift
     * selects a consonant family), in numeric mode, mid-word, under caps-lock,
     * or when shift is already on. Only ever ARMS shift (never forces it off),
     * so it can't fight a manual shift the user set.
     *
     * [textBeforeCursor], when non-null, is used in place of re-reading the
     * field. Callers that just typed the whitespace ending a sentence (the
     * space in [onSpace], the newline in [onEnter]) pass the text as it will
     * read AFTERWARDS -- the pre-commit text plus the whitespace they are
     * adding -- because getTextBeforeCursor doesn't always reflect a
     * just-committed space synchronously. Re-reading after the commit could
     * therefore miss the trailing space that marks the sentence end ("End. "),
     * leaving the next word lowercase -- the post-period bug this avoids.
     */
    private fun maybeAutoCapitalize(textBeforeCursor: CharSequence? = null) {
        safeApply {
            if (isAmharic || isNumberMode || !fieldAllowsAutoCap) return@safeApply
            if (shiftState != ShiftState.OFF || activeComposer.isComposing) return@safeApply
            val before = textBeforeCursor
                ?: editorGateway.textBeforeCursor(SENTENCE_LOOKBEHIND)?.value
            if (SentenceCase.startsNewSentence(before)) {
                shiftState = ShiftState.SHIFT
            }
        }
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
     * On the letter layouts, both languages compose the raw Latin inline
     * (underlined, in the field's composing region) as the user types, so
     * the current word stays replaceable by a tapped suggestion. For Amharic
     * nothing is transliterated into the field until commit -- a keypress is
     * ambiguous until the syllable (or word) ends, so the fidel readings
     * only ever live in the suggestion strip (see [amharicSuggestions])
     * while typing. Word-terminating keys
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
        safeApply {
            // Emoji search intercepts the real English key rows: keystrokes build
            // the query instead of touching the field. Same shift resolution as
            // the normal path so the query looks like what was typed (search
            // itself lowercases).
            emojiSearchField?.let { field ->
                if (showEmojiPanel) {
                    emojiSearchField =
                        field.insertAtCursor(if (isShiftEnabled) latin.uppercase() else latin.lowercase())
                    consumeShiftAfterCharacter()
                    return@safeApply
                }
            }
            leaveVoiceModeForKeyboardInput()
            val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

            // Email fields use a wider word-character set so the entire email
            // token (local-part + '@' + domain + '.' + digits) stays in the
            // composing region. That lets a chip tap replace the whole token in
            // one commitText call rather than just the trailing fragment, and
            // also keeps the live in-progress token in englishComposer.raw for
            // the email-suggestion pipeline to key off of.
            val wordChar = if (isEmailField) isEmailWordCharacter(output) else isWordCharacter(output)

            when {
                isNumberMode -> {
                    safeIc { it.commitText(output, 1) }
                }
                !wordChar -> {
                    activeComposer.commit()
                    val text = if (isAmharic) Transliterator.transliterate(output) else output
                    safeIc { it.commitText(text, 1) }
                }
                // Email fields: always Latin passthrough, regardless of the
                // user's current language mode -- addresses don't transliterate.
                // The fidel-corner preview on the Amharic layout's keys is
                // unaffected (it's purely visual, see KeyRow / AmharicTable).
                isEmailField -> {
                    englishComposer.onCharacter(output)
                }
                isAmharic -> {
                    amharicComposer.onCharacter(output)
                }
                else -> {
                    englishComposer.onCharacter(output)
                }
            }

            consumeShiftAfterCharacter()
            updateSuggestions()
        }
    }

    /**
     * Whether [output] belongs inside a composing word rather than
     * terminating one. Letters in both languages; apostrophe (English
     * contractions, and the SERA spelling of the glottal አ family) and
     * backtick (SERA pharyngeal ዐ) also count -- neither is on a letter
     * layout today, but if one is ever added it must feed the composer,
     * not chop the word.
     */
    private fun isWordCharacter(output: String) = isComposingWordCharacter(output)

    /**
     * The email-field word-character predicate. Same as
     * [isComposingWordCharacter] plus '@', '.', and ASCII digits, so the
     * entire local-part@domain.tld token stays inside the composing region.
     * Used only by [onCharacter] when [isEmailField] is true; the wider
     * word-character set is intentionally local to email fields so it can't
     * interfere with Amharic transliteration elsewhere.
     */
    private fun isEmailWordCharacter(output: String): Boolean {
        if (output.isEmpty()) return false
        for (c in output) {
            val ok = c.isLetter() ||
                c == '\'' ||
                c == '`' ||
                c == '@' ||
                c == '.' ||
                (c in '0'..'9')
            if (!ok) return false
        }
        return true
    }

    private val suggestionRefreshGate = SuggestionRefreshGate()

    fun onDeleteRepeatStart() {
        safeApply {
            suggestionRefreshGate.beginDeleteGesture()
        }
    }

    fun onDeleteRepeatEnd() {
        safeApply {
            val pendingRefresh = suggestionRefreshGate.endDeleteGesture()
            maybeResumeWordAfterDeleteRepeat()
            if (pendingRefresh || activeComposer.isComposing) updateSuggestions()
        }
    }

    private fun maybeResumeWordAfterDeleteRepeat() {
        safeApply {
            if (activeComposer.isComposing) return@safeApply
            val extracted = editorGateway
                .extractedText()
                ?.value
                ?: return@safeApply
            if (extracted.selectionStart != extracted.selectionEnd) return@safeApply
            maybeResumeWordAtCursor(extracted.startOffset + extracted.selectionStart)
        }
    }

    /**
     * Backspace pressed. Try to shrink the active composing buffer first --
     * one full rendered character at a time, which in Amharic can be a
     * multi-Latin-char span (so "she" -> ሸ, backspace -> nothing). If the
     * buffer is empty, fall back to deleting a character from the text
     * field itself.
     */
    fun onDelete() {
        safeApply {
            // In emoji search, backspace edits the query, not the field. (The
            // browse panel's own backspace key runs with a null query, so it
            // falls through to real field deletion below.) Deletes the selection
            // if there is one, else the character before the cursor.
            emojiSearchField?.let { field ->
                if (showEmojiPanel) {
                    emojiSearchField = when {
                        !field.selection.collapsed -> field.insertAtCursor("")
                        field.selection.start > 0 -> {
                            val cut = field.selection.start
                            TextFieldValue(
                                text = field.text.removeRange(cut - 1, cut),
                                selection = TextRange(cut - 1)
                            )
                        }
                        else -> field
                    }
                    return@safeApply
                }
            }
            leaveVoiceModeForKeyboardInput()
            if (activeComposer.isComposing) {
                val raw = activeComposer.raw
                val before = editorGateway
                    .textBeforeCursor(raw.length, optional = false)
                    ?.value
                if (!isComposerTextImmediatelyBeforeCursor(raw, before)) {
                    activeComposer.abandon()
                }
            }
            if (activeComposer.onBackspace()) {
                updateSuggestions()
                return@safeApply
            }
            val selected = editorGateway.selectedText()?.value
            if (!selected.isNullOrEmpty()) {
                editorGateway.commitText("")
            } else {
                val before = editorGateway.textBeforeCursor(32, optional = false)?.value
                val cluster = EmojiBackspace.lastClusterLength(before ?: "")
                editorGateway.deleteBeforeCursor(cluster.coerceAtLeast(1))
            }
            updateSuggestions()
        }
    }

    fun commitText(text: String) {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            activeComposer.commit()
            safeIc { it.commitText(text, 1) }
        }
    }

    /**
     * Space commits any in-flight word first, then inserts a space.
     *
     * [WordComposer.commit] replaces the underlined raw Latin with its
     * commitTransform: for Amharic that's the top-ranked fidel reading (the
     * same as suggestions[0], highlighted in the strip) -- so space picks
     * the default reading and a tap is only needed for a NON-default one;
     * for English it finalizes the inline composed word as-is. With no word
     * in flight it's a plain space.
     */
    fun onSpace() {
        safeApply {
            // CLDR annotations are multi-word ("red heart"), so space belongs to
            // the emoji search query, not the field.
            emojiSearchField?.let { field ->
                if (showEmojiPanel) {
                    emojiSearchField = field.insertAtCursor(" ")
                    return@safeApply
                }
            }
            leaveVoiceModeForKeyboardInput()
            activeComposer.commit()
            // Read the text as it stands BEFORE the space (committed + any
            // still-composing text is already in the field), then judge the
            // sentence boundary from that plus the space we're about to add. A
            // post-commit re-read is unreliable -- some editors don't surface the
            // just-committed space in getTextBeforeCursor right away, so the
            // trailing space that ends the sentence ("End. ") goes missing and the
            // next word never capitalizes.
            val beforeSpace = editorGateway.textBeforeCursor(SENTENCE_LOOKBEHIND)?.value
            safeIc { it.commitText(" ", 1) }
            updateSuggestions()
            maybeAutoCapitalize(beforeSpace?.let { "$it " })
        }
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
        safeApply {
            // In emoji search, enter commits the top result (with its remembered
            // skin tone) -- it never submits the field's IME action.
            emojiSearchQuery?.let { query ->
                if (showEmojiPanel) {
                    emojiRepository.data?.search(query)?.firstOrNull()?.let {
                        commitEmoji(selectedSkinTones[it.base] ?: it.base)
                    }
                    return@safeApply
                }
            }
            leaveVoiceModeForKeyboardInput()
            activeComposer.commit()
            val beforeEnter = editorGateway.textBeforeCursor(SENTENCE_LOOKBEHIND)?.value
            if (enterAction == EnterAction.NEWLINE) {
                editorGateway.sendEnter()
            } else {
                editorGateway.performEditorAction(editorActionId)
            }
            updateSuggestions()
            // A newline starts a fresh line -> capitalize its first letter, judged
            // from the pre-newline text plus the "\n" just added. An editor-action
            // Enter (search/send/go/...) inserts no newline, so there fall back to
            // a fresh read instead.
            maybeAutoCapitalize(
                if (enterAction == EnterAction.NEWLINE) beforeEnter?.let { "$it\n" } else null
            )
        }
    }

    /**
     * Resolves how the Enter key should present and behave for the current
     * field from its `EditorInfo`. A multi-line field, or one that opts out of
     * an enter action (`IME_FLAG_NO_ENTER_ACTION`), gets a plain newline;
     * otherwise the declared IME action (GO/SEARCH/SEND/NEXT/PREVIOUS/DONE)
     * drives both the key's icon and what Enter fires.
     */
    private fun resolveEnterAction(editorInfo: EditorInfo?) {
        safeApply {
            if (editorInfo == null) {
                enterAction = EnterAction.NEWLINE
                editorActionId = EditorInfo.IME_ACTION_UNSPECIFIED
                return@safeApply
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
    }

    /**
     * A suggestion chip was tapped: swap the current composing text for the
     * full suggested word and clear the strip.
     */
    fun onSuggestionTapped(word: String) {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            activeComposer.commitSuggestion(word)
            updateSuggestions()
        }
    }

    // ----------------------------
    // IME LIFECYCLE
    // ----------------------------

    override fun onEvaluateInputViewShown(): Boolean {
        return safeRun(true) {
            super.onEvaluateInputViewShown()
            true
        }
    }

    override fun onCreateInputView(): View {
        return safeRun(View(this)) {
            val inputView = AddiyonKeyboardView(this)
            window?.window?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(this)
                decorView.setViewTreeSavedStateRegistryOwner(this)
            }
            inputView.setViewTreeLifecycleOwner(this)
            inputView.setViewTreeSavedStateRegistryOwner(this)

            ensureLifecycleStarted()
            updateSystemNavigationBar()

            inputView
        }
    }

    override fun onCreate() {
        safeApply {
            super.onCreate()
            currentInstance = this
            val activityManager = getSystemService(android.app.ActivityManager::class.java)
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            safeApply { activityManager?.getMemoryInfo(memoryInfo) }
            isLowRam = DeviceMemoryPolicy.isLowRam(
                systemLowRam = activityManager?.isLowRamDevice == true,
                totalMemoryBytes = memoryInfo.totalMem,
            )
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                // Debug-only: surfaces accidental main-thread disk/network work in
                // Logcat during development, without affecting release builds.
                try {
                    StrictMode.setThreadPolicy(
                        StrictMode.ThreadPolicy.Builder()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .detectNetwork()
                            .penaltyLog()
                            .build()
                    )
                } catch (oom: OutOfMemoryError) {
                    SafeLog.e(oom, "onCreate StrictMode OOM")
                } catch (t: Throwable) {
                    SafeLog.e(t, "onCreate StrictMode")
                }
            }
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            refreshTheme(resources.configuration)
            refreshNumberRow()
            refreshKeyboardHeightScale()
            refreshFeedbackPrefs()
            // Restore the last-used language BEFORE the dictionary loads below:
            // the active language's dictionary is deliberately loaded first.
            isAmharic = KeyboardPrefs.amharicMode(this)
            KeyboardPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)

            amharicStore = SQLiteLanguageStore(
                this,
                "amharic.db",
                isLowRam,
                this::enterEmergencyMode,
            )
            englishStore = SQLiteLanguageStore(
                this,
                "english.db",
                isLowRam,
                this::enterEmergencyMode,
            )
            amharicDictionary = SQLiteDictionary(
                amharicStore,
                precomputedPrefixLength = 1,
                normalize = EthiopicNormalizer::normalize,
            )
            englishDictionary = SQLiteDictionary(
                englishStore,
                precomputedPrefixLength = 2,
                normalize = ::englishFold,
            )
            amharicNgrams = SQLiteNgramModel(amharicStore, EthiopicNormalizer::normalize)
            englishNgrams = SQLiteNgramModel(englishStore, ::englishFold)
            emojiRepository = EmojiRepository(this, onOutOfMemory = this::enterEmergencyMode)
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
            // SQLite-backed dictionaries open on a background thread (asset
            // copy + DB open). The active language loads first; the inactive
            // language is only constructed on toggle, so until the user
            // switches languages we hold a single open DB file. That keeps
            // the resident footprint at one dictionary (English or Amharic)
            // plus one ngram model, instead of two dictionaries + two models
            // competing for the page cache on low-RAM devices.
            ensureActiveLanguageStoreLoaded("after_active")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        safeApply {
            super.onConfigurationChanged(newConfig)
            refreshTheme(newConfig)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return safeRun(false) { super.onEvaluateFullscreenMode() }
    }

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        safeApply {
            idleReleaseHandler.removeCallbacks(idleRelease)
            super.onStartInput(editorInfo, restarting)
            // The EditorInfo can change between sessions even when the input view
            // stays mounted (e.g. user taps a different field while our keyboard
            // is still up). onStartInputView doesn't always fire in that case, so
            // resolve the input-type flags here as well -- otherwise the
            // fieldAllowsAutoCap / isEmailField state from the PRIOR field would
            // survive the rebind and a stray capital could leak into an email
            // field, or an email chip suggestion could keep showing in a plain
            // text field. resolveAutoCap is idempotent.
            resolveAutoCap(editorInfo)
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        safeApply {
            super.onStartInputView(editorInfo, restarting)
            editorGateway.beginSession()
            // Fresh (non-restarting) sessions feed the engagement counter behind
            // the one-time in-app review prompt (see ReviewPromptPolicy).
            if (!restarting) KeyboardPrefs.recordUsageSession(this)
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
            // Whether English auto-capitalization applies in this field.
            resolveAutoCap(editorInfo)
            // Email fields must NEVER carry an armed capital across from a prior
            // text field: shiftState may be ShiftState.SHIFT (one-shot, left over
            // from a sentence-end auto-cap in the previous field) or even
            // ShiftState.CAPS_LOCK (user pressed double-shift in the previous
            // field). resetShift() drops both. The per-key shift path in
            // onCharacter (line 1355) would otherwise uppercase the very first
            // letter typed into the email field, defeating
            // fieldAllowsAutoCap == false.
            if (isEmailField) resetShift()
            // Email chips are not relevant outside email fields; flush them.
            if (!isEmailField && emailSuggestions.isNotEmpty()) {
                emailSuggestions = emptyList()
            }
            // Numeric fields open on the phone-style keypad.
            resolveKeypadMode(editorInfo)
            ensureActiveLanguageStoreLoaded("after_input_start")
            updateSuggestions()
            // Arm a capital for the first letter if the caret opens at a sentence
            // start (empty field, or resumed after a sentence terminator).
            maybeAutoCapitalize()

            // Catch any theme change that happened while the keyboard was hidden,
            // and make sure the nav bar strip is colored correctly every time
            // the keyboard becomes visible again.
            refreshTheme(resources.configuration)
            ensureLifecycleResumed()
            maybeStartPendingVoiceAfterPermission()
        }
    }

    override fun onWindowShown() {
        safeApply {
            super.onWindowShown()
            maybeStartPendingVoiceAfterPermission()
        }
    }

    /**
     * The framework calls this whenever the cursor or selection changes in
     * the target field -- both when WE change it (by pushing composing text)
     * and when the USER changes it (by tapping somewhere else). Only a
     * collapsed caret at the composing region's end matches our own
     * setComposingText updates. Every other selection finalizes the visible
     * text in place before another key can rewrite the composing region.
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
        safeApply {
            super.onUpdateSelection(
                oldSelStart, oldSelEnd,
                newSelStart, newSelEnd,
                candidatesStart, candidatesEnd
            )

            val cursorAtComposingEnd = isSelectionAtComposingEnd(
                selectionStart = newSelStart,
                selectionEnd = newSelEnd,
                composingStart = candidatesStart,
                composingEnd = candidatesEnd
            )

            // Voice dictation in flight: a deliberate cursor move finalizes the
            // utterance where it was showing and restarts recognition cleanly at
            // the new position.
            if (voiceComposer.isComposing) {
                if (!cursorAtComposingEnd) {
                    finalizeVoiceComposing()
                    voiceInputController?.restartSession()
                }
                return@safeApply
            }

            if (activeComposer.isComposing) {
                // Movement consistent with our own composing pushes: nothing to do.
                if (cursorAtComposingEnd) return@safeApply
                // The user walked away from the word we were composing.
                activeComposer.abandon()
                updateSuggestions()
            } else {
                // Nothing composing: if the caret just landed at the end of a
                // committed word, adopt it back into composition so the strip
                // offers its completions again (cursor-aware suggestions);
                // otherwise refresh (or clear) the next-word predictions, which
                // depend on the words before the cursor.
                if (newSelStart == newSelEnd) maybeResumeWordAtCursor(newSelStart)
                updateSuggestions()
            }
        }
    }

    /**
     * Adopts the committed word the caret just landed at the END of back into
     * composition (Gboard-style cursor-aware suggestions): type "cana",
     * space, backspace over the space -- the caret sits after "cana" again
     * and the strip should offer "Canada" as if the word were still being
     * typed. The field word is lifted into the composing region
     * (setComposingRegion) and the composer re-seeded via
     * [WordComposer.resume].
     *
     * English adopts the literal field word. Amharic needs the raw LATIN
     * behind the committed fidel: [amharicCommitHistory] first, then
     * [AmharicWordReverser]'s round-trip-verified reversal -- and when
     * neither knows the word, no resume rather than a guess.
     *
     * Suppressed wherever the composer is out of play (numeric pages,
     * emoji panel, voice). [cursorPosition] is the collapsed selection from
     * onUpdateSelection; the word itself is read fresh from the connection,
     * so a stale callback sees the field's CURRENT tail and simply finds a
     * boundary character instead of a word.
     */
    private fun maybeResumeWordAtCursor(cursorPosition: Int) {
        safeApply {
            if (cursorPosition <= 0 || isNumberMode || showEmojiPanel || isPrivateField) {
                return@safeApply
            }
            if (voiceUiState.isVoiceMode || suggestionRefreshGate.isDeleteGestureActive) return@safeApply
            if (activeComposer.isComposing) return@safeApply
            val afterRead = editorGateway.textAfterCursor(1) ?: return@safeApply
            val after = afterRead.value
            if (after.isNotEmpty() && (after[0].isLetter() || after[0] == '\'')) return@safeApply
            val beforeRead = editorGateway.textBeforeCursor(ResumableWord.LOOKBEHIND)
                ?: return@safeApply
            if (beforeRead.token != afterRead.token) return@safeApply
            val before = beforeRead.value
            if (isAmharic) {
                val fidel = ResumableWord.trailingEthiopicWord(before) ?: return@safeApply
                val latin = amharicCommitHistory[fidel]
                    ?: AmharicWordReverser.reverse(fidel)
                    ?: return@safeApply
                // Guard on the return value: an editor that doesn't support
                // composing regions must not get resume()'s setComposingText,
                // which would INSERT a duplicate instead of replacing the word.
                if (!editorGateway.setComposingRegion(
                        cursorPosition - fidel.length,
                        cursorPosition,
                        beforeRead.token
                    )
                ) return@safeApply
                amharicComposer.resume(latin)
            } else {
                val word = ResumableWord.trailingLatinWord(before) ?: return@safeApply
                if (!editorGateway.setComposingRegion(
                        cursorPosition - word.length,
                        cursorPosition,
                        beforeRead.token
                    )
                ) return@safeApply
                englishComposer.resume(word)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        safeApply {
            super.onFinishInputView(finishingInput)
            // Field is going away without an explicit commit. Finalize the
            // composing region in place so hiding the keyboard can never erase
            // or replace text.
            activeComposer.finish()
            updateSuggestions()
            voiceInputController?.stop()
            finalizeVoiceComposing()
            resetVoiceUi()
            pauseLifecycleIfResumed()
            if (isLowRam) {
                idleReleaseHandler.removeCallbacks(idleRelease)
                idleReleaseHandler.postDelayed(idleRelease, LOW_RAM_IDLE_RELEASE_MS)
            }
            editorGateway.endSession()
        }
    }

    override fun onTrimMemory(level: Int) {
        safeApply {
            super.onTrimMemory(level)
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                amharicSuggestionCache.clear()
                amharicCommitCandidateCache.clear()
                composingNgramBoost = null
                composingPredictionCasing = emptyMap()
                if (::amharicDictionary.isInitialized) amharicDictionary.clearCache()
                if (::englishDictionary.isInitialized) englishDictionary.clearCache()
                if (::emojiRepository.isInitialized && !showEmojiPanel) {
                    emojiRepository.release()
                }
            }
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                languageLoadGeneration += 1
                isLanguageSwitching = false
                if (isAmharic && ::amharicStore.isInitialized) {
                    amharicStore.release()
                } else if (::englishStore.isInitialized) {
                    englishStore.release()
                }
            }
        }
    }

    override fun onDestroy() {
        safeApply {
            super.onDestroy()
            currentInstance = null
            KeyboardPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
            voiceInputController?.destroy()
            resetVoiceUi()
            invalidateSuggestionWork()
            suggestionExecutor.shutdownNow()
            idleReleaseHandler.removeCallbacks(idleRelease)
            languageLoadGeneration += 1
            if (::amharicStore.isInitialized) amharicStore.release()
            if (::englishStore.isInitialized) englishStore.release()
            if (::emojiRepository.isInitialized) emojiRepository.release()
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }

    private fun ensureLifecycleStarted() {
        safeApply {
            val state = lifecycleRegistry.currentState
            if (state == Lifecycle.State.DESTROYED) return@safeApply
            if (!state.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
        }
    }

    private fun ensureLifecycleResumed() {
        safeApply {
            ensureLifecycleStarted()
            if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        }
    }

    private fun pauseLifecycleIfResumed() {
        safeApply {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
        }
    }
}

/**
 * Replaces the selection (or, when collapsed, inserts at the cursor) and
 * leaves the cursor after the inserted text. With "" this is
 * delete-selection.
 */
private fun TextFieldValue.insertAtCursor(insert: String): TextFieldValue {
    val start = selection.min
    return TextFieldValue(
        text = text.replaceRange(start, selection.max, insert),
        selection = TextRange(start + insert.length)
    )
}
