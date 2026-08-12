package com.addiyon.keyboard.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.emoji.EmojiRepository
import com.addiyon.keyboard.language.amharic.AmharicLanguagePack
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.english.EnglishLanguagePack
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.model.onShiftTap
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.ui.keys.CharacterKeyPresentation
import com.addiyon.keyboard.ui.keys.LanguageKeyPresentation
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setKeyboard(controller: AmharicKeyboardUiController = controller()) {
        compose.setContent {
            CustomKeyboardTheme(isDarkTheme = false, palette = KeyboardPalette.CLASSIC) {
                KeyboardScreen(controller)
            }
        }
    }

    private fun controller() = AmharicKeyboardUiController(context)

    @Test
    fun rendersAmharicKeyboardWithToolbarControlsAndFidelPreviews() {
        setKeyboard()

        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Emoji").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice input").assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_SHIFT).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_SPACE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_ENTER).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_NUMBER_TOGGLE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_LANGUAGE_TOGGLE).assertIsDisplayed()
        compose.onNodeWithText("አማርኛ", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("፣", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("q", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("ቅ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun shiftChangesKeyLabelsThenACharacterTapConsumesOneShotShift() {
        setKeyboard()

        compose.onNodeWithTag(KeyboardTestTags.KEY_SHIFT).performClick()
        compose.onNodeWithText("Q", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(KeyboardTestTags.character("Q")).performClick()
        compose.onNodeWithText("q", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun amharicFidelPreviewsReturnAfterEnglishLanguageRoundTrip() {
        val controller = controller()
        setKeyboard(controller)

        compose.onNodeWithText("ቅ", useUnmergedTree = true).assertIsDisplayed()

        compose.runOnIdle {
            controller.activePack = EnglishLanguagePack(NoOpLanguageSuggestionEngine)
            controller.shiftState = ShiftState.SHIFT
        }
        compose.onNodeWithText("ቅ", useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle {
            controller.activePack = AmharicLanguagePack(NoOpLanguageSuggestionEngine)
        }
        compose.onNodeWithText("ጥ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun amharicNumberAndSymbolsTogglesCycleThroughAllNumericPages() {
        setKeyboard()

        compose.onNodeWithTag(KeyboardTestTags.KEY_NUMBER_TOGGLE).performClick()
        compose.onNodeWithText("ABC", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("፩፪", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(KeyboardTestTags.KEY_SYMBOLS_TOGGLE).performClick()
        compose.onNodeWithText("፩", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("፲", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(KeyboardTestTags.KEY_SYMBOLS_TOGGLE).performClick()
        compose.onNodeWithText("π", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("<>/", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(KeyboardTestTags.KEY_SYMBOLS_TOGGLE).performClick()
        compose.onNodeWithText("©", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("123", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(KeyboardTestTags.KEY_NUMBER_TOGGLE).performClick()
        compose.onNodeWithText("q", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun disposingKeyboardWhileCharacterPreviewIsPressedDoesNotCrash() {
        val showKeyboard = mutableStateOf(true)
        val controller = controller()
        compose.setContent {
            CustomKeyboardTheme(isDarkTheme = false, palette = KeyboardPalette.CLASSIC) {
                if (showKeyboard.value) {
                    KeyboardScreen(controller)
                }
            }
        }

        compose.onNodeWithText("q", useUnmergedTree = true)
            .performTouchInput { down(center) }
        compose.runOnIdle { showKeyboard.value = false }
        compose.waitForIdle()
    }
}

private class AmharicKeyboardUiController(context: Context) : KeyboardController {
    override var activePack by mutableStateOf<LanguagePack>(
        AmharicLanguagePack(NoOpLanguageSuggestionEngine)
    )
    override val latinSearchLayout
        get() = activePack.letterLayout
    override val languageKeyPresentation: LanguageKeyPresentation
        get() = LanguageKeyPresentation.cycling(
            labels = listOf("ሀለ", "EN", "OR"),
            activeIndex = when (activePack.id.value) {
                "am-ET" -> 0
                "en-US" -> 1
                else -> 2
            }
        )
    override var numbersMode by mutableStateOf(NumbersMode.OFF)
    override var shiftState by mutableStateOf(ShiftState.OFF)
    override val enterAction = EnterAction.NEWLINE
    override val isEmailField = false
    override val isPrivateField = false
    override val isLowRam = false
    override val isDarkTheme = false
    override val isShiftEnabled: Boolean
        get() = shiftState != ShiftState.OFF
    override val isNumberMode: Boolean
        get() = numbersMode != NumbersMode.OFF
    override val keyboardHeightScale = 1f
    override val showNumberRow = false
    override val vibrateOnKeypress = false
    override val soundOnKeypress = false
    override val suggestionUiState = SuggestionUiState.Toolbar
    override val expandedSuggestionsVisible = false
    override var showEmojiPanel by mutableStateOf(false)
    override val emojiSearchQuery: String? = null
    override val emojiRepository = EmojiRepository(context)
    override val emojiSearchField: TextFieldValue? = null
    override val selectedSkinTones: Map<String, String> = emptyMap()

    override fun characterKeyPresentation(value: String): CharacterKeyPresentation {
        val transformed = activePack.typingProfile.transformStandalone(value)
        val punctuation = value == "," || value == "."
        return when {
            transformed == value -> CharacterKeyPresentation(value)
            punctuation -> CharacterKeyPresentation(
                primaryText = transformed,
                secondaryText = value,
                longPressCommit = value,
                secondaryFontSize = 14.sp
            )
            else -> CharacterKeyPresentation(
                primaryText = value,
                secondaryText = activePack.cornerPreview(value)
            )
        }
    }

    override fun onCharacter(value: String) {
        if (shiftState == ShiftState.SHIFT) shiftState = ShiftState.OFF
    }

    override fun commitText(value: String) = Unit

    override fun toggleShift() {
        shiftState = shiftState.onShiftTap(isDoubleTap = false)
    }

    override fun onDeleteRepeatStart() = Unit
    override fun onDeleteRepeatEnd() = Unit
    override fun onDelete() = Unit
    override fun onSpace() = Unit
    override fun toggleLanguage() = Unit
    override fun onEnter() = Unit

    override fun toggleNumberMode() {
        numbersMode = if (numbersMode == NumbersMode.OFF) {
            NumbersMode.NUMBERS
        } else {
            NumbersMode.OFF
        }
    }

    override fun toggleSymbolsPage() {
        numbersMode = when (numbersMode) {
            NumbersMode.NUMBERS -> NumbersMode.GEEZ_NUMBERS
            NumbersMode.GEEZ_NUMBERS -> NumbersMode.SYMBOLS
            NumbersMode.SYMBOLS -> NumbersMode.MORE_SYMBOLS
            NumbersMode.MORE_SYMBOLS -> NumbersMode.NUMBERS
            NumbersMode.KEYPAD -> NumbersMode.NUMBERS
            NumbersMode.OFF -> NumbersMode.OFF
        }
    }

    override fun openKeypad() {
        numbersMode = NumbersMode.KEYPAD
    }

    override fun onSuggestionTapped(tap: SuggestionTap) = Unit
    override fun hideExpandedSuggestions() = Unit
    override fun dismissSuggestions() = Unit
    override fun toggleExpandedSuggestions() = Unit
    override fun openSettings() = Unit
    override fun openThemes() = Unit
    override fun openTypingGuide() = Unit
    override fun openFeedback() = Unit
    override fun onClipboardAction() = Unit

    override fun openEmojiPanel() {
        showEmojiPanel = true
    }

    override fun onVoiceInput() = Unit
    override fun exitVoiceMode() = Unit
    override fun recentEmojiSnapshot(): List<String> = emptyList()

    override fun closeEmojiPanel() {
        showEmojiPanel = false
    }

    override fun openEmojiSearch() = Unit
    override fun closeEmojiSearch() = Unit
    override fun clearEmojiSearchQuery() = Unit
    override fun updateEmojiSearchField(value: TextFieldValue) = Unit
    override fun commitEmoji(emoji: String) = Unit
    override fun setSkinTone(base: String, variant: String) = Unit
}

private object NoOpLanguageSuggestionEngine : LanguageSuggestionEngine {
    override val languageId = "am-ET"
    override val isReady = true
    override val isLoading = false

    override fun loadAsync(onReady: () -> Unit) = onReady()
    override fun complete(query: CompletionQuery): List<String> = emptyList()
    override fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion> =
        emptyList()
    override fun topFrequentWords(limit: Int): List<EngineSuggestion> = emptyList()
    override fun normalize(word: String): String = word
    override fun clearCaches() = Unit
    override fun release() = Unit
}
