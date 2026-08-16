package com.addiyon.keyboard.ui.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.EnglishTextRevampStrings
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.CustomTone
import com.addiyon.keyboard.asAiUiStrings
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    useKeyboardTopRowSpacing = true,
                    onTabSelected = { selected = it }
                )
            }
        }

        compose.onNodeWithTag(AI_TONE_ACTION_ROW_TAG).assertHeightIsEqualTo(56.dp)
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, selected) }

        compose.runOnIdle { enabled = true }
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsEnabled()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).performClick()
        compose.runOnIdle { assertEquals(AiToneTab.Humanize, selected) }
    }

    @Test
    fun fixGrammarThenCasualLeadTheToneRow() {
        compose.setContent {
            TestKeyboardHost {
                AiToneRow(
                    selectedTab = null,
                    isLoading = false,
                    enabled = true,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    useKeyboardTopRowSpacing = true,
                    onTabSelected = {}
                )
            }
        }

        val grammar = compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar)
            .fetchSemanticsNode().boundsInRoot
        val casual = compose.onNodeWithText(EnglishTextRevampStrings.aiToneCasual)
            .fetchSemanticsNode().boundsInRoot
        val humanize = compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(grammar.left < casual.left)
        assertTrue(casual.left < humanize.left)
    }

    @Test
    fun responseBackButtonOverlaysTheFullWidthToneScroller() {
        var dismissed = false
        compose.setContent {
            TestKeyboardHost {
                AiToneRow(
                    selectedTab = AiToneTab.FixGrammar,
                    isLoading = false,
                    enabled = true,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = { dismissed = true },
                    useKeyboardTopRowSpacing = true,
                    onTabSelected = {}
                )
            }
        }

        val back = compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG)
            .fetchSemanticsNode().boundsInRoot
        val grammar = compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar)
            .fetchSemanticsNode().boundsInRoot
        val minimumGap = with(compose.density) { AddiyonSpacing.xs.toPx() }
        assertTrue(grammar.left - back.right >= minimumGap)

        val scrollDistance = with(compose.density) { AddiyonSizes.minimumTouchTarget.toPx() }
        compose.onNodeWithTag(AI_TONE_ACTION_ROW_TAG).performTouchInput {
            down(center)
            moveTo(Offset(center.x - scrollDistance, center.y))
            up()
        }
        compose.waitForIdle()

        val scrolledBack = compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG)
            .fetchSemanticsNode().boundsInRoot
        val scrolledGrammar = compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(back.left, scrolledBack.left, 0.5f)
        assertTrue(scrolledGrammar.left < grammar.left)
        assertTrue(scrolledGrammar.left < scrolledBack.right)

        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun customTonesRenderAfterDefaultsWithTheAddChipLastAndBothAreClickable() {
        val custom = CustomTone(
            id = "c1",
            title = "Poetic",
            instruction = "Make it more poetic"
        )
        var selectedCustom: CustomTone? = null
        var added = false
        compose.setContent {
            TestKeyboardHost {
                AiToneRow(
                    selectedTab = null,
                    customTones = listOf(custom),
                    selectedCustomToneId = null,
                    isLoading = false,
                    enabled = true,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    useKeyboardTopRowSpacing = true,
                    onTabSelected = {},
                    onCustomToneSelected = { selectedCustom = it },
                    onAddCustomTone = { added = true }
                )
            }
        }

        val humanize = compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize)
            .fetchSemanticsNode().boundsInRoot
        val customChip = compose.onNodeWithText("Poetic")
            .fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag(AI_TONE_ADD_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(humanize.left < customChip.left)
        assertTrue(customChip.left < add.left)

        compose.onNodeWithTag(aiCustomToneChipTag(custom)).performClick()
        compose.runOnIdle { assertEquals(custom, selectedCustom) }
        compose.onNodeWithTag(AI_TONE_ADD_TAG).performClick()
        compose.runOnIdle { assertTrue(added) }
    }

    @Test
    fun addCustomChipStaysEnabledWhileTonesAreDisabled() {
        compose.setContent {
            TestKeyboardHost {
                AiToneRow(
                    selectedTab = null,
                    customTones = emptyList(),
                    selectedCustomToneId = null,
                    isLoading = false,
                    enabled = false,
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    useKeyboardTopRowSpacing = true,
                    onTabSelected = {},
                    onCustomToneSelected = {},
                    onAddCustomTone = {}
                )
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsNotEnabled()
        compose.onNodeWithText(EnglishTextRevampStrings.aiAddCustomTone).assertIsEnabled()
    }
}
