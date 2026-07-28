package com.addiyon.keyboard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.voice.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionAreaUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toolbarActionsAreVisibleAndClickableWhenThereAreNoSuggestions() {
        var action = ""

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = emptyList(),
                    isAmharic = true,
                    onTap = {},
                    onOpenSettings = { action = "settings" },
                    onOpenThemes = { action = "themes" },
                    onOpenGuide = { action = "guide" },
                    onFeedback = { action = "feedback" },
                    onAi = {},
                    onClipboard = {},
                    onEmoji = { action = "emoji" },
                    onVoice = { action = "voice" }
                )
            }
        }

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.runOnIdle { assertEquals("settings", action) }
        compose.onNodeWithContentDescription("Emoji").performClick()
        compose.runOnIdle { assertEquals("emoji", action) }
        compose.onNodeWithContentDescription("Typing guide").assertIsDisplayed()
        compose.onNodeWithContentDescription("Feedback").assertIsDisplayed()
        compose.onNodeWithContentDescription("Themes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voice input").performClick()
        compose.runOnIdle { assertEquals("voice", action) }
    }

    @Test
    fun suggestionTapCallsTheProvidedHandler() {
        var tapped: String? = null

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = listOf("hello", "help", "helium"),
                    isAmharic = false,
                    onTap = { tapped = it },
                    onOpenSettings = {},
                    onOpenThemes = {},
                    onOpenGuide = {},
                    onFeedback = {},
                    onAi = {},
                    onClipboard = {},
                    onEmoji = {}
                )
            }
        }

        compose.onNodeWithText("help", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("help", tapped) }
        compose.onNodeWithContentDescription("Voice input").assertIsDisplayed()
    }

    @Test
    fun twoAndThreeAmharicSuggestionsUseEqualCenteredSlots() {
        val allWords = listOf("ሀ", "ሁ", "ሂ")
        var visibleWords by mutableStateOf(allWords.take(2))

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = visibleWords,
                    isAmharic = true,
                    onTap = {},
                    onOpenSettings = {},
                    onOpenThemes = {},
                    onOpenGuide = {},
                    onFeedback = {},
                    onAi = {},
                    onClipboard = {},
                    onEmoji = {}
                )
            }
        }

        for (count in 2..3) {
            compose.runOnIdle {
                visibleWords = allWords.take(count)
            }
            compose.waitForIdle()

            val stripBounds = compose
                .onNodeWithTag(AMHARIC_SUGGESTION_STRIP_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
            visibleWords.forEachIndexed { index, word ->
                val wordCenter = compose
                    .onNodeWithText(word)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                    .x
                val expectedCenter =
                    stripBounds.left + stripBounds.width * (index + 0.5f) / count

                assertTrue(
                    "$count Amharic suggestions should use equal slots",
                    kotlin.math.abs(wordCenter - expectedCenter) <= 2f
                )
            }
        }
    }

    @Test
    fun oneFourAndFiveFittingAmharicSuggestionsStayCentered() {
        val allWords = listOf("ሀ", "ሁ", "ሂ", "ሃ", "ሄ")
        var visibleWords by mutableStateOf(allWords.take(1))

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = visibleWords,
                    isAmharic = true,
                    onTap = {},
                    onOpenSettings = {},
                    onOpenThemes = {},
                    onOpenGuide = {},
                    onFeedback = {},
                    onAi = {},
                    onClipboard = {},
                    onEmoji = {}
                )
            }
        }

        for (count in listOf(1, 4, 5)) {
            compose.runOnIdle {
                visibleWords = allWords.take(count)
            }
            compose.waitForIdle()

            val stripBounds = compose
                .onNodeWithTag(AMHARIC_SUGGESTION_STRIP_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
            val firstBounds = compose
                .onNodeWithText(visibleWords.first())
                .fetchSemanticsNode()
                .boundsInRoot
            val lastBounds = compose
                .onNodeWithText(visibleWords.last())
                .fetchSemanticsNode()
                .boundsInRoot
            val groupCenter = (firstBounds.left + lastBounds.right) / 2f

            assertTrue(
                "$count Amharic suggestions should be centered",
                kotlin.math.abs(groupCenter - stripBounds.center.x) <= 2f
            )
        }
    }

    @Test
    fun overflowingAmharicSuggestionsRemainHorizontallyScrollable() {
        val words = listOf("ሀ", "ሁ", "ሂ", "ሃ", "ሄ", "ህ").map { it.repeat(10) }

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = words,
                    isAmharic = true,
                    onTap = {},
                    onOpenSettings = {},
                    onOpenThemes = {},
                    onOpenGuide = {},
                    onFeedback = {},
                    onAi = {},
                    onClipboard = {},
                    onEmoji = {}
                )
            }
        }

        val stripBounds = compose
            .onNodeWithTag(AMHARIC_SUGGESTION_STRIP_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val lastNode = compose.onNodeWithText(words.last())
        val scrollConfig = compose
            .onNodeWithTag(AMHARIC_SUGGESTION_STRIP_TAG)
            .fetchSemanticsNode()
            .config

        assertTrue(stripBounds.width > 0f)
        assertTrue(HorizontalScrollAxisRange in scrollConfig)
        assertTrue(scrollConfig[HorizontalScrollAxisRange].maxValue() > 0f)
        lastNode.performScrollTo().assertIsDisplayed()
    }

    @Test
    fun voiceModeUsesLanguageAwareStatusAndControls() {
        var exited = false
        var voiceTapped = false
        var amharic by mutableStateOf(true)

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = emptyList(),
                    isAmharic = amharic,
                    onTap = {},
                    onOpenSettings = {},
                    onOpenThemes = {},
                    onOpenGuide = {},
                    onFeedback = {},
                    onAi = {},
                    onClipboard = {},
                    onEmoji = {},
                    voiceUiState = VoiceUiState.Listening,
                    onVoice = { voiceTapped = true },
                    onExitVoice = { exited = true }
                )
            }
        }

        compose.onNodeWithText("በማዳመጥ ላይ...").assertIsDisplayed()
        compose.runOnIdle {
            amharic = false
        }
        compose.onNodeWithText("Listening…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit voice input").performClick()
        compose.runOnIdle { assertEquals(true, exited) }
        compose.onNodeWithContentDescription("Stop voice input").performClick()
        compose.runOnIdle { assertEquals(true, voiceTapped) }
    }
}
