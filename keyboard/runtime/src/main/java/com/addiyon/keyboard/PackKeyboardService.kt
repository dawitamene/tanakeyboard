package com.addiyon.keyboard

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.AbstractComposeView
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
import com.addiyon.keyboard.emoji.EmojiUiController
import com.addiyon.keyboard.emoji.RecentEmojiStore
import com.addiyon.keyboard.emoji.SkinToneStore
import com.addiyon.keyboard.suggestion.EmailChip
import com.addiyon.keyboard.suggestion.EmailSuggestions
import com.addiyon.keyboard.suggestion.PersonalDictionary
import com.addiyon.keyboard.suggestion.PerWordCache
import com.addiyon.keyboard.suggestion.PredictionCache
import com.addiyon.keyboard.suggestion.SuggestionTrace
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.suggestion.PersonalCompletionSource
import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguageContext
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.LanguageRegistry
import com.addiyon.keyboard.language.LanguagePresentationCategory
import com.addiyon.keyboard.language.android.AndroidLanguagePackEnvironment
import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider
import com.addiyon.keyboard.product.LanguageKeyBehavior
import com.addiyon.keyboard.product.TYPING_GUIDE_FEATURE_ID
import com.addiyon.keyboard.suggestion.matchCase
import com.addiyon.keyboard.util.MemoryProbe
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ui.KeyboardController
import com.addiyon.keyboard.runtime.BaseKeyboardService
import com.addiyon.keyboard.features.appshell.KeyboardAppShellActivity
import com.addiyon.keyboard.features.appshell.KeyboardShellDestinations
import com.addiyon.keyboard.ui.KeyboardScreen
import com.addiyon.keyboard.ui.OptionalKeyboardUi
import com.addiyon.keyboard.ui.keys.CharacterKeyPresentation
import com.addiyon.keyboard.ui.keys.LanguageKeyPresentation
import com.addiyon.keyboard.ui.SuggestionTap
import com.addiyon.keyboard.ui.SuggestionUiState
import com.addiyon.keyboard.ui.SUGGESTION_LIST_LIMIT
import com.addiyon.keyboard.ui.SUGGESTION_STRIP_VISIBLE_LIMIT
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.voice.VoiceComposer
import com.addiyon.keyboard.voice.VoiceErrorKind
import com.addiyon.keyboard.voice.VoiceInputController
import com.addiyon.keyboard.voice.VoicePermissionActivity
import com.addiyon.keyboard.voice.VoiceUiState
import com.addiyon.keyboard.voice.isVoiceMode
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Max chips in the Amharic suggestion strip: the live word's readings plus
 * dictionary completions. The strip scrolls horizontally, so this can be
 * generous.
 */
private const val AMHARIC_SUGGESTION_LIMIT = SUGGESTION_LIST_LIMIT

/**
 * Max next-word prediction chips shown when the Amharic buffer is empty
 * (right after a commit): pure bigram/trigram predictions from the words
 * before the cursor, zero keystrokes typed.
 */
private const val NEXT_WORD_LIMIT = SUGGESTION_LIST_LIMIT

/**
 * Next-word fallback when the n-gram model has no successor for the current
 * context: the top most frequent dictionary words, capped at the shared
 * suggestion-list limit.
 */
private const val PREDICTION_FALLBACK_ENGLISH_LIMIT = SUGGESTION_LIST_LIMIT

private const val LOW_RAM_IDLE_RELEASE_MS = 20_000L
private const val PREDICTION_CACHE_SIZE = 64
private const val PREDICTION_IDENTITY_BEFORE = 256
private const val PREDICTION_IDENTITY_AFTER = 128

private const val ENGLISH_NGRAM_CONTEXT_LIMIT = 10

internal fun resolveConfiguredLanguagePackProviders(
    orderedLanguageIds: List<String>,
    providers: List<AndroidLanguagePackProvider>
): List<AndroidLanguagePackProvider> {
    val providerIds = providers.map { it.languageId.value }
    require(providerIds.distinct().size == providerIds.size) {
        "Language pack providers must have unique IDs: $providerIds"
    }
    val configuredIds = orderedLanguageIds.toSet()
    val missingIds = orderedLanguageIds.filterNot(providerIds::contains)
    val extraIds = providerIds.filterNot(configuredIds::contains)
    require(missingIds.isEmpty() && extraIds.isEmpty()) {
        "Language pack providers do not match product languages; " +
            "missing=$missingIds, extra=$extraIds"
    }
    val providersById = providers.associateBy { it.languageId.value }
    return orderedLanguageIds.map(providersById::getValue)
}


/** Characters of context read for sentence-start detection -- enough to see
 *  past any realistic run of trailing spaces to the terminator. See
 *  [SentenceCase]. */
private const val SENTENCE_LOOKBEHIND = 16

abstract class PackKeyboardService : BaseKeyboardService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    EmojiUiController,
    KeyboardController {

    final override val keyboardProduct
        get() = configuredKeyboardProduct

    protected abstract val configuredKeyboardProduct: com.addiyon.keyboard.product.KeyboardProduct
    protected abstract val appShellActivityClass: Class<out KeyboardAppShellActivity>
    protected abstract val configuredLanguagePackProviders: List<AndroidLanguagePackProvider>

    final override val showTypingGuide: Boolean
        get() = TYPING_GUIDE_FEATURE_ID in keyboardProduct.featureIds

    final override val showLanguageSwitchKey: Boolean
        get() = keyboardProduct.showsLanguageSwitchKey

    private fun onLanguageEngineOutOfMemory() = enterEmergencyMode()

    private fun onLanguageEngineFailure(throwable: Throwable, operation: String) {
        SafeLog.e(throwable, operation)
    }

    private fun onLanguageEngineWarning(message: String) {
        SafeLog.w(message)
    }

    protected fun prepareForOptionalPanel() {
        closeEmojiPanel()
        leaveVoiceModeForKeyboardInput()
        hideExpandedSuggestions()
    }

    protected fun onOptionalFeatureTextReplaced() {
        typingController.onStartInput()
        pendingPredictionBoundary = null
        clearComposingContextCache()
        invalidateSuggestionWork()
        updateSuggestions()
    }

    protected fun onOptionalFeatureTextCommitted(text: String): Boolean {
        val committed = typingController.onPhraseCompletion(text)
        if (committed) {
            pendingPredictionBoundary = null
            clearComposingContextCache()
            invalidateSuggestionWork()
            updateSuggestions()
        }
        return committed
    }

    protected open fun onEditorContextChanged() = Unit

    // ----------------------------
    // Lifecycle (UNCHANGED)
    // ----------------------------
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    companion object {
        const val EXTRA_APP_SHELL_DESTINATION = KeyboardAppShellActivity.EXTRA_OPEN_DESTINATION
        const val DESTINATION_SETTINGS = KeyboardShellDestinations.SETTINGS
        const val DESTINATION_THEMES = KeyboardShellDestinations.THEMES
        const val DESTINATION_GUIDE = KeyboardShellDestinations.GUIDE
        const val DESTINATION_FEEDBACK = KeyboardShellDestinations.FEEDBACK

        @Volatile
        var currentInstance: PackKeyboardService? = null
    }

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    // ----------------------------
    // KEYBOARD STATE
    // ----------------------------

    private lateinit var languageRegistry: LanguageRegistry

    var activeLanguageId by mutableStateOf(LanguageId.of("uninitialized"))
        private set

    override val activePack: LanguagePack
        get() = languageRegistry.activePack

    val installedPacks: List<LanguagePack>
        get() = languageRegistry.installedPacks

    override val latinSearchLayout
        get() = languageRegistry.installedPacks
            .firstOrNull { it.capabilities.hasLetterCase }
            ?.letterLayout
            ?: activePack.letterLayout

    override val languageKeyPresentation: LanguageKeyPresentation
        get() {
            if (
                keyboardProduct.languageKeyBehavior ==
                LanguageKeyBehavior.SWITCH_TO_NEXT_INPUT_METHOD
            ) {
                return LanguageKeyPresentation.systemSwitcher()
            }
            val packs = languageRegistry.installedPacks
            return LanguageKeyPresentation.cycling(
                labels = packs.map { it.languageKeyLabel },
                activeIndex = packs.indexOfFirst { it.id == activeLanguageId }
            )
        }

    override fun characterKeyPresentation(value: String): CharacterKeyPresentation {
        val transformed = activePack.typingProfile.transformStandalone(value)
        val punctuation = value == "," || value == "."
        if (transformed != value) {
            return if (punctuation) {
                CharacterKeyPresentation(
                    primaryText = transformed,
                    secondaryText = value,
                    longPressCommit = value,
                    secondaryFontSize = 14.sp
                )
            } else {
                CharacterKeyPresentation(
                    primaryText = value,
                    secondaryText = activePack.cornerPreview(value)
                )
            }
        }
        val alternate = if (punctuation) {
            languageRegistry.installedPacks.asSequence()
                .filter { it.id != activePack.id }
                .map { it.typingProfile.transformStandalone(value) }
                .firstOrNull { it != value }
        } else {
            null
        }
        return CharacterKeyPresentation(primaryText = value, longPressCommit = alternate)
    }

    val isAmharic: Boolean
        get() = activePack.presentationCategory == LanguagePresentationCategory.AMHARIC

    final override var numbersMode by mutableStateOf(NumbersMode.OFF)
        private set

    override val isNumberMode: Boolean
        get() = numbersMode != NumbersMode.OFF

    // The single source of truth for shift/caps-lock. isShiftEnabled below
    // is a derived convenience for callers that only care about "capitalize
    // or not" and don't need to distinguish one-shot shift from caps lock.
    final override var shiftState by mutableStateOf(ShiftState.OFF)
        private set

    /**
     * System-reported low-RAM flag (`isLowRamDevice`) OR a host with < 1 GB
     * total RAM. Used to gate RAM-expensive features (currently: the per-
     * keystroke fuzzy pass in [amharicSuggestions] / [englishSuggestions]) so
     * a 1 GB device doesn't have to run them. Captured at construction; the
     * service is created fresh per IME session so we don't need a ContentObserver.
     */
    final override var isLowRam: Boolean = false
        private set
    var isEmergencyMode by mutableStateOf(false)
        private set
    override val isShiftEnabled: Boolean
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
    val editorGateway = EditorGateway(
        connectionProvider = { currentInputConnection }
    )

    // What the Enter key should show and do in the current field, derived from
    // its IME action (see [resolveEnterAction], refreshed per input session in
    // onStartInputView). [editorActionId] is the raw EditorInfo action to fire
    // via performEditorAction when [enterAction] isn't a plain NEWLINE.
    final override var enterAction by mutableStateOf(EnterAction.NEWLINE)
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
    final override var isEmailField by mutableStateOf(false)
        private set

    final override var isPrivateField by mutableStateOf(false)
        private set

    // Tracked manually instead of relying on Compose's isSystemInDarkTheme(),
    // because an InputMethodService's window doesn't reliably deliver
    // configuration updates into the Compose tree the way an Activity does.
    // We read the current mode on creation and again whenever
    // onConfigurationChanged fires, so the keyboard UI can react to the
    // system dark/light toggle even while it's open.
    final override var isDarkTheme by mutableStateOf(false)
        private set

    // The selected color palette, read from the same SharedPreferences the
    // settings UI writes. Observable so the hosted keyboard recomposes when
    // it changes. See [refreshTheme].
    var palette by mutableStateOf(KeyboardPalette.CLASSIC)
        private set

    // Whether the optional Latin digit row renders above the top letter row
    // on the letter layouts. Observable (like [palette]) so the hosted
    // keyboard recomposes live when the user flips it in Preferences.
    final override var showNumberRow by mutableStateOf(false)
        private set

    // The user's "Keyboard height" multiplier, read from the same
    // SharedPreferences the settings slider writes. Observable (like
    // [showNumberRow]) so the hosted keyboard recomposes -- and resizes --
    // live when the user drags the slider. See [refreshKeyboardHeightScale].
    final override var keyboardHeightScale by mutableStateOf(KEYBOARD_HEIGHT_SCALE_DEFAULT)
        private set

    final override var vibrateOnKeypress by mutableStateOf(false)
        private set

    final override var soundOnKeypress by mutableStateOf(false)
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
        val languageId: LanguageId,
        val emailField: Boolean,
        val privateField: Boolean,
        val numberMode: Boolean
    )

    final override var suggestionUiState by mutableStateOf<SuggestionUiState>(SuggestionUiState.Toolbar)
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
    final override var showEmojiPanel by mutableStateOf(false)
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
    final override var emojiSearchField by mutableStateOf<TextFieldValue?>(null)
        private set

    /** The emoji search query text; null iff not in search mode. */
    override val emojiSearchQuery: String?
        get() = emojiSearchField?.text

    /**
     * base emoji -> the skin-tone variant the user last picked, mirrored
     * from [skinToneStore] so the grid cells can observe it. A state MAP,
     * not a state of a map: changing one base recomposes only cells reading
     * that key.
     */
    override val selectedSkinTones = mutableStateMapOf<String, String>()

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
        personalDictionary.learn(activeLanguageId.value, word)
        try {
            val before = editorGateway.textBeforeCursor(ResumableWord.LOOKBEHIND, optional = true)?.value
            val after = editorGateway.textAfterCursor(1, optional = true)?.value ?: ""
            if (before != null) {
                val email = ResumableWord.emailWordEndingAtCursor(before, after)
                if (email != null && email != word && '@' in email) {
                    personalDictionary.learnEmail(email)
                }
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
        else -> activePack.typingProfile
    }

    /**
     * The committed word the caret sits at the end of, if any -- the word the
     * suggestion strip answers when nothing is being composed, and the word a
     * completion chip tap re-opens and replaces (see WordAdoption). A language
     * engine may accept its committed script as a lookup key even when its live
     * composing buffer uses another representation. Continuing to type still
     * follows the profile's adoption rules; this read does not reverse-convert
     * or reopen the word by itself.
     *
     * Read lazily at each use (never cached): two short cursor-relative reads,
     * no absolute offsets involved. Optional, so an editor whose reads turn
     * slow simply degrades to next-word predictions instead of blocking.
     */
    private data class CaretWord(val word: String, val token: EditorToken)

    private fun currentCaretWord(): CaretWord? {
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
    private val activeSuggestionEngine: LanguageSuggestionEngine
        get() = activePack.suggestionEngine
    final override lateinit var emojiRepository: EmojiRepository
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
        onEditorContextChanged()
        safeApply {
            if (!suggestionRefreshGate.requestRefresh()) return@safeApply
            if (isPrivateField) {
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.Private)
                return@safeApply
            }
            if (!::languageRegistry.isInitialized || isNumberMode || isEmergencyMode) {
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
            if (!activePack.capabilities.providesSuggestions) {
                pendingPredictionBoundary = null
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.Toolbar)
                return@safeApply
            }

            val amharic = isAmharic
            val requestLanguageId = activeLanguageId
            val engine = activeSuggestionEngine
            val composing = typingController.isComposing
            val caret = if (composing) null else currentCaretWord()
            val typed = when {
                composing -> typingController.buffer
                caret != null -> caret.word
                else -> ""
            }
            val contextReader = activePack.contextReader
            val capturedContext = currentBoundaryContext(requestLanguageId)
                ?: if (composing) {
                    composingContextForWord(requestLanguageId, contextReader)
                } else {
                    captureNgramContext(contextReader)
                }
            val context = capturedContext?.context ?: LanguageContext(null, null)
            if (engine.isLoading) {
                invalidateSuggestionWork()
                publishSuggestionState(SuggestionUiState.LoadingLanguage)
                return@safeApply
            }
            if (typed.isEmpty()) {
                activeCompletionKey = null
                clearComposingContextCache()
                invalidateCompletionWork()
                if (context.prev1 == null) {
                    invalidatePredictionWork()
                    publishSuggestionState(SuggestionUiState.Toolbar)
                    return@safeApply
                }
                if (!engine.isReady) {
                    invalidatePredictionWork()
                    publishSuggestionState(SuggestionUiState.Toolbar)
                    return@safeApply
                }
                val request = PredictionRequestKey(
                    languageId = requestLanguageId,
                    prev2 = context.prev2,
                    prev1 = context.prev1,
                    limit = if (isLowRam) SUGGESTION_STRIP_VISIBLE_LIMIT else NEXT_WORD_LIMIT
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
                val previous1 = context.prev1 ?: return@safeApply
                val cached = predictionCache.get(
                    requestLanguageId.value,
                    context.prev2,
                    previous1,
                    request.limit
                )
                if (cached != null && cached.isNotEmpty()) {
                    val merged = cached.map { it.word }.let { words ->
                        if (!::personalDictionary.isInitialized) words
                        else if (amharic) {
                            val personal = personalDictionary.ranked(
                                requestLanguageId.value,
                                limit = request.limit
                            ).filter { w -> w.any { it in 'ሀ'..'፿' } }
                            (words + personal).distinct().take(request.limit)
                        } else {
                            val personal = personalDictionary.ranked(
                                requestLanguageId.value,
                                limit = request.limit
                            ).filter { '@' !in it }
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
                    languageId = requestLanguageId,
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
                val completionKey = CompletionRequestKey(typed, requestLanguageId)
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
                    languageId = requestLanguageId,
                    context = context,
                    observedCaretWord = caret?.word
                )
            }
        }
    }

    private data class CapturedNgramContext(
        val context: LanguageContext,
        val editorToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private data class PredictionBoundary(
        val context: LanguageContext,
        val languageId: LanguageId,
        val sourceToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private data class PredictionRequestKey(
        val languageId: LanguageId,
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
    private data class CompletionRequestKey(val raw: String, val languageId: LanguageId)

    private var activeCompletionKey: CompletionRequestKey? = null

    private data class PredictionRequest(
        val key: PredictionRequestKey,
        val editorToken: EditorToken,
        val contentIdentity: EditorContentIdentity
    )

    private var pendingPredictionBoundary: PredictionBoundary? = null
    private var activePredictionRequest: PredictionRequest? = null
    private val predictionCache = PredictionCache<List<EngineSuggestion>>(
        PREDICTION_CACHE_SIZE
    ) { languageId, word ->
        languageRegistry.installedPacks
            .firstOrNull { it.id.value == languageId }
            ?.suggestionEngine
            ?.normalize(word)
            ?: word
    }
    private val suggestionMainHandler = Handler(Looper.getMainLooper())
    private val idleReleaseHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable {
        if (!isLowRam) return@Runnable
        languageLoadGeneration += 1
        invalidateSuggestionWork()
        publishSuggestionState(SuggestionUiState.Toolbar)
        predictionCache.clear()
        pendingPredictionBoundary = null
        if (::languageRegistry.isInitialized) activeSuggestionEngine.release()
    }
    private val suggestionExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { runnable ->
            Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                    runnable.run()
                },
                "AddiyonSuggestions"
            )
        },
    )
    private var suggestionTask: Future<*>? = null
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

    private fun currentBoundaryContext(languageId: LanguageId): CapturedNgramContext? {
        val boundary = pendingPredictionBoundary ?: return null
        if (boundary.languageId != languageId || !editorGateway.isCurrent(boundary.sourceToken)) {
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
        context: LanguageContext,
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
            languageId = activeLanguageId,
            sourceToken = postToken,
            contentIdentity = identity
        )
    }

    private fun predictionContextAfterAcceptedWord(
        priorContext: LanguageContext,
        word: String
    ): LanguageContext =
        LanguageContext(
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
        suggestionTask?.cancel(true)
        suggestionTask = null
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
            if (::languageRegistry.isInitialized) {
                languageRegistry.installedPacks.forEach { it.suggestionEngine.clearCaches() }
            }
            if (::emojiRepository.isInitialized) emojiRepository.release()
            if (::languageRegistry.isInitialized) {
                languageRegistry.installedPacks.forEach { it.suggestionEngine.release() }
            }
        }
    }

    private fun scheduleSuggestionComputation(
        raw: String,
        languageId: LanguageId,
        context: LanguageContext,
        observedCaretWord: String? = null
    ) {
        activeCompletionKey = CompletionRequestKey(raw, languageId)
        val generation = ++suggestionGeneration
        val contextGeneration = composingContextGeneration
        val lowRam = isLowRam
        val pack = languageRegistry.installedPacks.firstOrNull { it.id == languageId } ?: return
        val engine = pack.suggestionEngine
        val amharic = pack.presentationCategory == LanguagePresentationCategory.AMHARIC
        suggestionTask?.cancel(true)
        suggestionExecutor.queue.clear()
        val personalCompletionSource = PersonalCompletionSource { prefix, limit ->
            if (!::personalDictionary.isInitialized) emptyList()
            else personalDictionary.completionEntries(languageId.value, prefix, limit)
        }
        val cachedBoost = composingNgramBoost
        val cachedCasing = composingPredictionCasing
        val immediatePair = when {
            cachedBoost != null -> cachedBoost to cachedCasing
            context.prev1 == null -> emptyMap<String, Int>() to emptyMap()
            else -> null
        }
        if (immediatePair != null) {
            val cached = try {
                engine.cachedCompletion(
                    CompletionQuery(
                        raw = raw,
                        contextWeights = immediatePair.first,
                        contextCasing = immediatePair.second,
                        personalCompletions = personalCompletionSource,
                        lowMemory = lowRam,
                    )
                )
            } catch (_: RuntimeException) {
                null
            }
            if (cached != null) {
                if (cached.isNotEmpty()) publishSuggestions(cached)
                return
            }
        }
        suggestionTask = suggestionExecutor.submit suggestion@{
            if (generation != suggestionGeneration || Thread.currentThread().isInterrupted) {
                return@suggestion
            }
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
            val pair = if (cachedBoost != null) {
                cachedBoost to cachedCasing
            } else {
                val predictions = try {
                    predictionsFor(engine, languageId, context, predictionLimit)
                } catch (_: RuntimeException) {
                    emptyList()
                }
                val ngramNext = predictions.associate {
                    (
                        if (amharic) {
                            engine.normalize(it.word)
                        } else {
                            engine.normalize(it.word)
                        }
                        ) to it.weight
                }
                val predictionCasing = if (amharic) {
                    emptyMap()
                } else {
                    predictions
                        .filter { it.word != it.word.lowercase() }
                        .associate { engine.normalize(it.word) to it.word }
                }
                if (contextGeneration == composingContextGeneration) {
                    composingNgramBoost = ngramNext
                    composingPredictionCasing = predictionCasing
                }
                ngramNext to predictionCasing
            }
            if (generation != suggestionGeneration || Thread.currentThread().isInterrupted) {
                return@suggestion
            }
            val computed = try {
                engine.complete(
                    CompletionQuery(
                        raw = raw,
                        contextWeights = pair.first,
                        contextCasing = pair.second,
                        personalCompletions = personalCompletionSource,
                        lowMemory = lowRam
                    )
                )
            } catch (_: RuntimeException) {
                emptyList()
            }
            suggestionMainHandler.post {
                if (generation != suggestionGeneration) return@post
                if (
                    activeLanguageId != languageId ||
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
        languageId: LanguageId,
        capturedContext: CapturedNgramContext,
        limit: Int,
    ) {
        val context = capturedContext.context
        val request = PredictionRequestKey(languageId, context.prev2, context.prev1, limit)
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
        val pack = languageRegistry.installedPacks.firstOrNull { it.id == languageId } ?: return
        val engine = pack.suggestionEngine
        val amharic = pack.presentationCategory == LanguagePresentationCategory.AMHARIC
        val executor = predictionExecutor
        executor.queue.clear()
        val cookie = generation.toInt()
        SuggestionTrace.beginAsync("prediction_queue", cookie)
        SuggestionTrace.beginAsync("prediction_request", cookie)
        try {
            executor.execute {
                SuggestionTrace.endAsync("prediction_queue", cookie)
                val predictions = try {
                    val ngramPredictions = predictionsFor(engine, languageId, context, limit)
                    if (ngramPredictions.isEmpty()) {
                        // No trigram or bigram successor for this context: fall
                        // back to the most frequent dictionary words so the strip
                        // still offers next-word candidates instead of going blank.
                        engine.topFrequentWords(
                            if (amharic) {
                                AMHARIC_SUGGESTION_LIMIT
                            } else {
                                PREDICTION_FALLBACK_ENGLISH_LIMIT
                            }
                        )
                    } else {
                        ngramPredictions
                    }.map { it.word }
                    .let { words ->
                        if (!::personalDictionary.isInitialized) words
                        else if (amharic) {
                            val personal = personalDictionary.ranked(
                                languageId.value,
                                limit = limit
                            ).filter { w -> w.any { it in 'ሀ'..'፿' } }
                            (words + personal).distinct().take(limit)
                        } else {
                            val personal = personalDictionary.ranked(
                                languageId.value,
                                limit = limit
                            ).filter { '@' !in it }
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
                            activeLanguageId != languageId ||
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
        val languageId: LanguageId
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
        languageId: LanguageId,
        contextReader: (CharSequence?) -> LanguageContext
    ): CapturedNgramContext? {
        val key = ComposingContextKey(
            sessionGeneration = editorGateway.sessionGeneration,
            languageId = languageId
        )
        return composingContextCache.getOrCapture(key) {
            captureNgramContext(contextReader)
        }
    }
    /**
     * Bigram/trigram next-word predictions for the words preceding the cursor,
     * read from the field via [contextReader] and looked up in [ngrams]; empty
     * until the model loads or when the field gives no context. While
     * composing, the raw composing region before the cursor would read as a
     * hard boundary, so that prefix is stripped from the tail first -- and the
     * caret is always at the composing end, so the whole buffer is the prefix.
     */
    private fun captureNgramContext(
        contextReader: (CharSequence?) -> LanguageContext,
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
                        ResumableWord.LOOKBEHIND + composingPrefix.length
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
                context = contextReader(field),
                editorToken = read.token,
                contentIdentity = predictionIdentityFrom(surrounding)
                    ?: return@safeRun null
            )
        }
    }

    private fun captureNgramContextFromCursor(
        contextReader: (CharSequence?) -> LanguageContext,
        composingPrefix: String
    ): CapturedNgramContext? {
        val beforeRead = editorGateway.textBeforeCursor(
            maxOf(PREDICTION_IDENTITY_BEFORE, ResumableWord.LOOKBEHIND + composingPrefix.length),
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
            context = contextReader(field),
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
        engine: LanguageSuggestionEngine,
        languageId: LanguageId,
        context: LanguageContext,
        limit: Int,
    ): List<EngineSuggestion> {
        val prev1 = context.prev1 ?: return emptyList()
        predictionCache.get(languageId.value, context.prev2, prev1, limit)?.let { return it }
        if (!engine.isReady) return emptyList()
        val predictions = safeRun(emptyList()) {
            SuggestionTrace.section("ngram_query") {
                engine.predict(context.prev2, prev1, limit)
            }
        }
        predictionCache.put(languageId.value, context.prev2, prev1, limit, predictions)
        return predictions
    }

    private fun publishSuggestions(value: List<String>, arePredictions: Boolean = false) {
        safeApply {
            suggestionPublicationGeneration += 1
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

    @Volatile
    var suggestionPublicationGeneration: Long = 0
        private set

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

    final override var expandedSuggestionsVisible by mutableStateOf(false)
        private set

    override fun toggleExpandedSuggestions() {
        safeApply {
            if (voiceUiState.isVoiceMode) return@safeApply
            val hasRemaining = when (val s = nonVoiceSuggestionUiState) {
                is SuggestionUiState.WordCompletions -> s.words.size > SUGGESTION_STRIP_VISIBLE_LIMIT
                is SuggestionUiState.NextWordPredictions -> s.words.size > SUGGESTION_STRIP_VISIBLE_LIMIT
                is SuggestionUiState.EmailSuggestions -> s.chips.size > SUGGESTION_STRIP_VISIBLE_LIMIT
                else -> false
            }
            if (!hasRemaining) return@safeApply
            expandedSuggestionsVisible = !expandedSuggestionsVisible
        }
    }

    override fun hideExpandedSuggestions() {
        expandedSuggestionsVisible = false
    }

    override fun dismissSuggestions() {
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
        // strip, so the same generation is the honest answer. Its editor
        // identity still has to be refreshed: two contexts can legitimately
        // produce the same words, and the chips must target the newer caret
        // boundary even though their visible content did not change.
        if (sameVisibleChips(nonVoiceSuggestionUiState, state)) {
            val generation = when (val current = nonVoiceSuggestionUiState) {
                is SuggestionUiState.WordCompletions -> current.actionGeneration
                is SuggestionUiState.NextWordPredictions -> current.actionGeneration
                is SuggestionUiState.EmailSuggestions -> current.actionGeneration
                else -> return nonVoiceSuggestionUiState
            }
            publishedSuggestionAction = publishedSuggestionActionFor(
                state = nonVoiceSuggestionUiState,
                generation = generation
            )
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
        publishedSuggestionAction = publishedSuggestionActionFor(scoped, generation)
        return scoped
    }

    private fun publishedSuggestionActionFor(
        state: SuggestionUiState,
        generation: Long
    ): PublishedSuggestionAction? {
        val token = editorGateway.currentToken() ?: return null
        if (
            state !is SuggestionUiState.WordCompletions &&
            state !is SuggestionUiState.NextWordPredictions &&
            state !is SuggestionUiState.EmailSuggestions
        ) {
            return null
        }
        return PublishedSuggestionAction(
            generation = generation,
            editorToken = token,
            caretWord = currentCaretWord()?.word,
            predictionIdentity = if (state is SuggestionUiState.NextWordPredictions) {
                activePredictionRequest?.contentIdentity
            } else {
                null
            },
            languageId = activeLanguageId,
            emailField = isEmailField,
            privateField = isPrivateField,
            numberMode = isNumberMode
        )
    }

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

    fun openAppScreen(screen: String) {
        safeApply {
            startActivity(
                Intent(this, appShellActivityClass)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_APP_SHELL_DESTINATION, screen)
            )
        }
    }

    override fun openSettings() = openAppScreen(DESTINATION_SETTINGS)

    override fun openThemes() = openAppScreen(DESTINATION_THEMES)

    override fun openTypingGuide() = openAppScreen(DESTINATION_GUIDE)

    override fun openFeedback() = openAppScreen(DESTINATION_FEEDBACK)

    /** Clipboard entry point from the suggestion toolbar. Not wired up yet. */
    override fun onClipboardAction() {
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
    override fun onVoiceInput() {
        safeApply {
            if (isPrivateField) return@safeApply
            if (voiceUiState is VoiceUiState.Listening) {
                voiceInputController?.stop()
                finalizeVoiceComposing()
                publishVoiceUiState(VoiceUiState.Paused)
                return@safeApply
            }

            startVoiceRecognition()
        }
    }

    /** Back arrow in the voice toolbar: leave voice mode entirely. */
    override fun exitVoiceMode() {
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
                publishVoiceUiState(VoiceUiState.PermissionRequired)
                pendingVoiceStartAfterPermission = true
                val intent = Intent(this, VoicePermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = safeRun(false) {
                    startActivity(intent)
                    true
                }
                if (!opened) {
                    pendingVoiceStartAfterPermission = false
                    publishVoiceUiState(
                        VoiceUiState.Unavailable(VoiceErrorKind.PERMISSION.userMessage)
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
            voiceController().start(
                languageTag = activePack.voiceLocaleTag ?: activePack.localeTag,
                allowedLanguageTags = languageRegistry.installedPacks.mapNotNull {
                    it.voiceLocaleTag ?: it.localeTag
                }
            )
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
        publishVoiceUiState(VoiceUiState.Unavailable(VoiceErrorKind.UNKNOWN.userMessage))
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
     * Opens the emoji picker panel. Commits the composing word first, for
     * the same reason [toggleNumberMode] does -- an emoji must land AFTER
     * the word, never inside a composing region. The repository load is a
     * safe no-op if the sequential startup chain already started it; the
     * panel shows a loading state until [EmojiRepository.isReady].
     */
    override fun openEmojiPanel() {
        safeApply {
            if (isEmergencyMode) return@safeApply
            leaveVoiceModeForKeyboardInput()
            typingController.commitActiveWord()
            updateSuggestions()
            emojiRepository.loadAsync()
            showEmojiPanel = true
        }
    }

    override fun closeEmojiPanel() {
        safeApply {
            showEmojiPanel = false
            emojiSearchField = null
            if (isLowRam && ::emojiRepository.isInitialized) {
                emojiRepository.release()
            }
        }
    }

    /** Enters emoji search mode (query row + English key rows). */
    override fun openEmojiSearch() {
        safeApply {
            emojiSearchField = TextFieldValue()
        }
    }

    /** Leaves search mode back to the browse panel. */
    override fun closeEmojiSearch() {
        safeApply {
            emojiSearchField = null
        }
    }

    /** The search query row's clear (x) button: empty the query, stay in search. */
    override fun clearEmojiSearchQuery() {
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
    override fun updateEmojiSearchField(value: TextFieldValue) {
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
    override fun commitEmoji(emoji: String) {
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
    override fun recentEmojiSnapshot(): List<String> = safeRun(emptyList()) {
        recentEmojiStore.snapshot()
    }

    /**
     * A tone was picked in the long-press popup: remember it (persisted, and
     * mirrored into [selectedSkinTones] so the cell recomposes to show it).
     * Picking the base (yellow) clears the preference. The caller commits
     * the picked emoji separately via [commitEmoji].
     */
    override fun setSkinTone(base: String, variant: String) {
        safeApply {
            skinToneStore.set(base, variant)
            if (variant == base) selectedSkinTones.remove(base)
            else selectedSkinTones[base] = variant
        }
    }

    override fun toggleLanguage() {
        if (!::languageRegistry.isInitialized) return
        when (keyboardProduct.languageKeyBehavior) {
            LanguageKeyBehavior.SWITCH_TO_NEXT_INPUT_METHOD -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    switchToNextInputMethod(false)
                } else {
                    val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    manager.switchToNextInputMethod(window.window?.attributes?.token, false)
                }
            }
            LanguageKeyBehavior.SWITCH_INSTALLED_PACK -> {
                val currentIndex = languageRegistry.installedPacks.indexOfFirst {
                    it.id == activeLanguageId
                }
                val next = languageRegistry.installedPacks[
                    (currentIndex + 1).mod(languageRegistry.installedPacks.size)
                ]
                setLanguage(next.id)
                if (activeLanguageId == next.id) synchronizeFrameworkSubtype(next)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun synchronizeFrameworkSubtype(pack: LanguagePack) {
        safeApply {
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val inputMethod = manager.inputMethodList.firstOrNull {
                it.serviceInfo.packageName == packageName &&
                    it.serviceInfo.name == javaClass.name
            } ?: return@safeApply
            val subtype = (0 until inputMethod.subtypeCount)
                .map(inputMethod::getSubtypeAt)
                .firstOrNull { candidate ->
                    languageRegistry.findByLocaleTag(subtypeLocaleTag(candidate)) == pack.id
                }
                ?: return@safeApply
            if (
                languageRegistry.findByLocaleTag(
                    manager.currentInputMethodSubtype?.let(::subtypeLocaleTag)
                ) == pack.id
            ) {
                return@safeApply
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchInputMethod(inputMethod.id, subtype)
            } else {
                val token = window.window?.attributes?.token ?: return@safeApply
                manager.setInputMethodAndSubtype(token, inputMethod.id, subtype)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun subtypeLocaleTag(subtype: InputMethodSubtype): String? =
        subtype.languageTag.takeIf(String::isNotBlank)
            ?: subtype.locale.replace('_', '-').takeIf(String::isNotBlank)

    fun setLanguage(languageId: LanguageId) {
        if (
            !::languageRegistry.isInitialized ||
            languageId == activeLanguageId ||
            !languageRegistry.contains(languageId)
        ) {
            return
        }
        safeApply {
            val outgoing = activePack
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            typingController.onLanguageChange()
            clearComposingContextCache()
            val changed = languageRegistry.activate(languageId)
            if (!changed) return@safeApply
            activeLanguageId = languageRegistry.activeLanguageId
            KeyboardPrefs.setActiveLanguageId(this, activeLanguageId.value)
            if (!activePack.capabilities.supportsGeezNumbers &&
                numbersMode == NumbersMode.GEEZ_NUMBERS
            ) {
                numbersMode = NumbersMode.NUMBERS
            }
            outgoing.suggestionEngine.release()
            predictionCache.clear()
            pendingPredictionBoundary = null
            ensureActiveLanguageStoreLoaded("after_toggle_language")
            updateSuggestions()
            if (!activePack.capabilities.hasLetterCase && autoShiftArmed) {
                resetShift()
            } else if (activePack.capabilities.supportsAutoCapitalization) {
                maybeAutoCapitalize()
            }
            MemoryProbe.snapshot("after_toggle_language_sync")
        }
    }

    private fun ensureActiveLanguageStoreLoaded(snapshotPrefix: String) {
        if (!::languageRegistry.isInitialized || isEmergencyMode) return
        val engine = activeSuggestionEngine
        if (engine.isReady) return
        val targetLanguageId = activeLanguageId
        val loadGeneration = ++languageLoadGeneration
        publishSuggestionState(SuggestionUiState.LoadingLanguage)
        engine.loadAsync storeReady@{
            if (
                loadGeneration != languageLoadGeneration ||
                targetLanguageId != activeLanguageId
            ) {
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
    override fun toggleNumberMode() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            typingController.commitActiveWord()
            numbersMode = if (numbersMode == NumbersMode.OFF) NumbersMode.NUMBERS else NumbersMode.OFF
            updateSuggestions()
        }
    }

    override fun toggleSymbolsPage() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            numbersMode = when (numbersMode) {
                NumbersMode.NUMBERS -> if (activePack.alternateNumbersLayout != null) {
                    NumbersMode.GEEZ_NUMBERS
                } else {
                    NumbersMode.SYMBOLS
                }
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
    override fun openKeypad() {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            closeEmojiPanel()
            typingController.commitActiveWord()
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
    override fun toggleShift() {
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
            lastShiftTapUptimeMs = 0L
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
     * variation is accepted by [InputTypePolicy] (password/email/URI/filter --
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
            // InputTypePolicy's deny-list (password/email/URI/filter) plus the
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
            if (!activePack.capabilities.supportsAutoCapitalization || isNumberMode || !fieldAllowsAutoCap) {
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
    override fun onCharacter(value: String) {
        safeApply {
            val latin = value
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

    override fun onDeleteRepeatStart() {
        safeApply {
            suggestionRefreshGate.beginDeleteGesture()
        }
    }

    override fun onDeleteRepeatEnd() {
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
    override fun onDelete() {
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

    override fun commitText(value: String) {
        safeApply {
            leaveVoiceModeForKeyboardInput()
            cursorKnownAtFieldStart = false
            typingController.onCommitText(value)
            consumeShiftAfterCharacter()
            updateSuggestions()
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
    override fun onSpace() {
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
                val contextReader = activePack.contextReader
                contextReader("$before ")
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
    override fun onEnter() {
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

    override fun onSuggestionTapped(tap: SuggestionTap) {
        safeApply {
            val state = validatedSuggestionTapState(tap) ?: return@safeApply
            val action = publishedSuggestionAction ?: return@safeApply
            val word = tap.word
            val prediction = state is SuggestionUiState.NextWordPredictions
            leaveVoiceModeForKeyboardInput()
            val contextReader = activePack.contextReader
            val priorContext = currentBoundaryContext(activeLanguageId)
                ?: captureNgramContext(contextReader)
            val priorContextValue = priorContext?.context ?: LanguageContext(null, null)
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
            action.languageId != activeLanguageId ||
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
            val inputView = SharedKeyboardView()
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

    @Composable
    protected open fun optionalKeyboardUi(): OptionalKeyboardUi = OptionalKeyboardUi()

    private inner class SharedKeyboardView : AbstractComposeView(this@PackKeyboardService) {
        @Composable
        override fun Content() {
            CustomKeyboardTheme(
                isDarkTheme = isDarkTheme,
                palette = palette,
                isLowRam = isLowRam
            ) {
                KeyboardScreen(
                    service = this@PackKeyboardService,
                    optionalUi = optionalKeyboardUi()
                )
            }
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
            val orderedProviders = resolveConfiguredLanguagePackProviders(
                keyboardProduct.orderedInputLanguageIds,
                configuredLanguagePackProviders
            )
            val languageEnvironment = AndroidLanguagePackEnvironment(
                context = this,
                isLowMemoryDevice = isLowRam,
                onOutOfMemory = ::onLanguageEngineOutOfMemory,
                onFailure = ::onLanguageEngineFailure,
                onWarning = ::onLanguageEngineWarning
            )
            val configuredPacks = orderedProviders.map { provider ->
                provider.create(languageEnvironment).also { pack ->
                    require(pack.id == provider.languageId) {
                        "Language pack ${pack.id} does not match provider ${provider.languageId}"
                    }
                }
            }
            languageRegistry = LanguageRegistry(
                packs = configuredPacks,
                defaultLanguageId = LanguageId.of(
                    keyboardProduct.defaultInputLanguageId
                )
            )
            val installedIds = languageRegistry.installedPacks.map { it.id.value }.toSet()
            val savedLanguageId = KeyboardPrefs.activeLanguageId(
                this,
                installedIds,
                keyboardProduct.defaultInputLanguageId
            )
            activeLanguageId = languageRegistry.restore(savedLanguageId)
            KeyboardPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
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
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        safeApply {
            super.onConfigurationChanged(newConfig)
            refreshTheme(newConfig)
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (!::languageRegistry.isInitialized) return
        val languageTag = newSubtype?.let(::subtypeLocaleTag) ?: return
        val languageId = languageRegistry.findByLocaleTag(languageTag) ?: return
        setLanguage(languageId)
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
            voiceInputController?.stop()
            resetVoiceUi()
            closeEmojiPanel()
            hideExpandedSuggestions()
            resetShift()
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
            cursorKnownAtFieldStart =
                editorInfo?.initialSelStart == 0 && editorInfo.initialSelEnd == 0
            maybeAutoCapitalize(useKnownInitialCursor = true)
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        safeApply {
            idleReleaseHandler.removeCallbacks(idleRelease)
            super.onStartInputView(editorInfo, restarting)
            // Fresh (non-restarting) sessions feed the engagement counter behind
            // the one-time in-app review prompt (see ReviewPromptPolicy).
            if (!restarting) KeyboardPrefs.recordUsageSession(this)
            // A new input session means a new InputConnection -- any half-typed
            // word we were composing belongs to a field that's no longer
            // ours. Drop it silently rather than trying to commit into the
            // wrong destination.
            // A new session starts on the keyboard, not a stale replacement panel.
            closeEmojiPanel()
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
            idleReleaseHandler.removeCallbacks(idleRelease)
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
                currentBoundaryContext(activeLanguageId)
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
                if (::languageRegistry.isInitialized) {
                    languageRegistry.installedPacks.forEach {
                        it.suggestionEngine.clearCaches()
                    }
                }
                clearComposingContextCache()
                predictionCache.trimToSize(if (isLowRam) 8 else 24)
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
                if (::languageRegistry.isInitialized) activeSuggestionEngine.release()
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
            if (::languageRegistry.isInitialized) {
                languageRegistry.installedPacks.forEach { it.suggestionEngine.release() }
            }
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
