package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.EnglishTextRevampStrings
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ai.AiPreferences
import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.ai.AiResult
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ai.CustomTone
import com.addiyon.keyboard.ai.CustomToneColor
import com.addiyon.keyboard.ai.CustomToneIcon
import com.addiyon.keyboard.ai.todayIso
import com.addiyon.keyboard.asAiUiStrings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toneSelectionKeepsTheRowVisibleWithoutRenderingALoadingSurface() {
        var state by mutableStateOf(AiUiState())

        compose.setContent {
            TestKeyboardHost {
                Column {
                    AiToneRow(
                        selectedTab = state.selectedTab,
                        isLoading = state.isLoading,
                        enabled = true,
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        useKeyboardTopRowSpacing = true,
                        onTabSelected = { state = state.copy(selectedTab = it, isLoading = true) }
                    )
                }
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar).assertIsNotSelected()
        compose.onNodeWithTag(AI_RESULTS_OVERLAY_TAG).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar).performClick()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneFixGrammar).assertIsSelected()
        compose.onNodeWithTag(AI_TONE_ACTION_ROW_TAG).assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag(AI_RESULTS_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun firstRequestLoadingShowsResponseSkeletonAndBackControl() {
        var dismissed = false
        compose.setContent {
            TestKeyboardHost {
                Column {
                    AiToneRow(
                        selectedTab = AiToneTab.FixGrammar,
                        isLoading = true,
                        enabled = true,
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onBack = { dismissed = true },
                        useKeyboardTopRowSpacing = true,
                        onTabSelected = {}
                    )
                    AiResultsOverlay(
                        state = AiUiState(
                            selectedTab = AiToneTab.FixGrammar,
                            isLoading = true
                        ),
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onReplaceVariant = {}
                    )
                }
            }
        }

        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(AI_RESULTS_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(AI_RESULTS_SKELETON_TAG).assertIsDisplayed()
        repeat(3) { index ->
            compose.onNodeWithTag(aiResultSkeletonCardTag(index)).assertIsDisplayed()
        }
        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test
    fun resultsOverlayDisplaysThreeStackedChoicesThatReplaceCapturedText() {
        var replaced: AiStrength? = null
        var dismissed = false
        val variants = mapOf(
            AiStrength.Subtle to AiResult("A subtle rewrite.", "Rephrase", "subtle"),
            AiStrength.Balanced to AiResult("A balanced rewrite.", "Rephrase", "balanced"),
            AiStrength.Strong to AiResult("A strong rewrite.", "Rephrase", "strong")
        )

        compose.setContent {
            TestKeyboardHost {
                Column {
                    AiToneRow(
                        selectedTab = AiToneTab.FixGrammar,
                        isLoading = false,
                        enabled = true,
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onBack = { dismissed = true },
                        useKeyboardTopRowSpacing = true,
                        onTabSelected = {}
                    )
                    AiResultsOverlay(
                        state = AiUiState(
                            selectedTab = AiToneTab.FixGrammar,
                            variantResults = variants
                        ),
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onReplaceVariant = { replaced = it }
                    )
                }
            }
        }

        compose.onAllNodesWithText(EnglishTextRevampStrings.aiToneFixGrammar)
            .assertCountEquals(1)
        variants.forEach { (strength, result) ->
            compose.onNodeWithTag(aiResponseVariantTag(strength)).assertIsDisplayed()
            compose.onNodeWithText(result.text).assertIsDisplayed()
        }
        compose.onNodeWithTag(aiResponseVariantTag(AiStrength.Strong)).performClick()
        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).performClick()
        compose.runOnIdle {
            assertEquals(AiStrength.Strong, replaced)
            assertEquals(true, dismissed)
        }
    }

    @Test
    fun changingToneFromResultsKeepsTheOverlayAndShowsThreeSkeletonCards() {
        var dismissed = false
        compose.setContent {
            TestKeyboardHost {
                Column {
                    AiToneRow(
                        selectedTab = AiToneTab.Casual,
                        isLoading = true,
                        enabled = true,
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onBack = { dismissed = true },
                        useKeyboardTopRowSpacing = true,
                        onTabSelected = {}
                    )
                    AiResultsOverlay(
                        state = AiUiState(
                            selectedTab = AiToneTab.Casual,
                            isLoading = true,
                            isResultLoading = true
                        ),
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onReplaceVariant = {}
                    )
                }
            }
        }

        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(AI_RESULTS_OVERLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(AI_RESULTS_SKELETON_TAG).assertIsDisplayed()
        repeat(3) { index ->
            compose.onNodeWithTag(aiResultSkeletonCardTag(index)).assertIsDisplayed()
        }

        compose.onNodeWithTag(AI_RESULTS_DISMISS_TAG).performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test
    fun dashboardUsesBrandedPageShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AiPreferences(context)
        preferences.setJwt(null)

        compose.setContent {
            TestAppHost {
                AiDashboardContent(
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    accountStore = preferences,
                    onBack = {},
                    onLogout = {},
                    onSwitchToAuth = {}
                )
            }
        }
        compose.onNodeWithText("AI account").assertIsDisplayed()
        compose.onNodeWithText("Your AI workspace").assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiSignInAction).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiPhraseCompletionTitle).assertDoesNotExist()
    }

    @Test
    fun signedInDashboardShowsRemainingPercentageAndSignsOutWithoutStatusCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AiPreferences(context)
        preferences.setJwt("test-token")
        preferences.setEmail("test@addiyon.com")
        preferences.saveQuota(AiQuota(10_000, 50_000, 40_000, todayIso()))
        var loggedOut = false

        compose.setContent {
            TestAppHost {
                AiDashboardContent(
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    accountStore = preferences,
                    onBack = {},
                    onLogout = { loggedOut = true },
                    onSwitchToAuth = {}
                )
            }
        }

        compose.onNodeWithText("80%").assertIsDisplayed()
        compose.onNodeWithText("40,000 of 50,000 tokens remaining").assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiUsageRemaining).assertIsDisplayed()
        compose.onNodeWithText("Signed in").assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiSignOut).performClick()
        compose.runOnIdle {
            assertEquals(true, loggedOut)
            assertEquals(null, preferences.jwt())
        }
    }

    @Test
    fun authUsesBrandedPageShell() {
        compose.setContent {
            TestAppHost {
                AiAuthBottomSheet(
                    email = "",
                    onEmailChanged = {},
                    password = "",
                    onPasswordChanged = {},
                    name = "",
                    onNameChanged = {},
                    otp = "",
                    onOtpChanged = {},
                    sending = false,
                    message = null,
                    step = AuthStep.Email,
                    onContinueWithGoogle = {},
                    onContinueEmail = {},
                    onLogin = {},
                    onSendOtp = {},
                    onVerifyOtp = {},
                    onRegister = {},
                    onBackToEmail = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Sign in to AI").assertIsDisplayed()
        compose.onNodeWithText("Make every message sound like you").assertIsDisplayed()
        compose.onNodeWithTag(AI_AUTH_GOOGLE_ACTION_TAG).assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag(AI_AUTH_EMAIL_FIELD_TAG).assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag(AI_AUTH_PRIMARY_ACTION_TAG).assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun customToneScreenSavesANewToneWithTitleInstructionEmojiAndColor() {
        var savedTitle: String? = null
        var savedInstruction: String? = null
        var savedIcon: String? = null
        var savedColor: String? = null
        compose.setContent {
            TestAppHost {
                AiCustomToneContent(
                    customTones = listOf(
                        CustomTone("c1", "Poetic", "Make it more poetic")
                    ),
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = {},
                    onSave = { title, instruction, icon, color ->
                        savedTitle = title
                        savedInstruction = instruction
                        savedIcon = icon
                        savedColor = color
                    },
                    onUpdate = { _, _, _, _, _ -> },
                    onRemove = {}
                )
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneTitle).assertIsDisplayed()
        compose.onNodeWithText("Poetic").assertIsDisplayed()
        compose.onNodeWithText("Make it more poetic").assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneTitleLabel).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneInstructionLabel).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneIconLabel).assertIsDisplayed()
        compose.onNodeWithTag(AI_CUSTOM_TONE_TITLE_FIELD_TAG)
            .performTextInput("Romantic")
        compose.onNodeWithTag(AI_CUSTOM_TONE_FIELD_TAG)
            .performTextInput("Make it sound romantic")
        compose.onNodeWithTag(aiCustomToneIconTag(CustomToneIcon.STAR)).performClick()
        compose.onNodeWithTag(aiCustomToneColorTag(CustomToneColor.ROSE)).performClick()
        compose.onNodeWithTag(AI_CUSTOM_TONE_SAVE_TAG).performClick()
        compose.runOnIdle {
            assertEquals("Romantic", savedTitle)
            assertEquals("Make it sound romantic", savedInstruction)
            assertEquals(CustomToneIcon.STAR, savedIcon)
            assertEquals(CustomToneColor.ROSE, savedColor)
        }
    }

    @Test
    fun customToneScreenEditsAnExistingTone() {
        var updated: CustomTone? = null
        compose.setContent {
            TestAppHost {
                AiCustomToneContent(
                    customTones = listOf(
                        CustomTone("c1", "Poetic", "Make it more poetic")
                    ),
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = {},
                    onSave = { _, _, _, _ -> },
                    onUpdate = { id, title, instruction, icon, color ->
                        updated = CustomTone(id, title, instruction, icon, color)
                    },
                    onRemove = {}
                )
            }
        }

        compose.onNodeWithTag(aiCustomToneEditTag("c1")).performClick()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneEditHeading).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneSaveChanges).assertIsDisplayed()
        compose.onNodeWithTag(AI_CUSTOM_TONE_TITLE_FIELD_TAG).performTextClearance()
        compose.onNodeWithTag(AI_CUSTOM_TONE_TITLE_FIELD_TAG).performTextInput("Romantic")
        compose.onNodeWithTag(AI_CUSTOM_TONE_SAVE_TAG).performClick()
        compose.runOnIdle {
            assertEquals(
                CustomTone(
                    "c1",
                    "Romantic",
                    "Make it more poetic",
                    CustomToneIcon.Default,
                    CustomToneColor.Default
                ),
                updated
            )
        }
    }

    @Test
    fun customToneScreenCancelsEditingAndReturnsToAddMode() {
        compose.setContent {
            TestAppHost {
                AiCustomToneContent(
                    customTones = listOf(
                        CustomTone("c1", "Poetic", "Make it more poetic")
                    ),
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = {},
                    onSave = { _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _ -> },
                    onRemove = {}
                )
            }
        }

        compose.onNodeWithTag(aiCustomToneEditTag("c1")).performClick()
        compose.onNodeWithTag(AI_CUSTOM_TONE_CANCEL_TAG).performClick()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneNewHeading).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneSave).assertIsDisplayed()
    }

    @Test
    fun customToneScreenRemovesSavedTones() {
        var removed: String? = null
        compose.setContent {
            TestAppHost {
                AiCustomToneContent(
                    customTones = listOf(
                        CustomTone("c1", "Poetic", "Make it more poetic")
                    ),
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = {},
                    onSave = { _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _ -> },
                    onRemove = { removed = it }
                )
            }
        }

        compose.onNodeWithTag(aiCustomToneItemTag("c1")).assertIsDisplayed()
        compose.onNodeWithTag(aiCustomToneRemoveTag("c1")).performClick()
        compose.runOnIdle { assertEquals("c1", removed) }
    }

    @Test
    fun customToneScreenShowsEmptyStateAndRejectsBlankFields() {
        var saved = false
        compose.setContent {
            TestAppHost {
                AiCustomToneContent(
                    customTones = emptyList(),
                    strings = EnglishTextRevampStrings.asAiUiStrings(),
                    onBack = {},
                    onSave = { _, _, _, _ -> saved = true },
                    onUpdate = { _, _, _, _, _ -> },
                    onRemove = {}
                )
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneEmpty).assertIsDisplayed()
        compose.onNodeWithTag(AI_CUSTOM_TONE_SAVE_TAG).performClick()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCustomToneError).assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, saved) }
    }
}
