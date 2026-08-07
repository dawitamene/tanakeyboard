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
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.addiyon.keyboard.composing.ResumableWord
import com.addiyon.keyboard.composing.SuggestionKind
import com.addiyon.keyboard.composing.TypingController
import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.composing.isCompletionChipTapValid
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.EnterActionPolicy
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
import com.addiyon.keyboard.suggestion.PersonalDictionary
import com.addiyon.keyboard.suggestion.NgramContext
import com.addiyon.keyboard.suggestion.PerWordCache
import com.addiyon.keyboard.suggestion.PredictionCache
import com.addiyon.keyboard.suggestion.PredictionLanguage
import com.addiyon.keyboard.suggestion.SQLiteDictionary
import com.addiyon.keyboard.suggestion.SQLiteLanguageStore
import com.addiyon.keyboard.suggestion.SQLiteNgramModel
import com.addiyon.keyboard.suggestion.SubstitutionCost
import com.addiyon.keyboard.suggestion.Suggestion
import com.addiyon.keyboard.suggestion.SuggestionTrace
import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import com.addiyon.keyboard.suggestion.matchCase
import com.addiyon.keyboard.telemetry.Telemetry
import com.addiyon.keyboard.telemetry.TelemetryLanguage
import com.addiyon.keyboard.telemetry.TelemetryLayout
import com.addiyon.keyboard.telemetry.TelemetrySuggestionKind
import com.addiyon.keyboard.telemetry.TelemetryVoiceError
import com.addiyon.keyboard.telemetry.TelemetryVoiceResult
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.util.MemoryProbe
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ai.AiController
import com.addiyon.keyboard.ai.AiError
import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.ai.AiRepository
import com.addiyon.keyboard.ai.AiServiceFactory
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ai.countWords
import com.addiyon.keyboard.ai.todayIso
import com.addiyon.keyboard.ui.SuggestionTap
import com.addiyon.keyboard.ui.SuggestionUiState
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.voice.VoiceComposer
import com.addiyon.keyboard.voice.VoiceErrorKind
import com.addiyon.keyboard.voice.VoiceInputController
import com.addiyon.keyboard.voice.VoiceUiState
import com.addiyon.keyboard.voice.isVoiceMode
import java.util.concurrent.ArrayBlockingQueue
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * Next-word fallback when the n-gram model has no successor for the current
 * context: the top most frequent dictionary words, capped at the English
 * strip's three fixed slots (Amharic reuses [AMHARIC_SUGGESTION_LIMIT]).
 */
private const val PREDICTION_FALLBACK_ENGLISH_LIMIT = 3

/**
 * English strip capacity: exact-prefix completions first, then up to
 * [ENGLISH_FUZZY_LIMIT] typo corrections appended below them.
 */
private const val ENGLISH_EXACT_LIMIT = 3
private const val ENGLISH_FUZZY_LIMIT = 2
private const val ENGLISH_SUGGESTION_LIMIT = ENGLISH_EXACT_LIMIT + ENGLISH_FUZZY_LIMIT
private const val LOW_RAM_IDLE_RELEASE_MS = 20_000L
private const val PREDICTION_CACHE_SIZE = 64
private const val PREDICTION_IDENTITY_BEFORE = 256
private const val PREDICTION_IDENTITY_AFTER = 128

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

private fun VoiceErrorKind.telemetryCategory(): TelemetryVoiceError = when (this) {
    VoiceErrorKind.PERMISSION -> TelemetryVoiceError.PERMISSION
    VoiceErrorKind.NETWORK,
    VoiceErrorKind.SERVER,
    VoiceErrorKind.SERVER_DISCONNECTED -> TelemetryVoiceError.NETWORK
    VoiceErrorKind.NO_SPEECH -> TelemetryVoiceError.SILENCE
    VoiceErrorKind.RECOGNIZER_BUSY,
    VoiceErrorKind.TOO_MANY_REQUESTS -> TelemetryVoiceError.BUSY
    VoiceErrorKind.LANGUAGE_UNAVAILABLE,
    VoiceErrorKind.LANGUAGE_UNSUPPORTED,
    VoiceErrorKind.UNAVAILABLE -> TelemetryVoiceError.UNAVAILABLE
    VoiceErrorKind.AUDIO,
    VoiceErrorKind.CLIENT,
    VoiceErrorKind.UNKNOWN -> TelemetryVoiceError.OTHER
}

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

    var aiUiState by mutableStateOf(AiUiState())
        private set

    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var aiRepository: AiRepository
    private lateinit var aiController: AiController

    private fun currentAiQuota(): AiQuota {
        val today = todayIso()
        val storedDay = KeyboardPrefs.aiQuotaDay(this)
        val storedUsed = KeyboardPrefs.aiWordsUsedToday(this)
        val limit = KeyboardPrefs.aiDailyLimit(this)
        if (storedDay != today) {
            KeyboardPrefs.setAiQuotaDay(this, today)
            KeyboardPrefs.setAiWordsUsedToday(this, 0)
            return AiQuota(0, limit, limit, today)
        }
        val used = storedUsed.coerceIn(0, limit)
        return AiQuota(used, limit, (limit - used).coerceAtLeast(0), today)
    }

    private fun consumeAiQuota(words: Int) {
        val quota = currentAiQuota()
        val newUsed = (quota.used + words).coerceAtMost(quota.limit)
        KeyboardPrefs.setAiWordsUsedToday(this, newUsed)
        aiUiState = aiUiState.copy(quota = currentAiQuota())
    }

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
    private var cursorKnownAtFieldStart = false
    private var autoShiftArmed = false

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

    private var nonVoiceSuggestionUiState: SuggestionUiState = SuggestionUiState.Toolbar
    private var suggestionActionGeneration = 0L
    private var publishedSuggestionAction: PublishedSuggestionAction? = null

    private data class PublishedSuggestionAction(
        val generation: Long,
        val editorToken: EditorToken,
        val caretWord: String?,
        val predictionIdentity: EditorContentIdentity?,
        val amharic: Boolean,
        val emailField: Boolean,
        val privateField: Boolean,
        val numberMode: Boolean
    )

    var suggestionUiState by mutableStateOf<SuggestionUiState>(SuggestionUiState.Toolbar)
        private set

    val suggestions: List<String>
        get() = when (val state = nonVoiceSuggestionUiState) {
            is SuggestionUiState.WordCompletions -> state.words
            is SuggestionUiState.NextWordPredictions -> state.words
            else -> emptyList()
        }

    val emailSuggestions: List<EmailChip>
        get() = (nonVoiceSuggestionUiState as? SuggestionUiState.EmailSuggestions)
            ?.chips
            .orEmpty()

    val suggestionsArePredictions: Boolean
        get() = nonVoiceSuggestionUiState is SuggestionUiState.NextWordPredictions

    val isLanguageSwitching: Boolean
        get() = nonVoiceSuggestionUiState is SuggestionUiState.LoadingLanguage

    /**
     * The controller's raw buffer -- the word the strip is answering.
     * Exposed for instrumented tests, which need to assert that the buffer
     * tracks what is actually in the field across clears and caret moves.
     */
    @get:VisibleForTesting
    val composingBufferForTest: String
        get() = typingController.buffer

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
    // One TypingController owns every edit the keyboard makes: the live
    // composing word, caret-aware word resume, and chip-tap replacement. It
    // never names an absolute document offset (see composing/TypingController.kt),
    // which is what makes it behave identically in plain EditTexts and in
    // rich/Compose/WebView fields whose reported positions cannot be trusted.
    // Language/field-specific behaviour comes from [typingProfile], read fresh
    // on every use, so a language or field-type change takes effect without
    // rebuilding the controller.
    private val typingController = TypingController(
        editor = editorGateway,
        profile = ::typingProfile,
        onWordCommitted = ::rememberWord
    )

    private lateinit var personalDictionary: PersonalDictionary

    private fun rememberWord(word: String) {
        if (!::personalDictionary.isInitialized || isPrivateField || isNumberMode) return
        personalDictionary.learn(word)
        try {
            val before = editorGateway.textBeforeCursor(ResumableWord.LOOKBEHIND, optional = true)?.value
            val after = editorGateway.textAfterCursor(1, optional = true)?.value ?: ""
            if (before != null) {
                val email = ResumableWord.emailWordEndingAtCursor(before, after)
                if (email != null && email != word && '@' in email) personalDictionary.learn(email)
            }
        } catch (_: Throwable) {
        }
        KeyboardPrefs.setPersonalDictionary(this, personalDictionary.encode())
    }

    /**
     * The typing rules for the current language and field:
     *
     *  - Email fields are always Latin (addresses don't transliterate) with a
     *    wider word-character set, so the whole local-part@domain.tld token
     *    stays one composed word and a chip tap replaces it in one commit.
     *  - Amharic composes raw SERA Latin inline and swaps in the top-ranked
     *    fidel reading on commit; committed fidel words can only be re-opened
     *    when this keyboard composed them this session (remembersRawLatin --
     *    reverse-transliterating arbitrary fidel would be a guess).
     *  - English composes/commits Latin verbatim.
     */
    private fun typingProfile(): TypingProfile = when {
        isEmailField -> TypingProfile(
            isWordCharacter = ::isEmailWordCharacter,
            wordEndingAtCursor = ResumableWord::emailWordEndingAtCursor
        )
        isAmharic -> TypingProfile(
            isWordCharacter = ::isComposingWordCharacter,
            commitTransform = { raw -> topAmharicCandidate(raw) },
            transformStandalone = { raw -> Transliterator.transliterate(raw) },
            wordEndingAtCursor = ResumableWord::amharicWordEndingAtCursor,
            remembersRawLatin = true
        )
        else -> TypingProfile(
            isWordCharacter = ::isComposingWordCharacter,
            wordEndingAtCursor = ResumableWord::latinWordEndingAtCursor
        )
    }

    private fun telemetryLanguage(): TelemetryLanguage =
        if (isAmharic) TelemetryLanguage.AMHARIC else TelemetryLanguage.ENGLISH

    /**
     * The committed word the caret sits at the end of, if any -- the word the
     * suggestion strip answers when nothing is being composed, and the word a
     * completion chip tap re-opens and replaces (see WordAdoption). Latin
     * pipelines only: Amharic's buffer is SERA Latin while its field text is
     * fidel, so a committed fidel word is never a suggestion lookup key
     * (mirrors the old allowsCommittedWordResume rule).
     *
     * Read lazily at each use (never cached): two short cursor-relative reads,
     * no absolute offsets involved. Optional, so an editor whose reads turn
     * slow simply degrades to next-word predictions instead of blocking.
     */
    private data class CaretWord(val word: String, val token: EditorToken)

    private fun currentCaretWord(): CaretWord? {
        if (isAmharic && !isEmailField) return null
        if (typingController.isComposing) return null
        val profile = typingProfile()
        val before = editorGateway.textBeforeCursor(ResumableWord.LOOKBEHIND, optional = true)
            ?: return null
        val after = editorGateway.textAfterCursor(1, optional = true)
            ?.takeIf { it.token.sameEditorState(before.token) } ?: return null
        val word = profile.wordEndingAtCursor(before.value, after.value) ?: return null
        return CaretWord(word, before.token)
    }

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
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.Private)
                return@safeApply
            }
            if (!::amharicDictionary.isInitialized || isNumberMode || isEmergencyMode) {
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.Toolbar)
                return@safeApply
            }
            if (isEmailField) {
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                val token = if (typingController.isComposing) {
                    typingController.buffer
                } else {
                    currentCaretWord()?.word.orEmpty()
                }
                publishEmailSuggestions(
                    EmailSuggestions.emailChipsFor(token, personalDictionary.emailAddresses())
                )
                return@safeApply
            }

            val amharic = isAmharic
            val composing = typingController.isComposing
            val caret = if (composing) null else currentCaretWord()
            val typed = when {
                composing -> typingController.buffer
                caret != null -> caret.word
                else -> ""
            }
            val contextReader =
                if (amharic) NgramContext.AMHARIC else NgramContext.ENGLISH
            val capturedContext = currentBoundaryContext(amharic)
                ?: if (composing) {
                    composingContextForWord(amharic, contextReader)
                } else {
                    captureNgramContext(contextReader)
                }
            val context = capturedContext?.context ?: NgramContext.EMPTY
            val store = if (amharic) amharicStore else englishStore

            if (store.isLoading) {
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.LoadingLanguage)
                return@safeApply
            }
            if (typed.isEmpty()) {
                activeCompletionKey = null
                clearComposingContextCache()
                if (amharic) {
                    amharicSuggestionCache.clear()
                    amharicCommitCandidateCache.clear()
                }
                invalidateCompletionWork()
                if (context.prev1 == null) {
                    invalidatePredictionWork()
                    publishSuggestionState(SuggestionUiState.Toolbar)
                    return@safeApply
                }
                val ngrams = ngramModelFor(amharic)
                if (ngrams == null || !ngrams.isReady) {
                    invalidatePredictionWork()
                    publishSuggestionState(SuggestionUiState.Toolbar)
                    return@safeApply
                }
                val request = PredictionRequestKey(
                    amharic = amharic,
                    prev2 = context.prev2,
                    prev1 = context.prev1,
                    limit = if (isLowRam) 3 else NEXT_WORD_LIMIT
                )
                val contextToken = capturedContext?.editorToken
                if (contextToken == null) {
                    invalidatePredictionWork()
                    publishSuggestionState(SuggestionUiState.Toolbar)
                    return@safeApply
                }
                val activeRequest = activePredictionRequest
                if (
                    activeRequest?.key == request &&
                    activeRequest.editorToken.sameEditorState(contextToken) &&
                    activeRequest.contentIdentity == capturedContext.contentIdentity
                ) {
                    return@safeApply
                }
                val language = if (amharic) PredictionLanguage.AMHARIC else PredictionLanguage.ENGLISH
                val cached = predictionCache.get(language, context.prev2, context.prev1, request.limit)
                if (cached != null && cached.isNotEmpty()) {
                    val merged = cached.map { it.word }.let { words ->
                        if (!::personalDictionary.isInitialized) words
                        else if (amharic) {
                            val personal = personalDictionary.ranked(limit = request.limit).filter { w -> w.any { it in 'ሀ'..'፿' } }
                            (words + personal).distinct().take(request.limit)
                        } else {
                            val personal = personalDictionary.ranked(limit = request.limit).filter { '@' !in it }
                            (words + personal).distinct().take(request.limit)
                        }
                    }
                    if (merged.isNotEmpty()) {
                        activePredictionRequest = PredictionRequest(request, contextToken, capturedContext.contentIdentity)
                        publishSuggestions(merged, arePredictions = true)
                        return@safeApply
                    }
                }
                // Same anti-flicker rule as the completion path below, at the
                // word boundary: keep the just-committed word's completions on
                // screen while the next-word predictions compute, so the row
                // never flashes the toolbar icons between words. The carried
                // chips stay scoped to their pre-commit generation, and the
                // caret has since moved, so a stale tap is rejected by the
                // token revalidation in onSuggestionTapped.
                val carried = nonVoiceSuggestionUiState as? SuggestionUiState.WordCompletions
                    ?: nonVoiceSuggestionUiState as? SuggestionUiState.NextWordPredictions
                publishSuggestionState(carried ?: SuggestionUiState.LoadingPredictions)
                schedulePredictionComputation(
                    amharic = amharic,
                    capturedContext = capturedContext,
                    limit = request.limit
                )
            } else {
                pendingPredictionBoundary = null
                invalidatePredictionWork()
                // Selection-change echoes re-enter updateSuggestions with the
                // same buffer after every keystroke; without this guard each
                // echo cancelled the in-flight lookup and restarted it,
                // doubling the time to chips for no reason.
                val completionKey = CompletionRequestKey(typed, amharic)
                if (completionKey == activeCompletionKey) return@safeApply
                // Don't drop to the toolbar while the new completions compute.
                // Publishing Toolbar here made the strip flash its icons between
                // every keystroke -- chips, icons, chips -- because the lookup is
                // async. Instead carry the previous chips over (completions, or
                // the predictions that were showing when the first letter of
                // this word landed; both stay tappable-safe via their scoped
                // generation), and when there are none to carry show the blank
                // three-slot strip rather than the toolbar, so the row never
                // changes shape under the user.
                val carried = nonVoiceSuggestionUiState as? SuggestionUiState.WordCompletions
                    ?: nonVoiceSuggestionUiState as? SuggestionUiState.NextWordPredictions
                publishSuggestionState(carried ?: SuggestionUiState.LoadingCompletions)
                scheduleSuggestionComputation(
                    raw = typed,
                    amharic = amharic,
                    context = context,
                    observedCaretWord = caret?.word
                )
            }
        }
    }

    private data class CapturedNgramContext(
        val context: NgramContext.Context,
        val editorToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private data class PredictionBoundary(
        val context: NgramContext.Context,
        val amharic: Boolean,
        val sourceToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private data class PredictionRequestKey(
        val amharic: Boolean,
        val prev2: String?,
        val prev1: String?,
        val limit: Int
    )

    /**
     * Identity of the in-flight (or last-scheduled) completion lookup. The
     * n-gram context is deliberately not part of the key: it is fixed for the
     * lifetime of a word (cached in [composingNgramBoost]), so the raw buffer
     * fully determines what a re-schedule would compute.
     */
    private data class CompletionRequestKey(val raw: String, val amharic: Boolean)

    private var activeCompletionKey: CompletionRequestKey? = null

    private data class PredictionRequest(
        val key: PredictionRequestKey,
        val editorToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private var pendingPredictionBoundary: PredictionBoundary? = null
    private var activePredictionRequest: PredictionRequest? = null
    private val predictionCache =
        PredictionCache<List<SQLiteNgramModel.Prediction>>(PREDICTION_CACHE_SIZE)
    private val suggestionMainHandler = Handler(Looper.getMainLooper())
    private val idleReleaseHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable {
        if (!isLowRam) return@Runnable
        languageLoadGeneration += 1
        invalidateSuggestionWork()
        publishSuggestionState(SuggestionUiState.Toolbar)
        predictionCache.clear()
        pendingPredictionBoundary = null
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
    private val predictionExecutorDelegate = lazy(LazyThreadSafetyMode.NONE) {
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable -> Thread(runnable, "AddiyonPredictions") },
        )
    }
    private val predictionExecutor by predictionExecutorDelegate
    private var suggestionGeneration = 0L
    private var predictionGeneration = 0L
    private var predictionBoundaryMutationDepth = 0

    private fun currentBoundaryContext(amharic: Boolean): CapturedNgramContext? {
        val boundary = pendingPredictionBoundary ?: return null
        if (boundary.amharic != amharic || !editorGateway.isCurrent(boundary.sourceToken)) {
            pendingPredictionBoundary = null
            return null
        }
        return CapturedNgramContext(
            context = boundary.context,
            editorToken = boundary.sourceToken,
            contentIdentity = boundary.contentIdentity
        )
    }

    private inline fun <T> duringPredictionBoundaryMutation(block: () -> T): T {
        predictionBoundaryMutationDepth += 1
        return try {
            block()
        } finally {
            predictionBoundaryMutationDepth -= 1
        }
    }

    private fun predictionBoundaryAfterAcceptedReplacement(
        snapshot: EditorReplacementSnapshot?,
        replacement: String,
        context: NgramContext.Context,
        allowedIntermediateSelectionStart: Int? = null,
        allowedIntermediateSelectionEnd: Int? = null
    ): PredictionBoundary? {
        if (
            snapshot == null ||
            context.prev1 == null ||
            isPrivateField ||
            isEmailField ||
            isNumberMode
        ) {
            return null
        }
        val identity = snapshot.identityAfter(
            replacement = replacement,
            beforeChars = PREDICTION_IDENTITY_BEFORE,
            afterChars = PREDICTION_IDENTITY_AFTER
        ) ?: return null
        val postToken = editorGateway.transitionAfterAcceptedReplacement(
            sourceToken = snapshot.token,
            expectedSelection = identity.selectionStart,
            allowedIntermediateSelectionStart = allowedIntermediateSelectionStart,
            allowedIntermediateSelectionEnd = allowedIntermediateSelectionEnd
        ) ?: return null
        if (
            postToken.selectionStart != identity.selectionStart ||
            postToken.selectionEnd != identity.selectionEnd
        ) {
            return null
        }
        return PredictionBoundary(
            context = context,
            amharic = isAmharic,
            sourceToken = postToken,
            contentIdentity = identity
        )
    }

    private fun predictionContextAfterAcceptedWord(
        priorContext: NgramContext.Context,
        word: String
    ): NgramContext.Context =
        NgramContext.Context(
            prev2 = priorContext.prev1,
            prev1 = word
        )

    private fun predictionBoundaryEchoMatches(
        boundary: PredictionBoundary,
        selectionStart: Int,
        selectionEnd: Int
    ): Boolean =
        editorGateway.isCurrent(boundary.sourceToken) &&
            boundary.sourceToken.selectionStart == selectionStart &&
            boundary.sourceToken.selectionEnd == selectionEnd &&
            boundary.contentIdentity.selectionStart == selectionStart &&
            boundary.contentIdentity.selectionEnd == selectionEnd

    private fun predictionIdentityFrom(
        surrounding: EditorSurroundingText
    ): EditorContentIdentity? {
        if (surrounding.selectionStart != surrounding.selectionEnd) return null
        return EditorContentIdentity(
            selectionStart = surrounding.absoluteSelectionStart,
            selectionEnd = surrounding.absoluteSelectionEnd,
            textBeforeSelection = surrounding.textBeforeSelection
                .takeLast(PREDICTION_IDENTITY_BEFORE),
            textAfterSelection = surrounding.textAfterSelection
                .take(PREDICTION_IDENTITY_AFTER)
        )
    }

    private fun predictionReplacementSnapshot(
        replacementStart: Int,
        replacementEnd: Int,
        token: EditorToken
    ): EditorReplacementSnapshot? =
        editorGateway.replacementSnapshot(
            replacementStart = replacementStart,
            replacementEnd = replacementEnd,
            beforeChars = PREDICTION_IDENTITY_BEFORE,
            afterChars = PREDICTION_IDENTITY_AFTER
        )?.takeIf { it.token.sameEditorState(token) }

    private fun invalidateCompletionWork() {
        suggestionGeneration += 1
        activeCompletionKey = null
        suggestionExecutor.queue.clear()
    }

    private fun invalidatePredictionWork() {
        predictionGeneration += 1
        activePredictionRequest = null
        if (predictionExecutorDelegate.isInitialized()) {
            predictionExecutor.queue.clear()
        }
    }

    private fun invalidateSuggestionWork() {
        invalidateCompletionWork()
        invalidatePredictionWork()
    }

    private fun enterEmergencyMode() {
        suggestionMainHandler.post {
            if (isEmergencyMode) return@post
            isEmergencyMode = true
            pendingPredictionBoundary = null
            invalidateSuggestionWork()
            publishSuggestionState(SuggestionUiState.Toolbar)
            showEmojiPanel = false
            emojiSearchField = null
            clearComposingContextCache()
            predictionCache.clear()
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
        observedCaretWord: String? = null
    ) {
        activeCompletionKey = CompletionRequestKey(raw, amharic)
        val generation = ++suggestionGeneration
        val contextGeneration = composingContextGeneration
        val lowRam = isLowRam
        val ngramModel = ngramModelFor(amharic)
        suggestionExecutor.queue.clear()
        suggestionExecutor.execute {
            // Deliberately NOT lowered to THREAD_PRIORITY_BACKGROUND. That moves a
            // thread into Android's background cgroup, which is capped at a small
            // share of CPU whenever anything foreground is running -- and something
            // foreground always is, because the user is typing. It was the largest
            // single multiplier on suggestion latency: the work is short, it is on
            // the critical path of every keystroke, and the user is waiting for it.
            // The one-time 37 MB dictionary install still yields; see
            // SQLiteLanguageStore, where a background thread is genuinely right.
            val predictionLimit = if (amharic) {
                if (lowRam) 4 else AMHARIC_SUGGESTION_LIMIT
            } else {
                if (lowRam) 4 else ENGLISH_NGRAM_CONTEXT_LIMIT
            }
            val cachedBoost = composingNgramBoost
            val cachedCasing = composingPredictionCasing
            val pair = if (cachedBoost != null) {
                cachedBoost to cachedCasing
            } else {
                val predictions = try {
                    ngramModel?.let { predictionsFor(it, context, predictionLimit) }.orEmpty()
                } catch (_: RuntimeException) {
                    emptyList()
                }
                val ngramNext = predictions.associate {
                    (
                        if (amharic) {
                            EthiopicNormalizer.normalize(it.word)
                        } else {
                            englishFold(it.word)
                        }
                        ) to it.weight
                }
                val predictionCasing = if (amharic) {
                    emptyMap()
                } else {
                    predictions
                        .filter { it.word != it.word.lowercase() }
                        .associate { englishFold(it.word) to it.word }
                }
                if (contextGeneration == composingContextGeneration) {
                    composingNgramBoost = ngramNext
                    composingPredictionCasing = predictionCasing
                }
                ngramNext to predictionCasing
            }
            val computed = try {
                if (amharic) {
                    amharicSuggestions(raw, pair.first, lowRam)
                } else {
                    englishSuggestions(raw, pair.first, pair.second, lowRam)
                }
            } catch (_: RuntimeException) {
                emptyList()
            }
            suggestionMainHandler.post {
                if (generation != suggestionGeneration) return@post
                if (
                    isAmharic != amharic ||
                    isEmailField ||
                    isPrivateField ||
                    isNumberMode
                ) {
                    return@post
                }
                if (observedCaretWord != null) {
                    // The strip was answering the committed word at the caret.
                    // Re-read it rather than comparing tokens: an app tickling
                    // its own spans (spell check, rich text) emits selection
                    // callbacks that bump the selection generation without
                    // moving the caret, and token equality would drop the
                    // result those callbacks didn't actually invalidate.
                    if (currentCaretWord()?.word != observedCaretWord) return@post
                } else {
                    if (typingController.buffer != raw) return@post
                }
                if (computed.isEmpty() && raw.isNotEmpty()) {
                    // The user is in the middle of typing a word. Don't flash the
                    // toolbar icons just because this particular prefix has no
                    // dictionary matches yet; updateSuggestions() already left
                    // the previous completions or a blank LoadingCompletions strip
                    // in place, which is visually stable.
                    return@post
                }
                publishSuggestions(computed)
            }
        }
    }

    private fun schedulePredictionComputation(
        amharic: Boolean,
        capturedContext: CapturedNgramContext,
        limit: Int,
    ) {
        val context = capturedContext.context
        val request = PredictionRequestKey(amharic, context.prev2, context.prev1, limit)
        val ticket = PredictionRequest(
            key = request,
            editorToken = capturedContext.editorToken,
            contentIdentity = capturedContext.contentIdentity
        )
        val activeRequest = activePredictionRequest
        if (
            activeRequest?.key == request &&
            activeRequest.editorToken.sameEditorState(ticket.editorToken) &&
            activeRequest.contentIdentity == ticket.contentIdentity
        ) {
            return
        }
        invalidateCompletionWork()
        val generation = ++predictionGeneration
        activePredictionRequest = ticket
        val ngramModel = ngramModelFor(amharic)
        val executor = predictionExecutor
        executor.queue.clear()
        val cookie = generation.toInt()
        SuggestionTrace.beginAsync("prediction_queue", cookie)
        SuggestionTrace.beginAsync("prediction_request", cookie)
        try {
            executor.execute {
                SuggestionTrace.endAsync("prediction_queue", cookie)
                val predictions = try {
                    val model = ngramModel
                    val ngramPredictions = model?.let { predictionsFor(it, context, limit) }
                        .orEmpty()
                    if (ngramPredictions.isEmpty()) {
                        // No trigram or bigram successor for this context: fall
                        // back to the most frequent dictionary words so the strip
                        // still offers next-word candidates instead of going blank.
                        model?.topFrequentWords(
                            if (amharic) {
                                AMHARIC_SUGGESTION_LIMIT
                            } else {
                                PREDICTION_FALLBACK_ENGLISH_LIMIT
                            }
                        ).orEmpty()
                    } else {
                        ngramPredictions
                    }.map { it.word }
                    .let { words ->
                        if (!::personalDictionary.isInitialized) words
                        else if (amharic) {
                            val personal = personalDictionary.ranked(limit = limit).filter { w -> w.any { it in 'ሀ'..'፿' } }
                            (words + personal).distinct().take(limit)
                        } else {
                            val personal = personalDictionary.ranked(limit = limit).filter { '@' !in it }
                            (words + personal).distinct().take(limit)
                        }
                    }
                } catch (_: RuntimeException) {
                    emptyList()
                }
                suggestionMainHandler.post {
                    var refreshAfterRejectedIdentity = false
                    try {
                        if (generation != predictionGeneration) return@post
                        if (
                            isAmharic != amharic ||
                            isEmailField ||
                            isPrivateField ||
                            isNumberMode ||
                            typingController.isComposing ||
                            activePredictionRequest !== ticket
                        ) {
                            return@post
                        }
                        if (
                            !editorGateway.contentIdentityMatches(
                                ticket.contentIdentity,
                                ticket.editorToken
                            )
                        ) {
                            activePredictionRequest = null
                            pendingPredictionBoundary = pendingPredictionBoundary
                                ?.takeUnless {
                                    it.sourceToken.sameEditorState(ticket.editorToken)
                                }
                            refreshAfterRejectedIdentity = true
                            return@post
                        }
                        SuggestionTrace.section("prediction_publication") {
                            publishSuggestions(predictions, arePredictions = true)
                        }
                    } finally {
                        SuggestionTrace.endAsync("prediction_request", cookie)
                        if (refreshAfterRejectedIdentity) {
                            updateSuggestions()
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            SuggestionTrace.endAsync("prediction_queue", cookie)
            SuggestionTrace.endAsync("prediction_request", cookie)
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
    @Volatile
    private var composingContextGeneration = 0L

    @Volatile
    private var composingNgramBoost: Map<String, Int>? = null

    /**
     * Per-word map from a predicted next word's folded key to its context
     * proper-noun casing (e.g. "york" -> "York"), so an English completion of a
     * proper noun is shown capitalized to match the prediction after the same
     * context. Empty when the context predicts nothing capitalized. Cached and
     * cleared alongside [composingNgramBoost] (English-only; Amharic leaves it
     * empty).
     */
    @Volatile
    private var composingPredictionCasing: Map<String, String> = emptyMap()

    private data class ComposingContextKey(
        val sessionGeneration: Long,
        val amharic: Boolean
    )

    private val composingContextCache =
        PerWordCache<ComposingContextKey, CapturedNgramContext?>()

    private fun clearComposingContextCache() {
        composingContextGeneration += 1
        composingContextCache.clear()
        composingNgramBoost = null
        composingPredictionCasing = emptyMap()
    }

    private fun composingContextForWord(
        amharic: Boolean,
        contextReader: NgramContext
    ): CapturedNgramContext? {
        val key = ComposingContextKey(
            sessionGeneration = editorGateway.sessionGeneration,
            amharic = amharic
        )
        return composingContextCache.getOrCapture(key) {
            captureNgramContext(contextReader)
        }
    }
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
     * composing, the raw composing region before the cursor would read as a
     * hard boundary, so that prefix is stripped from the tail first -- and the
     * caret is always at the composing end, so the whole buffer is the prefix.
     */
    private fun captureNgramContext(
        contextReader: NgramContext,
    ): CapturedNgramContext? {
        return safeRun(null) {
            val composingPrefix = if (typingController.isComposing) {
                typingController.buffer
            } else {
                currentCaretWord()?.word.orEmpty()
            }
            val read = editorGateway
                .surroundingText(
                    beforeChars = maxOf(
                        PREDICTION_IDENTITY_BEFORE,
                        NgramContext.WINDOW + composingPrefix.length
                    ),
                    afterChars = PREDICTION_IDENTITY_AFTER,
                    optional = true
                )
                ?: return@safeRun captureNgramContextFromCursor(contextReader, composingPrefix)
            val surrounding = read.value
            if (surrounding.selectionStart != surrounding.selectionEnd) {
                return@safeRun null
            }
            val before = surrounding.textBeforeSelection
            val field = if (composingPrefix.isNotEmpty()) {
                if (!before.endsWith(composingPrefix)) return@safeRun null
                before.subSequence(0, before.length - composingPrefix.length)
            } else {
                before
            }
            CapturedNgramContext(
                context = contextReader.extract(field),
                editorToken = read.token,
                contentIdentity = predictionIdentityFrom(surrounding)
                    ?: return@safeRun null
            )
        }
    }

    private fun captureNgramContextFromCursor(
        contextReader: NgramContext,
        composingPrefix: String
    ): CapturedNgramContext? {
        val beforeRead = editorGateway.textBeforeCursor(
            maxOf(PREDICTION_IDENTITY_BEFORE, NgramContext.WINDOW + composingPrefix.length),
            optional = false
        ) ?: return null
        val afterRead = editorGateway.textAfterCursor(PREDICTION_IDENTITY_AFTER, optional = false)
            ?.takeIf { it.token.sameEditorState(beforeRead.token) }
            ?: return null
        val before = beforeRead.value
        val field = if (composingPrefix.isNotEmpty()) {
            if (!before.endsWith(composingPrefix)) return null
            before.removeSuffix(composingPrefix)
        } else before
        val selection = beforeRead.token.selectionStart
        if (selection < 0) return null
        return CapturedNgramContext(
            context = contextReader.extract(field),
            editorToken = beforeRead.token,
            contentIdentity = EditorContentIdentity(
                selectionStart = selection,
                selectionEnd = selection,
                textBeforeSelection = before.takeLast(PREDICTION_IDENTITY_BEFORE),
                textAfterSelection = afterRead.value.take(PREDICTION_IDENTITY_AFTER)
            )
        )
    }

    private fun predictionsFor(
        ngrams: SQLiteNgramModel,
        context: NgramContext.Context,
        limit: Int,
    ): List<SQLiteNgramModel.Prediction> {
        val prev1 = context.prev1 ?: return emptyList()
        val language = if (ngrams === amharicNgrams) {
            PredictionLanguage.AMHARIC
        } else {
            PredictionLanguage.ENGLISH
        }
        predictionCache.get(language, context.prev2, prev1, limit)?.let { return it }
        if (!ngrams.isReady) return emptyList()
        val predictions = safeRun(emptyList()) {
            SuggestionTrace.section("ngram_query") {
                ngrams.predict(context.prev2, prev1, limit)
            }
        }
        predictionCache.put(language, context.prev2, prev1, limit, predictions)
        return predictions
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
            publishSuggestionState(
                when {
                    // An empty result mid-sentence must not drop to the toolbar.
                    // Toolbar and the chip strip are different layouts, so the swap
                    // is a full relayout the user sees as a flash of icons between
                    // words. The completion path already held the previous strip
                    // for this reason; predictions did not, which is why the
                    // flicker survived at every word boundary. Hold the blank strip
                    // instead -- same shape, no icons.
                    value.isEmpty() && arePredictions ->
                        SuggestionUiState.LoadingPredictions
                    value.isEmpty() -> SuggestionUiState.Toolbar
                    arePredictions -> SuggestionUiState.NextWordPredictions(value.toList())
                    else -> SuggestionUiState.WordCompletions(value.toList())
                }
            )
        }
    }

    private fun publishEmailSuggestions(value: List<EmailChip>) {
        safeApply {
            publishSuggestionState(
                if (value.isEmpty()) {
                    SuggestionUiState.Toolbar
                } else {
                    SuggestionUiState.EmailSuggestions(value.toList())
                }
            )
        }
    }

    /**
     * Do these two states put exactly the same tappable chips on screen? Only the
     * chip-bearing states can match: a toolbar or loading strip carries no action
     * to keep scoped, so re-publishing one is free anyway.
     */
    private fun sameVisibleChips(current: SuggestionUiState, next: SuggestionUiState): Boolean =
        when {
            current is SuggestionUiState.WordCompletions &&
                next is SuggestionUiState.WordCompletions -> current.words == next.words
            current is SuggestionUiState.NextWordPredictions &&
                next is SuggestionUiState.NextWordPredictions -> current.words == next.words
            current is SuggestionUiState.EmailSuggestions &&
                next is SuggestionUiState.EmailSuggestions -> current.chips == next.chips
            else -> false
        }

    var expandedSuggestionsVisible by mutableStateOf(false)
        private set

    fun toggleExpandedSuggestions() {
        safeApply {
            if (voiceUiState.isVoiceMode) return@safeApply
            val hasRemaining = when (val s = nonVoiceSuggestionUiState) {
                is SuggestionUiState.WordCompletions -> s.words.size > 3
                is SuggestionUiState.NextWordPredictions -> s.words.size > 3
                is SuggestionUiState.EmailSuggestions -> s.chips.size > 3
                else -> false
            }
            if (!hasRemaining) return@safeApply
            expandedSuggestionsVisible = !expandedSuggestionsVisible
        }
    }

    fun hideExpandedSuggestions() {
        expandedSuggestionsVisible = false
    }

    fun dismissSuggestions() {
        safeApply {
            if (voiceUiState.isVoiceMode) return@safeApply
            when (nonVoiceSuggestionUiState) {
                is SuggestionUiState.WordCompletions,
                is SuggestionUiState.NextWordPredictions,
                is SuggestionUiState.EmailSuggestions,
                SuggestionUiState.LoadingPredictions,
                SuggestionUiState.LoadingCompletions -> Unit
                else -> return@safeApply
            }
            expandedSuggestionsVisible = false
            invalidateSuggestionWork()
            publishSuggestionState(SuggestionUiState.Toolbar)
        }
    }

    private fun publishSuggestionState(state: SuggestionUiState) {
        val scopedState = scopeSuggestionState(state)
        val changed = scopedState != nonVoiceSuggestionUiState
        nonVoiceSuggestionUiState = scopedState
        if (changed) expandedSuggestionsVisible = false
        if (!voiceUiState.isVoiceMode) {
            suggestionUiState = scopedState
        }
    }

    private fun publishVoiceUiState(state: VoiceUiState) {
        voiceUiState = state
        if (state.isVoiceMode) {
            suggestionActionGeneration += 1
            publishedSuggestionAction = null
            suggestionUiState = SuggestionUiState.Voice(state)
        } else {
            publishSuggestionState(nonVoiceSuggestionUiState)
        }
    }

    private fun scopeSuggestionState(state: SuggestionUiState): SuggestionUiState {
        // Reuse the current generation when the visible chips are unchanged.
        //
        // actionGeneration participates in the state's equals(), so bumping it on
        // every publish made Compose recompose the whole strip on every keystroke
        // even when the words were byte-identical -- and each recomposition re-ran
        // TextAutoSize's iterative measurement across three slots. Carrying the
        // previous completions forward during an in-flight lookup (the anti-flicker
        // path) republishes constantly, so this was the common case, not the rare
        // one. A tap is still scoped correctly: an unchanged strip is the same
        // strip, so the same generation is the honest answer.
        if (sameVisibleChips(nonVoiceSuggestionUiState, state)) {
            return nonVoiceSuggestionUiState
        }
        val generation = ++suggestionActionGeneration
        val scoped = when (state) {
            is SuggestionUiState.WordCompletions ->
                state.copy(actionGeneration = generation)
            is SuggestionUiState.NextWordPredictions ->
                state.copy(actionGeneration = generation)
            is SuggestionUiState.EmailSuggestions ->
                state.copy(actionGeneration = generation)
            else -> state
        }
        val token = editorGateway.currentToken()
        publishedSuggestionAction = if (
            token != null &&
            (
                scoped is SuggestionUiState.WordCompletions ||
                    scoped is SuggestionUiState.NextWordPredictions ||
                    scoped is SuggestionUiState.EmailSuggestions
                )
        ) {
            PublishedSuggestionAction(
                generation = generation,
                editorToken = token,
                caretWord = currentCaretWord()?.word,
                predictionIdentity = if (
                    scoped is SuggestionUiState.NextWordPredictions
                ) {
                    activePredictionRequest?.contentIdentity
                } else {
                    null
                },
                amharic = isAmharic,
                emailField = isEmailField,
                privateField = isPrivateField,
                numberMode = isNumberMode
            )
        } else {
            null
        }
        return scoped
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
                val cased = casing[englishFold(word)] ?: word
                if (cased !in merged && merged.size < ENGLISH_SUGGESTION_LIMIT) merged.add(cased)
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
            if (::personalDictionary.isInitialized && merged.size < ENGLISH_SUGGESTION_LIMIT) {
                val personalRaw = personalDictionary.completions(typed, ENGLISH_COMPLETION_POOL)
                val emailFirst = personalRaw.filter { '@' in it }
                val nonEmail = personalRaw.filter { '@' !in it }
                for (word in emailFirst) {
                    if (merged.size >= ENGLISH_SUGGESTION_LIMIT) break
                    val cased = matchCase(typed, word)
                    if (cased !in merged) merged.add(cased)
                }
                for (word in nonEmail) {
                    if (merged.size >= ENGLISH_SUGGESTION_LIMIT) break
                    val cased = matchCase(typed, word)
                    if (cased !in merged) merged.add(cased)
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
            val quirkReadings = candidateReadings.filter { it.isQuirk }.map { it.text }.toSet()
            val personalAmharic = if (!::personalDictionary.isInitialized) emptyList() else {
                val seen = HashSet<String>()
                val out = ArrayList<String>(AMHARIC_SUGGESTION_LIMIT)
                for (reading in readings.distinct()) {
                    for (w in personalDictionary.completions(reading, AMHARIC_SUGGESTION_LIMIT)) {
                        if (w !in seen) {
                            seen.add(w)
                            out.add(w)
                            if (out.size >= AMHARIC_SUGGESTION_LIMIT) break
                        }
                    }
                    if (out.size >= AMHARIC_SUGGESTION_LIMIT) break
                }
                out
            }
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
            val rankedWithPersonal = if (personalAmharic.isEmpty()) ranked else (ranked + personalAmharic).distinct().take(AMHARIC_SUGGESTION_LIMIT)
            if (rankedWithPersonal.size >= AMHARIC_SUGGESTION_LIMIT ||
                readings.none { it.length <= MAX_FUZZY_READING_LENGTH }
            ) {
                return@safeRun pinPreferredAlternate(rankedWithPersonal, preferredAlternate).also {
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
                return@safeRun pinPreferredAlternate(rankedWithPersonal, preferredAlternate).also {
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

            val rankedFuzzy = CandidateRanker.rankAmharic(
                    readings = readings,
                    limit = AMHARIC_SUGGESTION_LIMIT,
                    frequencyOf = readingFrequencies::get,
                    completionsForPrefix = completionsForPrefix,
                    visibleReadings = visibleReadings,
                    fuzzyWords = fuzzy,
                    quirkReadings = quirkReadings,
                    ngramNext = ngramNext,
                    preferGreedy = Transliterator.hasExplicitFamilySelection(latin)
                )
            val rankedFuzzyWithPersonal = if (personalAmharic.isEmpty()) rankedFuzzy else (rankedFuzzy + personalAmharic).distinct().take(AMHARIC_SUGGESTION_LIMIT)
            pinPreferredAlternate(
                rankedFuzzyWithPersonal,
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
     * the saved preference, then refreshes navigation icon appearance. Light/
     * dark follows the system; only the color palette is user-selectable, and
     * it themes just the keyboard.
     */
    private fun refreshTheme(configuration: Configuration) {
        safeApply {
            palette = KeyboardPrefs.palette(this)
            val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            updateSystemNavigationAppearance()
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

    private fun updateSystemNavigationAppearance() {
        safeApply {
            window?.window?.let { imeWindow ->
                WindowInsetsControllerCompat(imeWindow, imeWindow.decorView)
                    .isAppearanceLightNavigationBars =
                    palette.usesDarkNavigationIcons(isDarkTheme)
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

    fun onAiAction() {
        safeApply {
            if (aiUiState.isVisible) {
                dismissAiPanel()
                return@safeApply
            }
            if (!::aiController.isInitialized) return@safeApply
            if (showEmojiPanel) showEmojiPanel = false
            if (voiceUiState.isVoiceMode) {
                voiceInputController?.stop()
                finalizeVoiceComposing()
                resetVoiceUi()
            }
            val jwt = KeyboardPrefs.aiJwt(this)
            val needsAuth = jwt.isNullOrBlank()
            if (needsAuth) {
                val intent = Intent(this, AiAccountActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(AiAccountActivity.EXTRA_MODE, AiAccountActivity.MODE_AUTH)
                }
                ExternalActions.start(this, intent, "Unable to open AI sign-in.")
                return@safeApply
            }
            val quota = currentAiQuota()
            val captured = if (isPrivateField) null else aiController.captureInput()
            aiUiState = AiUiState(
                isVisible = true,
                selectedTab = aiUiState.selectedTab,
                strength = aiUiState.strength,
                input = captured,
                result = null,
                alternatives = emptyList(),
                isLoading = false,
                error = if (isPrivateField) AiError.PrivateField else null,
                quota = quota,
                isPrivateField = isPrivateField,
                needsAuth = false,
                authEmail = KeyboardPrefs.aiEmail(this) ?: aiUiState.authEmail
            )
        }
    }

    fun openAiDashboard() {
        safeApply {
            val intent = Intent(this, AiAccountActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AiAccountActivity.EXTRA_MODE, AiAccountActivity.MODE_DASHBOARD)
            }
            ExternalActions.start(this, intent, "Unable to open AI dashboard.")
        }
    }

    fun dismissAiPanel() {
        safeApply { aiUiState = aiUiState.copy(isVisible = false, isLoading = false) }
    }

    fun onAiTabSelected(tab: AiToneTab) {
        safeApply {
            aiUiState = aiUiState.copy(selectedTab = tab, result = null, error = null)
            val input = aiUiState.input
            if (input == null || input.text.isBlank()) {
                aiUiState = aiUiState.copy(error = AiError.NoText)
                return@safeApply
            }
            if (isPrivateField) {
                aiUiState = aiUiState.copy(error = AiError.PrivateField)
                return@safeApply
            }
            if (aiUiState.needsAuth) {
                aiUiState = aiUiState.copy(error = AiError.NeedsAuth)
                return@safeApply
            }
            if (aiUiState.quota.remaining <= 0 || input.wordCount > aiUiState.quota.remaining) {
                aiUiState = aiUiState.copy(error = AiError.QuotaExceeded(aiUiState.quota.remaining))
                return@safeApply
            }
            aiUiState = aiUiState.copy(isLoading = true, error = null)
            aiScope.launch {
                val res = withContext(Dispatchers.IO) { aiController.revamp(input, tab, aiUiState.strength) }
                safeApply {
                    res.onSuccess { result ->
                        consumeAiQuota(input.wordCount)
                        aiUiState = aiUiState.copy(result = result, isLoading = false)
                    }.onFailure { t ->
                        val err = aiController.parseError(t)
                        if (err is AiError.QuotaExceeded) aiUiState = aiUiState.copy(quota = currentAiQuota())
                        aiUiState = aiUiState.copy(error = err, isLoading = false)
                    }
                }
            }
        }
    }

    fun onAiStrengthSelected(strength: AiStrength) {
        safeApply { aiUiState = aiUiState.copy(strength = strength) }
    }

    fun onAiCopy() {
        safeApply {
            val text = aiUiState.result?.text ?: return@safeApply
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AI result", text))
            try { Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        }
    }

    fun onAiReplace() {
        safeApply {
            val result = aiUiState.result ?: return@safeApply
            val input = aiUiState.input ?: return@safeApply
            val snapshot = input.snapshot
            val replacement = result.text
            if (snapshot != null) {
                val token = editorGateway.currentToken()
                if (token == null || token.generation != snapshot.tokenGeneration) {
                    aiUiState = aiUiState.copy(error = AiError.Server("Text changed — reopen AI"))
                    return@safeApply
                }
                val selStart = minOf(snapshot.replacementStart, snapshot.replacementEnd)
                val selEnd = maxOf(snapshot.replacementStart, snapshot.replacementEnd)
                val currentToken = editorGateway.currentToken()
                if (currentToken != null && (currentToken.selectionStart != selStart || currentToken.selectionEnd != selEnd)) {
                    if (input.source == com.addiyon.keyboard.ai.AiSource.Selection) {
                        aiUiState = aiUiState.copy(error = AiError.Server("Selection changed — reopen AI"))
                        return@safeApply
                    }
                }
                val snapshotObj = editorGateway.replacementSnapshot(selStart, selEnd, 128, 128)
                val ok = if (snapshotObj != null) {
                    editorGateway.write(snapshotObj.token) { conn ->
                        try {
                            conn.setComposingRegion(selStart, selEnd)
                            conn.commitText(replacement, 1)
                            true
                        } catch (_: Throwable) { conn.commitText(replacement, 1) }
                    }
                } else {
                    editorGateway.commitText(replacement)
                }
                if (ok) {
                    aiUiState = aiUiState.copy(isVisible = false)
                    typingController.onSelectionChanged(selStart + replacement.length, selStart + replacement.length, -1, -1)
                    updateSuggestions()
                } else {
                    aiUiState = aiUiState.copy(error = AiError.Server("Replace failed"))
                }
            } else {
                val ok = editorGateway.commitText(replacement)
                if (ok) {
                    aiUiState = aiUiState.copy(isVisible = false)
                    updateSuggestions()
                } else {
                    aiUiState = aiUiState.copy(error = AiError.Server("Replace failed"))
                }
            }
        }
    }

    fun onAiAuthEmailChanged(email: String) {
        safeApply { aiUiState = aiUiState.copy(authEmail = email) }
    }

    fun onAiSendLink() {
        safeApply {
            val email = aiUiState.authEmail.trim()
            if (!email.contains("@")) {
                aiUiState = aiUiState.copy(authMessage = "Enter a valid email")
                return@safeApply
            }
            aiUiState = aiUiState.copy(authSending = true, authMessage = null)
            aiScope.launch {
                val res = withContext(Dispatchers.IO) {
                    aiRepository.issueMagicLink(email, "addiyon://auth/callback")
                }
                safeApply {
                    res.onSuccess { r ->
                        KeyboardPrefs.setAiEmail(this@AddiyonKeyboardService, email)
                        val msg = if (r.devLink != null) "Link sent (dev): ${r.devLink}" else "Check your email for the link"
                        aiUiState = aiUiState.copy(authSending = false, authMessage = msg)
                    }.onFailure { t ->
                        val err = aiController.parseError(t)
                        aiUiState = aiUiState.copy(authSending = false, authMessage = err.toString())
                    }
                }
            }
        }
    }

    fun onAiAuthTokenReceived(token: String) {
        safeApply {
            aiUiState = aiUiState.copy(authSending = true)
            aiScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val api = aiRepository
                        val anonId = KeyboardPrefs.aiAnonId(this@AddiyonKeyboardService)
                        val quotaRes = api.quota(KeyboardPrefs.aiJwt(this@AddiyonKeyboardService), anonId)
                        quotaRes.isSuccess
                    } catch (_: Throwable) { false }
                }
                safeApply {
                    KeyboardPrefs.setAiJwt(this@AddiyonKeyboardService, token)
                    val quota = currentAiQuota()
                    aiUiState = aiUiState.copy(needsAuth = false, authSending = false, quota = quota, error = null, authMessage = "Authenticated")
                }
            }
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
                publishVoiceUiState(VoiceUiState.Paused)
                Telemetry.voiceFinished(TelemetryVoiceResult.CANCELLED, isPrivateField)
                return@safeApply
            }

            startVoiceRecognition()
        }
    }

    /** Back arrow in the voice toolbar: leave voice mode entirely. */
    fun exitVoiceMode() {
        safeApply {
            val wasVoiceMode = voiceUiState.isVoiceMode
            voiceInputController?.stop()
            finalizeVoiceComposing()
            resetVoiceUi()
            if (wasVoiceMode) {
                Telemetry.voiceFinished(TelemetryVoiceResult.CANCELLED, isPrivateField)
            }
            updateSuggestions()
        }
    }

    private fun startVoiceRecognition() {
        safeApply {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                publishVoiceUiState(VoiceUiState.PermissionRequired)
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
                    publishVoiceUiState(
                        VoiceUiState.Unavailable(VoiceErrorKind.PERMISSION.userMessage)
                    )
                    Telemetry.voiceFinished(
                        TelemetryVoiceResult.ERROR,
                        TelemetryVoiceError.PERMISSION,
                        isPrivateField
                    )
                }
                return@safeApply
            }

            // Flush any half-typed word first: the composition and voice must
            // never both own a composing region in the field.
            typingController.commitActiveWord()
            voiceComposer.reset()
            // Set Listening BEFORE start(): an unavailable recognizer fails
            // synchronously through onVoiceFatalError, which must win.
            publishVoiceUiState(VoiceUiState.Listening)
            Telemetry.voiceStarted(telemetryLanguage(), isPrivateField)
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
            } else {
                Telemetry.voiceFinished(TelemetryVoiceResult.COMPLETED, isPrivateField)
            }
        }
    }

    private fun stopVoiceAfterEditorFailure() {
        voiceInputController?.stop()
        voiceComposer.reset()
        publishVoiceUiState(VoiceUiState.Unavailable(VoiceErrorKind.UNKNOWN.userMessage))
        Telemetry.voiceFinished(
            TelemetryVoiceResult.ERROR,
            TelemetryVoiceError.OTHER,
            isPrivateField
        )
    }

    private fun onVoiceFatalError(kind: VoiceErrorKind) {
        safeApply {
            finalizeVoiceComposing()
            publishVoiceUiState(
                if (kind == VoiceErrorKind.TOO_MANY_REQUESTS) {
                    VoiceUiState.Paused
                } else {
                    VoiceUiState.Unavailable(kind.userMessage)
                }
            )
            Telemetry.voiceFinished(
                TelemetryVoiceResult.ERROR,
                kind.telemetryCategory(),
                isPrivateField
            )
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
     * see [com.addiyon.keyboard.composing.Composition.finalizeInPlace] for the
     * same lesson). Safe no-op when no utterance is live.
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
            publishVoiceUiState(VoiceUiState.Idle)
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
            Telemetry.voiceFinished(TelemetryVoiceResult.CANCELLED, isPrivateField)
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
            typingController.commitActiveWord()
            updateSuggestions()
            emojiRepository.loadAsync()
            showEmojiPanel = true
            Telemetry.layoutOpened(TelemetryLayout.EMOJI, isPrivateField)
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
        setLanguage(!isAmharic)
    }

    /**
     * Switches the active language to [amharic], or does nothing if that
     * language is already active. Everything that flips the language funnels
     * through here -- the globe key via [toggleLanguage] and the system
     * language switcher via [onCurrentInputMethodSubtypeChanged] -- so both
     * paths get the same composer commit and dictionary swap.
     */
    fun setLanguage(amharic: Boolean) {
        if (amharic == isAmharic) return
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            // The half-typed word belongs to the outgoing language's pipeline:
            // commit it BEFORE isAmharic flips, so the profile (and its
            // commitTransform) is still the outgoing language's.
            typingController.onLanguageChange()
            // Flush per-word caches BEFORE flipping -- the cache key folds
            // per-language, and the ranked results would otherwise leak across
            // a toggle (English prefix "th" ranked with Amharic boosts, etc.).
            amharicSuggestionCache.clear()
            amharicCommitCandidateCache.clear()
            clearComposingContextCache()
            // Release the previously-active dictionary+ngram and load the
            // new one. On a low-RAM device this is the point where the OS
            // would have killed us under the old in-memory trie design; the
            // page-cached SQLite approach makes the swap cheap.
            val priorActive = if (isAmharic) amharicDictionary else englishDictionary
            isAmharic = amharic
            KeyboardPrefs.setAmharicMode(this, isAmharic)
            Telemetry.languageSwitched(
                destination = telemetryLanguage(),
                privateField = isPrivateField
            )
            if (!isAmharic && numbersMode == NumbersMode.GEEZ_NUMBERS) {
                numbersMode = NumbersMode.NUMBERS
            }
            val priorStore = if (amharic) englishStore else amharicStore
            priorStore.release()
            priorActive.clearCache()
            predictionCache.clear()
            pendingPredictionBoundary = null
            ensureActiveLanguageStoreLoaded("after_toggle_language")
            updateSuggestions()
            if (isAmharic && autoShiftArmed) {
                resetShift()
            } else if (!isAmharic) {
                maybeAutoCapitalize()
            }
            MemoryProbe.snapshot("after_toggle_language_sync")
        }
    }

    private fun ensureActiveLanguageStoreLoaded(snapshotPrefix: String) {
        if (!::amharicDictionary.isInitialized) return
        if (isEmergencyMode) return
        val dictionary = if (isAmharic) amharicDictionary else englishDictionary
        val ngrams = if (isAmharic) amharicNgrams else englishNgrams
        val store = if (isAmharic) amharicStore else englishStore
        if (dictionary.isReady && ngrams.isReady) {
            return
        }
        val targetAmharic = isAmharic
        val loadGeneration = ++languageLoadGeneration
        publishSuggestionState(SuggestionUiState.LoadingLanguage)
        store.loadAsync storeReady@{
            if (loadGeneration != languageLoadGeneration || targetAmharic != isAmharic) {
                return@storeReady
            }
            MemoryProbe.snapshot("${snapshotPrefix}_store")
            predictionCache.clear()
            invalidatePredictionWork()
            updateSuggestions()
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
            typingController.commitActiveWord()
            numbersMode = if (numbersMode == NumbersMode.OFF) NumbersMode.NUMBERS else NumbersMode.OFF
            Telemetry.layoutOpened(
                if (numbersMode == NumbersMode.OFF) {
                    TelemetryLayout.LETTERS
                } else {
                    TelemetryLayout.NUMBERS
                },
                isPrivateField
            )
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
            Telemetry.layoutOpened(
                when (numbersMode) {
                    NumbersMode.OFF -> TelemetryLayout.LETTERS
                    NumbersMode.NUMBERS -> TelemetryLayout.NUMBERS
                    NumbersMode.KEYPAD -> TelemetryLayout.KEYPAD
                    NumbersMode.GEEZ_NUMBERS,
                    NumbersMode.SYMBOLS,
                    NumbersMode.MORE_SYMBOLS -> TelemetryLayout.SYMBOLS
                },
                isPrivateField
            )
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
            typingController.commitActiveWord()
            numbersMode = NumbersMode.KEYPAD
            Telemetry.layoutOpened(TelemetryLayout.KEYPAD, isPrivateField)
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
            autoShiftArmed = false
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
            autoShiftArmed = false
        }
    }

    fun resetShift() {
        safeApply {
            shiftState = ShiftState.OFF
            autoShiftArmed = false
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
    private fun maybeAutoCapitalize(
        textBeforeCursor: CharSequence? = null,
        useKnownInitialCursor: Boolean = false
    ) {
        safeApply {
            if (isAmharic || isNumberMode || !fieldAllowsAutoCap) {
                if (autoShiftArmed) resetShift()
                return@safeApply
            }
            if (typingController.isComposing || shiftState == ShiftState.CAPS_LOCK) return@safeApply
            val before = textBeforeCursor
                ?: editorGateway.textBeforeCursor(SENTENCE_LOOKBEHIND)?.value
            val startsSentence = SentenceCase.startsNewSentence(
                before,
                cursorKnownAtFieldStart = useKnownInitialCursor && cursorKnownAtFieldStart
            )
            if (startsSentence && shiftState == ShiftState.OFF) {
                shiftState = ShiftState.SHIFT
                autoShiftArmed = true
            } else if (!startsSentence && autoShiftArmed) {
                resetShift()
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
            cursorKnownAtFieldStart = false
            val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

            if (isNumberMode) {
                editorGateway.commitText(output)
            } else {
                // Word composition, punctuation transliteration, committed-word
                // resume and email passthrough are all profile-driven now --
                // see typingProfile().
                typingController.onCharacter(output)
            }

            consumeShiftAfterCharacter()
            updateSuggestions()
        }
    }

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
            if (pendingRefresh || typingController.isComposing) updateSuggestions()
        }
    }

    /**
     * Backspace pressed. The controller shrinks the composing word first (one
     * Latin character at a time, so each typed letter clears individually);
     * with nothing composed it deletes from the field -- a range selection if
     * there is one, else one emoji-aware cluster -- and then re-opens whatever
     * word the caret landed at the end of, so the strip keeps answering the
     * word being edited.
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
            val cluster = if (typingController.isComposing) {
                1
            } else {
                val before = editorGateway.textBeforeCursor(
                    ResumableWord.LOOKBEHIND,
                    optional = false
                )?.value
                EmojiBackspace.lastClusterLength(before ?: "").coerceAtLeast(1)
            }
            typingController.onBackspace(cluster)
            updateSuggestions()
        }
    }

    fun commitText(text: String) {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            typingController.onCommitText(text)
        }
    }

    /**
     * Space commits any in-flight word first, then inserts a space.
     *
     * The composition's [com.addiyon.keyboard.composing.Composition.commit]
     * replaces the underlined raw Latin with its commitTransform: for Amharic
     * that's the top-ranked fidel reading (the same as suggestions[0],
     * highlighted in the strip) -- so space picks the default reading and a
     * tap is only needed for a NON-default one;
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
            typingController.commitActiveWord()
            val sourceToken = editorGateway.currentToken()
            val boundarySnapshot = SuggestionTrace.section("prediction_boundary_capture") {
                sourceToken?.let {
                    editorGateway.replacementSnapshot(
                        replacementStart = minOf(it.selectionStart, it.selectionEnd),
                        replacementEnd = maxOf(it.selectionStart, it.selectionEnd),
                        beforeChars = maxOf(
                            PREDICTION_IDENTITY_BEFORE,
                            SENTENCE_LOOKBEHIND
                        ),
                        afterChars = PREDICTION_IDENTITY_AFTER
                    )
                }
            }
            val beforeSpace = boundarySnapshot?.surrounding?.textBeforeSelection
            val boundaryContext = beforeSpace?.let { before ->
                val contextReader = if (isAmharic) {
                    NgramContext.AMHARIC
                } else {
                    NgramContext.ENGLISH
                }
                contextReader.extract("$before ")
            }
            pendingPredictionBoundary = null
            val inserted = duringPredictionBoundaryMutation {
                editorGateway.commitText(" ", boundarySnapshot?.token)
            }
            pendingPredictionBoundary = if (inserted && boundaryContext != null) {
                predictionBoundaryAfterAcceptedReplacement(
                    snapshot = boundarySnapshot,
                    replacement = " ",
                    context = boundaryContext
                )
            } else {
                null
            }
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
            typingController.commitActiveWord()
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
            val resolution = editorInfo?.let {
                EnterActionPolicy.resolve(it.inputType, it.imeOptions)
            } ?: EnterActionPolicy.default
            enterAction = resolution.action
            editorActionId = resolution.editorActionId
        }
    }

    /**
     * A suggestion chip was tapped: swap the current composing text for the
     * full suggested word and clear the strip.
     */
    fun onSuggestionTapped(word: String) {
        val generation = when (val state = suggestionUiState) {
            is SuggestionUiState.WordCompletions -> state.actionGeneration
            is SuggestionUiState.NextWordPredictions -> state.actionGeneration
            is SuggestionUiState.EmailSuggestions -> state.actionGeneration
            else -> return
        }
        onSuggestionTapped(SuggestionTap(word, generation))
    }

    fun onSuggestionTapped(tap: SuggestionTap) {
        safeApply {
            val state = validatedSuggestionTapState(tap) ?: return@safeApply
            val action = publishedSuggestionAction ?: return@safeApply
            val word = tap.word
            val prediction = state is SuggestionUiState.NextWordPredictions
            leaveVoiceModeForKeyboardInput()
            val acceptedKind = if (prediction) {
                TelemetrySuggestionKind.PREDICTION
            } else {
                TelemetrySuggestionKind.COMPLETION
            }
            val contextReader = if (isAmharic) NgramContext.AMHARIC else NgramContext.ENGLISH
            val priorContext = currentBoundaryContext(isAmharic)
                ?: captureNgramContext(contextReader)
            val priorContextValue = priorContext?.context ?: NgramContext.EMPTY
            val addTrailingSpace = !isEmailField && '@' !in word
            val replacement = if (addTrailingSpace) "$word " else word
            val nextContext = predictionContextAfterAcceptedWord(
                priorContext = priorContextValue,
                word = word
            )
            pendingPredictionBoundary = null

            // How much text before the caret the tapped chip replaces. A
            // completion replaces the word it was offered for -- the composing
            // buffer, or the committed word the caret sits at the end of (which
            // the controller re-opens and swaps atomically; see WordAdoption).
            // A prediction is a NEW word: it inserts at the caret and replaces
            // nothing. The length is used only for the post-commit prediction
            // boundary's before/after identity, never for an edit.
            val composing = typingController.isComposing
            val replacedLength = when {
                prediction -> 0
                composing -> typingController.buffer.length
                else -> {
                    // No composition and no committed word at the caret: a
                    // completion has nothing to replace -- no-op, exactly like
                    // the old null-snapshot bail, rather than inserting a
                    // duplicate next to the word it was meant to swap out.
                    val expected = action.caretWord ?: return@safeApply
                    if (currentCaretWord()?.word != expected) return@safeApply
                    expected.length
                }
            }
            val mutationSnapshot = editorGateway.currentToken()?.let { token ->
                predictionReplacementSnapshot(
                    replacementStart = minOf(token.selectionStart, token.selectionEnd) - replacedLength,
                    replacementEnd = maxOf(token.selectionStart, token.selectionEnd),
                    token = token
                )
            }
            val committed = duringPredictionBoundaryMutation {
                typingController.onSuggestionTap(
                    word,
                    if (prediction) SuggestionKind.PREDICTION else SuggestionKind.COMPLETION,
                    trailingSpace = addTrailingSpace
                )
            }
            pendingPredictionBoundary = if (committed) {
                predictionBoundaryAfterAcceptedReplacement(
                    snapshot = mutationSnapshot,
                    replacement = replacement,
                    context = nextContext
                )
            } else {
                null
            }
            if (committed) {
                Telemetry.suggestionAccepted(acceptedKind, isPrivateField)
            }
            updateSuggestions()
        }
    }

    /**
     * Decides whether a chip tap may act, and on which state.
     *
     * Every check here is deliberately strict -- a tap may only act on the exact
     * strip the user was looking at, in the same field, with the editor still
     * where that strip was published against.
     *
     * Note that the strictness relies on [EditorGateway.noteSelection] only
     * advancing its selection generation when the selection actually MOVES. A
     * redundant onUpdateSelection callback used to advance it anyway, which made
     * [PublishedSuggestionAction.editorToken] stale while nothing had changed and
     * silently rejected every chip tap from then on.
     */
    private fun validatedSuggestionTapState(tap: SuggestionTap): SuggestionUiState? {
        val action = publishedSuggestionAction ?: return null
        val state = suggestionUiState
        if (
            tap.actionGeneration != action.generation ||
            action.amharic != isAmharic ||
            action.emailField != isEmailField ||
            action.privateField != isPrivateField ||
            action.numberMode != isNumberMode ||
            isPrivateField ||
            voiceUiState.isVoiceMode
        ) {
            return null
        }
        // The stale-token check that lived here (`revalidateSelection`) compared
        // the current caret against the position captured at publish time, and
        // rejected every tap once the user typed even one more letter. That is
        // exactly the silent-no-op in rich-text editors (Samsung Notes, Gmail,
        // Docs, anything Compose or WebView backed): the strip carries the
        // previous keystroke's chips forward while the next lookup is in flight
        // -- the carry is visible, the generation still matches, but the
        // captured selection has moved on. Validate against what the strip was
        // ACTUALLY generated for instead: the live composing buffer (the
        // controller is the authority that the field holds exactly that buffer
        // with the caret at its end), or the committed word the caret sits at
        // the end of (compared via [currentCaretWord]).
        val editorStateValid = when (state) {
            is SuggestionUiState.NextWordPredictions -> {
                action.predictionIdentity?.let {
                    editorGateway.contentIdentityMatches(it, action.editorToken)
                } == true
            }
            else -> isCompletionChipTapValid(
                composing = typingController.isComposing,
                currentCaretWord = currentCaretWord()?.word,
                publishedCaretWord = action.caretWord
            )
        }
        if (!editorStateValid) {
            if (
                state is SuggestionUiState.NextWordPredictions &&
                state.actionGeneration == tap.actionGeneration
            ) {
                pendingPredictionBoundary = null
                invalidatePredictionWork()
                updateSuggestions()
            }
            return null
        }
        val validCandidate = when (state) {
            is SuggestionUiState.WordCompletions ->
                state.actionGeneration == tap.actionGeneration && tap.word in state.words
            is SuggestionUiState.NextWordPredictions ->
                state.actionGeneration == tap.actionGeneration && tap.word in state.words
            is SuggestionUiState.EmailSuggestions ->
                state.actionGeneration == tap.actionGeneration &&
                    state.chips.any { it.commit == tap.word }
            else -> false
        }
        return state.takeIf { validCandidate }
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
            updateSystemNavigationAppearance()

            inputView
        }
    }

    /**
     * The system language switcher (and Settings > Languages & input) selects
     * one of the two subtypes declared in `res/xml/method.xml`. Without this,
     * those surfaces would offer a language the keyboard then ignored, since
     * the active language lives in [isAmharic] rather than in the subtype.
     *
     * Routed through [setLanguage] rather than [toggleLanguage] so it is
     * idempotent: the platform also delivers this callback on the initial
     * binding and after IME switches, where the subtype usually already
     * matches. Toggling there would flip the user out of their own language
     * and -- because Amharic composition is discard-on-exit -- take an
     * uncommitted word with it.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        safeApply {
            super.onCurrentInputMethodSubtypeChanged(newSubtype)
            val amharic = SubtypeLanguagePolicy.selectsAmharic(
                languageTag = safeRun(null) { newSubtype?.languageTag },
                locale = safeRun(null) { @Suppress("DEPRECATION") newSubtype?.locale },
            ) ?: return@safeApply
            setLanguage(amharic)
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
            personalDictionary = PersonalDictionary.decode(KeyboardPrefs.personalDictionary(this))
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
            val api = AiServiceFactory.create(debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            aiRepository = AiRepository(api)
            aiController = AiController(
                editorGateway = editorGateway,
                repository = aiRepository,
                quotaProvider = ::currentAiQuota,
                jwtProvider = { KeyboardPrefs.aiJwt(this) },
                anonIdProvider = { KeyboardPrefs.aiAnonId(this) },
                isPrivateFieldProvider = { isPrivateField }
            )
            aiUiState = aiUiState.copy(quota = currentAiQuota(), authEmail = KeyboardPrefs.aiEmail(this) ?: "")
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
            editorGateway.beginSession(
                initialSelectionStart = editorInfo?.initialSelStart ?: -1,
                initialSelectionEnd = editorInfo?.initialSelEnd ?: -1
            )
            // A new input session means a new InputConnection -- any half-typed
            // word we were composing belongs to a field that's no longer ours.
            // Drop the bookkeeping silently rather than writing into the wrong
            // destination.
            typingController.onStartInput()
            voiceComposer.reset()
            pendingPredictionBoundary = null
            clearComposingContextCache()
            invalidateSuggestionWork()
            // The EditorInfo can change between sessions even when the input view
            // stays mounted (e.g. user taps a different field while our keyboard
            // is still up). onStartInputView doesn't always fire in that case, so
            // resolve the input-type flags here as well -- otherwise the
            // fieldAllowsAutoCap / isEmailField state from the PRIOR field would
            // survive the rebind and a stray capital could leak into an email
            // field, or an email chip suggestion could keep showing in a plain
            // text field. resolveAutoCap is idempotent.
            resolveAutoCap(editorInfo)
            resolveEnterAction(editorInfo)
            resolveKeypadMode(editorInfo)
            publishSuggestionState(
                if (isPrivateField) SuggestionUiState.Private else SuggestionUiState.Toolbar
            )
            if (!restarting) {
                Telemetry.imeSessionStarted(
                    language = telemetryLanguage(),
                    privateField = isPrivateField
                )
            }
            cursorKnownAtFieldStart =
                editorInfo?.initialSelStart == 0 && editorInfo.initialSelEnd == 0
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        safeApply {
            super.onStartInputView(editorInfo, restarting)
            // Fresh (non-restarting) sessions feed the engagement counter behind
            // the one-time in-app review prompt (see ReviewPromptPolicy).
            if (!restarting) KeyboardPrefs.recordUsageSession(this)
            // A new input session means a new InputConnection -- any half-typed
            // word we were composing belongs to a field that's no longer
            // ours. Drop it silently rather than trying to commit into the
            // wrong destination.
            // A new session starts on the keyboard, not a stale emoji/AI panel.
            closeEmojiPanel()
            if (aiUiState.isVisible) aiUiState = aiUiState.copy(isVisible = false)
            // The Enter key adapts to this field's IME action (search/go/send/...).
            resolveEnterAction(editorInfo)
            // Whether English auto-capitalization applies in this field.
            resolveAutoCap(editorInfo)
            cursorKnownAtFieldStart =
                editorInfo?.initialSelStart == 0 && editorInfo.initialSelEnd == 0
            if (shiftState == ShiftState.SHIFT) resetShift()
            // Email fields must NEVER carry an armed capital across from a prior
            // text field: shiftState may be ShiftState.SHIFT (one-shot, left over
            // from a sentence-end auto-cap in the previous field) or even
            // ShiftState.CAPS_LOCK (user pressed double-shift in the previous
            // field). resetShift() drops both. The per-key shift path in
            // onCharacter (line 1355) would otherwise uppercase the very first
            // letter typed into the email field, defeating
            // fieldAllowsAutoCap == false.
            if (isEmailField) resetShift()
            // Numeric fields open on the phone-style keypad.
            resolveKeypadMode(editorInfo)
            ensureActiveLanguageStoreLoaded("after_input_start")
            updateSuggestions()
            // Arm a capital for the first letter if the caret opens at a sentence
            // start (empty field, or resumed after a sentence terminator).
            maybeAutoCapitalize(useKnownInitialCursor = true)

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
     * and when the USER changes it (by tapping somewhere else).
     *
     * The one rule (see TypingController.onSelectionChanged): a composition
     * survives only while the caret is still at its end. Anything else
     * finalizes it in place -- cursor movement never adds, removes or
     * replaces text.
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
            if (predictionBoundaryMutationDepth > 0) {
                editorGateway.noteSelection(newSelStart, newSelEnd)
                return@safeApply
            }
            val verifiedPredictionBoundaryEcho = pendingPredictionBoundary?.let {
                predictionBoundaryEchoMatches(
                    boundary = it,
                    selectionStart = newSelStart,
                    selectionEnd = newSelEnd
                )
            } == true
            if (!verifiedPredictionBoundaryEcho) {
                editorGateway.noteSelection(newSelStart, newSelEnd)
            }
            if (pendingPredictionBoundary != null) {
                currentBoundaryContext(isAmharic)
            }

            // Voice dictation in flight: a deliberate cursor move finalizes the
            // utterance where it was showing and restarts recognition cleanly at
            // the new position.
            if (voiceComposer.isComposing) {
                val cursorAtComposingEnd = newSelStart == newSelEnd &&
                    candidatesStart >= 0 &&
                    candidatesEnd >= candidatesStart &&
                    newSelStart == candidatesEnd
                if (!cursorAtComposingEnd) {
                    finalizeVoiceComposing()
                    voiceInputController?.restartSession()
                }
                return@safeApply
            }

            typingController.onSelectionChanged(
                selectionStart = newSelStart,
                selectionEnd = newSelEnd,
                candidatesStart = candidatesStart,
                candidatesEnd = candidatesEnd
            )
            updateSuggestions()
            if (!typingController.isComposing) {
                maybeAutoCapitalize()
            }
        }
    }

    override fun onFinishInput() {
        safeApply {
            // The session is ending without an explicit commit: freeze what is
            // visible, never rewrite it.
            typingController.onFinishInput()
            voiceInputController?.stop()
            finalizeVoiceComposing()
            voiceComposer.reset()
            resetVoiceUi()
            pendingPredictionBoundary = null
            clearComposingContextCache()
            invalidateSuggestionWork()
            publishSuggestionState(SuggestionUiState.Toolbar)
            editorGateway.endSession()
            super.onFinishInput()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        safeApply {
            super.onFinishInputView(finishingInput)
            // Field is going away without an explicit commit. Finalize the
            // composing region in place so hiding the keyboard can never erase
            // or replace text.
            typingController.onFinishInput()
            pendingPredictionBoundary = null
            clearComposingContextCache()
            invalidateSuggestionWork()
            voiceInputController?.stop()
            finalizeVoiceComposing()
            resetVoiceUi()
            pauseLifecycleIfResumed()
            if (isLowRam) {
                idleReleaseHandler.removeCallbacks(idleRelease)
                idleReleaseHandler.postDelayed(idleRelease, LOW_RAM_IDLE_RELEASE_MS)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        safeApply {
            super.onTrimMemory(level)
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                amharicSuggestionCache.clear()
                amharicCommitCandidateCache.clear()
                clearComposingContextCache()
                predictionCache.trimToSize(if (isLowRam) 8 else 24)
                if (::amharicDictionary.isInitialized) amharicDictionary.clearCache()
                if (::englishDictionary.isInitialized) englishDictionary.clearCache()
                if (::emojiRepository.isInitialized && !showEmojiPanel) {
                    emojiRepository.release()
                }
            }
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                languageLoadGeneration += 1
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                predictionCache.clear()
                publishSuggestionState(SuggestionUiState.Toolbar)
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
            typingController.onFinishInput()
            pendingPredictionBoundary = null
            clearComposingContextCache()
            editorGateway.endSession()
            super.onDestroy()
            currentInstance = null
            KeyboardPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
            aiScope.cancel()
            voiceInputController?.destroy()
            resetVoiceUi()
            invalidateSuggestionWork()
            suggestionExecutor.shutdownNow()
            if (predictionExecutorDelegate.isInitialized()) {
                predictionExecutor.shutdownNow()
            }
            predictionCache.clear()
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
