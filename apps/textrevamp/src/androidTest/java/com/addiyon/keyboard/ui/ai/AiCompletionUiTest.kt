package com.addiyon.keyboard.ui.ai

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.EnglishTextRevampStrings
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ai.AiCompletionCapture
import com.addiyon.keyboard.ai.AiCompletionContextKey
import com.addiyon.keyboard.ai.AiCompletionSnapshot
import com.addiyon.keyboard.ai.AiCompletionUiState
import com.addiyon.keyboard.asAiUiStrings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiCompletionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readyCompletionHasFixedHeightAndSeparateActions() {
        var inserted = false
        var dismissed = false
        val prefix = "I will send the draft"
        val state = AiCompletionUiState.Ready(
            completion = " before the meeting.",
            capture = AiCompletionCapture(
                prefix = prefix,
                contextKey = AiCompletionContextKey(1, prefix),
                snapshot = AiCompletionSnapshot(1)
            )
        )

        compose.setContent {
            TestKeyboardHost {
                AiCompletionBar(
                    state = state,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onInsert = { inserted = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        compose.onNodeWithTag(AI_COMPLETION_BAR_TAG)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(44.dp)
        compose.onNodeWithText("before the meeting.").assertIsDisplayed()
        compose.onNodeWithTag(AI_COMPLETION_INSERT_TAG).performClick()
        compose.onNodeWithTag(AI_COMPLETION_DISMISS_TAG).performClick()
        compose.runOnIdle {
            assertTrue(inserted)
            assertTrue(dismissed)
        }
    }
}
