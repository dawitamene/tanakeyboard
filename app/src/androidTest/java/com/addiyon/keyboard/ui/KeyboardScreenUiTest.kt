package com.addiyon.keyboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.TestKeyboardHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setKeyboard(service: AddiyonKeyboardService = AddiyonKeyboardService()) {
        compose.setContent {
            TestKeyboardHost {
                KeyboardScreen(service)
            }
        }
    }

    @Test
    fun rendersAmharicKeyboardWithToolbarControlsAndFidelPreviews() {
        setKeyboard()

        compose.onNodeWithTag(KeyboardTestTags.KEY_SHIFT).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_SPACE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_ENTER).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_NUMBER_TOGGLE).assertIsDisplayed()
        compose.onNodeWithTag(KeyboardTestTags.KEY_LANGUAGE_TOGGLE).assertIsDisplayed()
        compose.onNodeWithText("አማርኛ", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("፣", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("q", useUnmergedTree = true).assertIsDisplayed()
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
}
