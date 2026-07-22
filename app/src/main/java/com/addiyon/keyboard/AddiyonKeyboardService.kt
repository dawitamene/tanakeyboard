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
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
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
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.model.onShiftTap
import com.addiyon.keyboard.emoji.EmojiBackspace
import com.addiyon.keyboard.emoji.EmojiRepository
import com.addiyon.keyboard.emoji.RecentEmojiStore
import com.addiyon.keyboard.emoji.SkinToneStore
import com.addiyon.keyboard.suggestion.AmharicPrefixCompletion
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.NgramContext
import com.addiyon.keyboard.suggestion.NgramDictionary
import com.addiyon.keyboard.suggestion.NgramModel
import com.addiyon.keyboard.suggestion.WordDictionary
import com.addiyon.keyboard.suggestion.WordTrie
import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.AmharicWordReverser
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import com.addiyon.keyboard.suggestion.matchCase
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
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

    // Whether the current field accepts English auto-capitalization (a text
    // field that isn't a password/email/URI). Recomputed per input session in
    // onStartInputView; consulted by maybeAutoCapitalize.
    private var fieldAllowsAutoCap = false

    // Whether the current field takes an email address. Observable because
    // the letter layouts' comma key re-labels itself "@" in email fields
    // (see KeyRow). Recomputed per input session in onStartInputView.
    var isEmailField by mutableStateOf(false)
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
     * True when [suggestions] holds next-word PREDICTIONS (empty Amharic
     * buffer, context from the field) rather than completions of a word
     * being typed. The strip renders prediction chips without the chip-0
     * "space commits this" highlight, because with nothing composing, space
     * just inserts a space.
     */
    var suggestionsArePredictions by mutableStateOf(false)
        private set

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
        // to what's in the buffer. discardOnExit: the inline (raw Latin)
        // word is TENTATIVE -- leaving it without space/enter/punctuation/a
        // tapped suggestion removes it from the field instead of committing
        // it; see WordComposer's "WHY AMHARIC DISCARDS ON EXIT" doc.
        // Backspace uses the default one-char step so each typed letter can
        // be cleared individually.
        commitTransform = { raw -> topAmharicCandidate(raw) },
        discardOnExit = true,
        // Every committed word is remembered fidel -> raw Latin, so the caret
        // can walk back onto it and resume typing it -- see
        // [amharicCommitHistory] / [maybeResumeWordAtCursor].
        onCommit = { raw, display ->
            if (display.isNotEmpty()) amharicCommitHistory[display] = raw
        }
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
    private lateinit var amharicNgrams: NgramDictionary
    private lateinit var englishNgrams: NgramDictionary
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
        if (!suggestionRefreshGate.requestRefresh()) return
        if (!::amharicDictionary.isInitialized) {
            publishSuggestions(emptyList())
            return
        }
        if (isAmharic) {
            val latinBuffer = if (amharicComposer.isComposing) amharicComposer.raw else ""
            if (latinBuffer.isEmpty()) {
                // Word boundary: the field text before the (next) composing
                // region can change here, so the per-word caches are stale.
                composingNgramBoost = null
                composingPredictionCasing = emptyMap()
                amharicSuggestionCache.clear()
                // Nothing composing: offer next-word PREDICTIONS from the
                // committed words before the cursor (empty when there's no
                // usable context, restoring the toolbar icons).
                publishSuggestions(
                    ngramPredictions(NEXT_WORD_LIMIT).map { it.word },
                    arePredictions = true
                )
            } else {
                publishSuggestions(amharicSuggestions(latinBuffer))
            }
        } else {
            val typed = if (englishComposer.isComposing) englishComposer.raw else ""
            if (typed.isEmpty()) {
                // Word boundary: the committed text before the cursor can
                // change here, so the per-word context boost is stale.
                composingNgramBoost = null
                composingPredictionCasing = emptyMap()
                // Nothing composing: next-word PREDICTIONS from the committed
                // words before the cursor (empty when there's no usable
                // context, restoring the toolbar icons).
                publishSuggestions(
                    nextWordPredictions(
                        englishNgrams, NgramContext.ENGLISH, englishComposer, NEXT_WORD_LIMIT
                    ).map { it.word },
                    arePredictions = true
                )
            } else {
                publishSuggestions(englishSuggestions())
            }
        }
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
    private val amharicSuggestionCache =
        object : LinkedHashMap<String, List<String>>(SUGGESTION_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) =
                size > SUGGESTION_CACHE_SIZE
        }

    /**
     * Bigram/trigram next-word predictions for the words preceding the cursor,
     * read from the field via [contextReader] and looked up in [ngrams]; empty
     * until the model loads or when the field gives no context. While
     * composing, the raw composing region sits immediately before the cursor
     * and would read as a hard boundary, so it's stripped from the tail first
     * ([composer]'s raw buffer).
     */
    private fun nextWordPredictions(
        ngrams: NgramDictionary,
        contextReader: NgramContext,
        composer: WordComposer,
        limit: Int
    ): List<NgramModel.Prediction> {
        val raw = if (composer.isComposing) composer.raw else ""
        val before = currentInputConnection
            ?.getTextBeforeCursor(NgramContext.WINDOW + raw.length, 0)
            ?: return emptyList()
        val field = if (raw.isNotEmpty() && before.endsWith(raw)) {
            before.subSequence(0, before.length - raw.length)
        } else {
            before
        }
        val context = contextReader.extract(field)
        val prev1 = context.prev1 ?: return emptyList()
        return ngrams.predict(context.prev2, prev1, limit)
    }

    /** Amharic next-word predictions -- see [nextWordPredictions]. */
    private fun ngramPredictions(limit: Int): List<NgramModel.Prediction> =
        nextWordPredictions(amharicNgrams, NgramContext.AMHARIC, amharicComposer, limit)

    /**
     * The reading that lands in the field when the current Amharic word is
     * committed: the best exact dictionary reading if one exists, else the
     * structurally greedy reading. Longer completions remain tap-only.
     */
    private fun topAmharicCandidate(raw: String): String {
        if (raw.isEmpty()) return ""
        if (!::amharicDictionary.isInitialized) return Transliterator.transliterate(raw)
        val candidateReadings = Transliterator.candidateReadings(raw)
        return CandidateRanker.bestCommitCandidate(
            candidateReadings.map { it.text },
            amharicDictionary::frequencyOf,
            quirkReadings = candidateReadings.filter { it.isQuirk }.map { it.text }.toSet(),
            preferGreedy = Transliterator.hasExplicitFamilySelection(raw)
        ) ?: Transliterator.transliterate(raw)
    }

    private fun publishSuggestions(value: List<String>, arePredictions: Boolean = false) {
        if (suggestions != value) suggestions = value
        val predictions = arePredictions && value.isNotEmpty()
        if (suggestionsArePredictions != predictions) suggestionsArePredictions = predictions
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
    private fun englishSuggestions(): List<String> {
        val typed = englishComposer.raw
        if (typed.isEmpty()) return emptyList()

        val key = typed.lowercase()

        // Context nudge (boost weights) + proper-noun casing overrides, both
        // keyed by the per-char lowercase fold and computed together, once, for
        // the composing word's lifetime (see composingNgramBoost).
        if (composingNgramBoost == null) {
            val preds = nextWordPredictions(
                englishNgrams, NgramContext.ENGLISH, englishComposer, ENGLISH_NGRAM_CONTEXT_LIMIT
            )
            composingNgramBoost = preds.associate { englishFold(it.word) to it.weight }
            composingPredictionCasing = preds
                .filter { it.word != it.word.lowercase() }
                .associate { englishFold(it.word) to it.word }
        }
        val ngramNext = composingNgramBoost ?: emptyMap()
        val casing = composingPredictionCasing

        val pool = englishDictionary.suggestionEntries(key, ENGLISH_COMPLETION_POOL)
            .map { CandidateRanker.DictionaryWord(it.word, it.frequency) }
        val merged = ArrayList<String>(ENGLISH_SUGGESTION_LIMIT)
        for (word in CandidateRanker.rankByContext(pool, ngramNext, ::englishFold, ENGLISH_EXACT_LIMIT)) {
            // Swap in the context proper-noun casing ("york" -> "York") when the
            // model predicts this word capitalized after the same context.
            val cased = casing[englishFold(word)] ?: word
            if (cased !in merged) merged.add(cased)
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
     * Amharic suggestions are scored from exact dictionary readings,
     * prefix completions, the current literal fallback, and fuzzy matches.
     */
    private fun amharicSuggestions(latin: String): List<String> {
        if (latin.isEmpty()) return emptyList()
        if (amharicDictionary.isReady) {
            amharicSuggestionCache[latin]?.let { return it }
        }

        val candidateReadings = Transliterator.candidateReadings(latin)
        val readings = candidateReadings.map { it.text }
        val visibleReadings = readings
        // Structural split readings: kept for completions/quirk chips, but not
        // allowed to win the default over the natural greedy reading.
        val quirkReadings = candidateReadings.filter { it.isQuirk }.map { it.text }.toSet()
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
                it.length > 1 && (!amharicDictionary.isReady || amharicDictionary.isWord(it))
            }
        val completionCache = HashMap<String, List<CandidateRanker.DictionaryWord>>()
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
        val ngramNext = composingNgramBoost
            ?: ngramPredictions(AMHARIC_SUGGESTION_LIMIT)
                // Folded keys, matching CandidateRanker.ngramBoost's folded
                // lookup, so a variant-spelled candidate still gets its boost.
                .associate { EthiopicNormalizer.normalize(it.word) to it.weight }
                .also { composingNgramBoost = it }

        val ranked = CandidateRanker.rankAmharic(
            readings = readings,
            limit = AMHARIC_SUGGESTION_LIMIT,
            frequencyOf = amharicDictionary::frequencyOf,
            completionsForPrefix = completionsForPrefix,
            visibleReadings = visibleReadings,
            quirkReadings = quirkReadings,
            ngramNext = ngramNext,
            preferGreedy = Transliterator.hasExplicitFamilySelection(latin)
        )
        if (ranked.size >= AMHARIC_SUGGESTION_LIMIT ||
            readings.none { it.length <= MAX_FUZZY_READING_LENGTH }
        ) {
            return pinPreferredAlternate(ranked, preferredAlternate).also {
                if (amharicDictionary.isReady) amharicSuggestionCache[latin] = it
            }
        }

        // Fuzzy pass, bounded three ways to keep the worst keystroke cheap:
        // only the top [MAX_FUZZY_READINGS] readings (rank order -- the rest
        // are deep alternates that almost never contribute a correction),
        // the full 2-edit budget only for the TOP reading (an alternate
        // reading is already a variation; giving all of them 2 edits is
        // what made long non-word buffers freeze), and stop as soon as the
        // strip's worth of matches is gathered.
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

        return pinPreferredAlternate(
            CandidateRanker.rankAmharic(
                readings = readings,
                limit = AMHARIC_SUGGESTION_LIMIT,
                frequencyOf = amharicDictionary::frequencyOf,
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

    /**
     * Force the preferred alternate to sit directly behind the default reading.
     * Left in place when it is already the default, and a no-op when there is no
     * alternate. Result is re-capped to the limit.
     */
    private fun pinPreferredAlternate(
        ranked: List<String>,
        preferredAlternate: String?
    ): List<String> {
        if (preferredAlternate == null || ranked.firstOrNull() == preferredAlternate) return ranked
        val pinned = ArrayList<String>(ranked.size + 1)
        pinned.addAll(ranked)
        pinned.remove(preferredAlternate)
        pinned.add(minOf(1, pinned.size), preferredAlternate)
        return pinned.take(AMHARIC_SUGGESTION_LIMIT)
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

    /** Re-derives [keyboardHeightScale] from the saved preference. */
    private fun refreshKeyboardHeightScale() {
        keyboardHeightScale = KeyboardPrefs.keyboardHeightScale(this)
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
     * the word, never inside a composing region. The repository load is a
     * safe no-op if the sequential startup chain already started it; the
     * panel shows a loading state until [EmojiRepository.isReady].
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
        emojiSearchField = null
    }

    /** Enters emoji search mode (query row + English key rows). */
    fun openEmojiSearch() {
        emojiSearchField = TextFieldValue()
    }

    /** Leaves search mode back to the browse panel. */
    fun closeEmojiSearch() {
        emojiSearchField = null
    }

    /** The search query row's clear (x) button: empty the query, stay in search. */
    fun clearEmojiSearchQuery() {
        if (emojiSearchField != null) emojiSearchField = TextFieldValue()
    }

    /**
     * The search BasicTextField's onValueChange: the only edits it can
     * originate itself are touch-driven (cursor moves, selection drags) --
     * text edits come through the key guards, which write [emojiSearchField]
     * directly.
     */
    fun updateEmojiSearchField(value: TextFieldValue) {
        if (emojiSearchField != null) emojiSearchField = value
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
        // Persisted so the chosen language survives the service being torn
        // down (switching to another keyboard and back, reboots, ...).
        KeyboardPrefs.setAmharicMode(this, isAmharic)
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
            // The keypad's "*#(" key: exit to the full numbers/symbols page
            // (the keypad itself carries no symbols). The NUMBERS page's
            // "1234" key ([openKeypad]) is the way back in.
            NumbersMode.KEYPAD -> NumbersMode.NUMBERS
            NumbersMode.OFF -> NumbersMode.OFF
        }
    }

    /**
     * The "1234" key on the NUMBERS page: shows the phone-style keypad
     * ([NumbersMode.KEYPAD]). No composer flush needed -- reaching the
     * NUMBERS page already committed any in-flight word -- but flushing is
     * harmless and keeps this safe if the key ever moves to a letter layout.
     */
    fun openKeypad() {
        leaveVoiceModeForKeyboardInput()
        closeEmojiPanel()
        activeComposer.commit()
        numbersMode = NumbersMode.KEYPAD
        updateSuggestions()
    }

    /**
     * Shift key tapped. A single tap toggles the one-shot SHIFT on/off; a
     * quick double tap engages CAPS_LOCK; a tap while caps-locked releases
     * it -- see [ShiftState.onShiftTap] for the full transition table. The
     * double-tap window is the platform's own double-tap timeout.
     */
    fun toggleShift() {
        leaveVoiceModeForKeyboardInput()
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastShiftTapUptimeMs <= ViewConfiguration.getDoubleTapTimeout()
        lastShiftTapUptimeMs = now
        shiftState = shiftState.onShiftTap(isDoubleTap)
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
        lastShiftTapUptimeMs = 0L
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
        }
    }

    fun resetShift() {
        shiftState = ShiftState.OFF
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
        val inputType = editorInfo?.inputType ?: 0
        val isText = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        // Default-ON for ordinary text fields (Gboard/SwiftKey behavior),
        // rather than only when the editor opts in via
        // TYPE_TEXT_FLAG_CAP_SENTENCES -- most apps never set that flag, so
        // gating on it left sentence capitalization off almost everywhere. The
        // NO_AUTOCAP_VARIATIONS deny-list (password/email/URI/filter) plus the
        // text-class check are what keep a stray capital out of the fields that
        // shouldn't get one.
        fieldAllowsAutoCap = isText && variation !in NO_AUTOCAP_VARIATIONS
        isEmailField = isText && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
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
        if (isAmharic || isNumberMode || !fieldAllowsAutoCap) return
        if (shiftState != ShiftState.OFF || activeComposer.isComposing) return
        val before = textBeforeCursor
            ?: currentInputConnection?.getTextBeforeCursor(SENTENCE_LOOKBEHIND, 0)
        if (SentenceCase.startsNewSentence(before)) {
            shiftState = ShiftState.SHIFT
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
        // Emoji search intercepts the real English key rows: keystrokes build
        // the query instead of touching the field. Same shift resolution as
        // the normal path so the query looks like what was typed (search
        // itself lowercases).
        emojiSearchField?.let { field ->
            if (showEmojiPanel) {
                emojiSearchField =
                    field.insertAtCursor(if (isShiftEnabled) latin.uppercase() else latin.lowercase())
                consumeShiftAfterCharacter()
                return
            }
        }
        leaveVoiceModeForKeyboardInput()
        val output = if (isShiftEnabled) latin.uppercase() else latin.lowercase()

        when {
            isNumberMode -> {
                currentInputConnection?.commitText(output, 1)
            }
            !isWordCharacter(output) -> {
                activeComposer.commit()
                val text = if (isAmharic) Transliterator.transliterate(output) else output
                currentInputConnection?.commitText(text, 1)
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

    /**
     * Whether [output] belongs inside a composing word rather than
     * terminating one. Letters in both languages; apostrophe (English
     * contractions, and the SERA spelling of the glottal አ family) and
     * backtick (SERA pharyngeal ዐ) also count -- neither is on a letter
     * layout today, but if one is ever added it must feed the composer,
     * not chop the word.
     */
    private fun isWordCharacter(output: String) = isComposingWordCharacter(output)

    private val suggestionRefreshGate = SuggestionRefreshGate()

    fun onDeleteGestureStart() {
        suggestionRefreshGate.beginDeleteGesture()
    }

    fun onDeleteGestureEnd() {
        val pendingRefresh = suggestionRefreshGate.endDeleteGesture()
        // A backspace TAP's selection update almost always arrives while the
        // finger is still down -- inside the gesture, where the gate
        // deliberately suppresses cursor-aware resume (so a HELD repeat
        // doesn't adopt the shrinking word mid-delete). No further selection
        // callback comes after release, so this is the moment to adopt the
        // word the caret now sits at the end of ("cana ", backspace -> the
        // strip must offer "Canada" again).
        maybeResumeWordAfterDeleteGesture()
        if (pendingRefresh || activeComposer.isComposing) updateSuggestions()
    }

    /**
     * The gesture-end variant of [maybeResumeWordAtCursor]: the last
     * onUpdateSelection was consumed mid-gesture, so its selection args can't
     * be trusted to still be current -- read the caret position fresh from
     * the editor instead (one extracted-text round-trip, once per gesture).
     * Editors that don't support text extraction simply don't resume here;
     * the next real cursor move still goes through the normal path.
     */
    private fun maybeResumeWordAfterDeleteGesture() {
        if (activeComposer.isComposing) return
        val extracted = currentInputConnection
            ?.getExtractedText(ExtractedTextRequest(), 0)
            ?: return
        if (extracted.selectionStart != extracted.selectionEnd) return
        maybeResumeWordAtCursor(extracted.startOffset + extracted.selectionStart)
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
     * [WordComposer.commit] replaces the underlined raw Latin with its
     * commitTransform: for Amharic that's the top-ranked fidel reading (the
     * same as suggestions[0], highlighted in the strip) -- so space picks
     * the default reading and a tap is only needed for a NON-default one;
     * for English it finalizes the inline composed word as-is. With no word
     * in flight it's a plain space.
     */
    fun onSpace() {
        // CLDR annotations are multi-word ("red heart"), so space belongs to
        // the emoji search query, not the field.
        emojiSearchField?.let { field ->
            if (showEmojiPanel) {
                emojiSearchField = field.insertAtCursor(" ")
                return
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
        val beforeSpace = currentInputConnection?.getTextBeforeCursor(SENTENCE_LOOKBEHIND, 0)
        currentInputConnection?.commitText(" ", 1)
        updateSuggestions()
        maybeAutoCapitalize(beforeSpace?.let { "$it " })
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
        // Pre-newline text, for the same reason onSpace reads before committing.
        val beforeEnter = ic?.getTextBeforeCursor(SENTENCE_LOOKBEHIND, 0)
        if (enterAction == EnterAction.NEWLINE) {
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            ic?.performEditorAction(editorActionId)
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
        val inputView = AddiyonKeyboardView(this)
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        inputView.setViewTreeLifecycleOwner(this)
        inputView.setViewTreeSavedStateRegistryOwner(this)

        ensureLifecycleStarted()
        updateSystemNavigationBar()

        return inputView
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
        refreshKeyboardHeightScale()
        refreshFeedbackPrefs()
        // Restore the last-used language BEFORE the dictionary loads below:
        // the active language's dictionary is deliberately loaded first.
        isAmharic = KeyboardPrefs.amharicMode(this)
        KeyboardPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)

        amharicDictionary = WordDictionary(this, "amharic_words.dat", EthiopicNormalizer::normalize)
        englishDictionary = WordDictionary(this, "english_words.dat")
        amharicNgrams = NgramDictionary(this, "amharic_ngrams.dat", EthiopicNormalizer::normalize)
        englishNgrams = NgramDictionary(this, "english_ngrams.dat", ::englishFold)
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
        // Parsing the dictionary lines (~254k Amharic, ~250k English) happens
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
                // Then the n-gram models (small next to the tries) one after
                // another, then emoji data last: none gates typing, and keeping
                // the loads sequential avoids overlapping allocation storms.
                amharicNgrams.loadAsync {
                    updateSuggestions()
                    englishNgrams.loadAsync {
                        updateSuggestions()
                        emojiRepository.loadAsync()
                    }
                }
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
        // Numeric fields open on the phone-style keypad.
        resolveKeypadMode(editorInfo)
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
     * that freezes the underlined text in place; for Amharic the tentative
     * word is removed from the field (walking away is not an accept
     * gesture -- see [WordComposer.abandon]) -- otherwise the next
     * keystroke would keep rewriting a region that's no longer near the
     * caret.
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

        if (activeComposer.isComposing) {
            // Movement consistent with our own composing pushes: nothing to do.
            if (cursorInsideComposing) return
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

    /**
     * Adopts the committed word the caret just landed at the END of back into
     * composition (Gboard-style cursor-aware suggestions): type "cana",
     * space, backspace over the space -- the caret sits after "cana" again
     * and the strip should offer "Canada" as if the word were still being
     * typed. The field word is lifted into the composing region
     * (setComposingRegion) and the composer re-seeded via
     * [WordComposer.resume]; a resumed word is exempt from Amharic's
     * discard-on-exit (see [WordComposer]), so walking away restores it.
     *
     * English adopts the literal field word. Amharic needs the raw LATIN
     * behind the committed fidel: [amharicCommitHistory] first, then
     * [AmharicWordReverser]'s round-trip-verified reversal -- and when
     * neither knows the word, no resume rather than a guess.
     *
     * Suppressed while a held-delete gesture is repeating (adopting the
     * shrinking word mid-repeat would swap deletion granularity under the
     * held key) and wherever the composer is out of play (numeric pages,
     * emoji panel, voice). [cursorPosition] is the collapsed selection from
     * onUpdateSelection; the word itself is read fresh from the connection,
     * so a stale callback sees the field's CURRENT tail and simply finds a
     * boundary character instead of a word.
     */
    private fun maybeResumeWordAtCursor(cursorPosition: Int) {
        if (cursorPosition <= 0 || isNumberMode || showEmojiPanel) return
        if (voiceUiState.isVoiceMode || suggestionRefreshGate.isDeleteGestureActive) return
        if (activeComposer.isComposing) return
        val ic = currentInputConnection ?: return
        // Only the END of a word: any word character right after the caret
        // means it landed inside one.
        val after = ic.getTextAfterCursor(1, 0) ?: return
        if (after.isNotEmpty() && (after[0].isLetter() || after[0] == '\'')) return
        val before = ic.getTextBeforeCursor(ResumableWord.LOOKBEHIND, 0) ?: return
        if (isAmharic) {
            val fidel = ResumableWord.trailingEthiopicWord(before) ?: return
            val latin = amharicCommitHistory[fidel]
                ?: AmharicWordReverser.reverse(fidel)
                ?: return
            // Guard on the return value: an editor that doesn't support
            // composing regions must not get resume()'s setComposingText,
            // which would INSERT a duplicate instead of replacing the word.
            if (!ic.setComposingRegion(cursorPosition - fidel.length, cursorPosition)) return
            amharicComposer.resume(latin)
        } else {
            val word = ResumableWord.trailingLatinWord(before) ?: return
            if (!ic.setComposingRegion(cursorPosition - word.length, cursorPosition)) return
            englishComposer.resume(word)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Field is going away without an explicit commit. For English,
        // finalize the composing region IN PLACE (no new text inserted) --
        // using commit()/commitText here duplicated the word, because the
        // framework also finalizes the still-active composing region as the
        // session ends. For Amharic the tentative in-field word is removed
        // (a hidden keyboard is not an accept gesture) unless it was resumed
        // from already-committed text, which is restored. See
        // WordComposer.finish().
        activeComposer.finish()
        updateSuggestions()
        voiceInputController?.stop()
        finalizeVoiceComposing()
        resetVoiceUi()
        pauseLifecycleIfResumed()
    }

    override fun onDestroy() {
        super.onDestroy()
        KeyboardPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        voiceInputController?.stop()
        resetVoiceUi()
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private fun ensureLifecycleStarted() {
        val state = lifecycleRegistry.currentState
        if (state == Lifecycle.State.DESTROYED) return
        if (!state.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    private fun ensureLifecycleResumed() {
        ensureLifecycleStarted()
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    private fun pauseLifecycleIfResumed() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
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
