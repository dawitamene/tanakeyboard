package com.addiyon.keyboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.voice.VoiceUiState
import org.junit.Assert.assertEquals
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
    fun voiceModeShowsStatusExitAndStopControls() {
        var exited = false
        var voiceTapped = false

        compose.setContent {
            TestKeyboardHost {
                SuggestionArea(
                    suggestions = emptyList(),
                    isAmharic = true,
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

        compose.onNodeWithText("Listening…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit voice input").performClick()
        compose.runOnIdle { assertEquals(true, exited) }
        compose.onNodeWithContentDescription("Stop voice input").performClick()
        compose.runOnIdle { assertEquals(true, voiceTapped) }
    }
}
