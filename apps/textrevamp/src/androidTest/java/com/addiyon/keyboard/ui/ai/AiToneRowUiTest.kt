package com.addiyon.keyboard.ui.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.EnglishTextRevampStrings
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.asAiUiStrings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiToneRowUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tonesStayVisibleButDisabledUntilTheEditorContainsText() {
        var enabled by mutableStateOf(false)
        var selected: AiToneTab? = null

        compose.setContent {
            TestKeyboardHost {
                AiToneRow(
                    selectedTab = selected,
                    isLoading = false,
                    enabled = enabled,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onTabSelected = { selected = it }
                )
            }
        }

        compose.onNodeWithTag(AI_TONE_ACTION_ROW_TAG).assertHeightIsEqualTo(44.dp)
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, selected) }

        compose.runOnIdle { enabled = true }
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsEnabled()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).performClick()
        compose.runOnIdle { assertEquals(AiToneTab.Humanize, selected) }
    }
}
