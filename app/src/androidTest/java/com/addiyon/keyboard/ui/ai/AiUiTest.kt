package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ai.AiInput
import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.ai.AiResult
import com.addiyon.keyboard.ai.AiSnapshot
import com.addiyon.keyboard.ai.AiSource
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ai.todayIso
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.i18n.AmharicStrings
import com.addiyon.keyboard.ui.i18n.EnglishStrings
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toneStartsUnselectedAndSelectionShowsLoading() {
        var state by mutableStateOf(
            AiUiState(
                isVisible = true,
                input = AiInput("A short message to refine.", 5, AiSource.Sentence, null)
            )
        )

        compose.setContent {
            TestKeyboardHost {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AiPanel(
                        state = state,
                        onDismiss = {},
                        onTabSelected = { tone ->
                            state = state.copy(selectedTab = tone, isLoading = true)
                        },
                        onCopyVariant = {},
                        onReplaceVariant = {},
                        onSendLink = {}
                    )
                }
            }
        }

        compose.onNodeWithText(EnglishStrings.aiToneHumanize).assertIsNotSelected()
        compose.onNodeWithText(EnglishStrings.aiToneProfessional).assertIsNotSelected()
        compose.onNodeWithText(EnglishStrings.aiSelectToneMessage).assertIsDisplayed()
        compose.onNodeWithTag(AI_PANEL_SKELETON_TAG).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(EnglishStrings.aiToneProfessional).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(EnglishStrings.aiToneProfessional).assertIsSelected()
        compose.onNodeWithTag(AI_PANEL_SKELETON_TAG).assertIsDisplayed()
        compose.onNodeWithText(EnglishStrings.aiLoading).assertDoesNotExist()
    }

    @Test
    fun fixedHeightResultShowsThreeStackedVersionsWithoutExtraControls() {
        var copied: AiStrength? = null
        var replaced: AiStrength? = null
        val input = AiInput(
            text = "A short message to refine.",
            wordCount = 5,
            source = AiSource.Sentence,
            snapshot = AiSnapshot(0, 25, 1L, 1L)
        )
        val variants = mapOf(
            AiStrength.Subtle to AiResult("A subtle rewrite.", "Rephrase", "subtle"),
            AiStrength.Balanced to AiResult("A balanced rewrite.", "Rephrase", "balanced"),
            AiStrength.Strong to AiResult("A strong rewrite.", "Rephrase", "strong")
        )
        val baseState = AiUiState(
            isVisible = true,
            input = input,
            selectedTab = AiToneTab.Humanize,
            quota = AiQuota(12, 800, 788, "2026-08-08"),
            variantResults = variants
        )

        compose.setContent {
            TestKeyboardHost {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AiPanel(
                        state = baseState,
                        onDismiss = {},
                        onTabSelected = {},
                        onCopyVariant = { copied = it },
                        onReplaceVariant = { replaced = it },
                        onSendLink = {}
                    )
                }
            }
        }

        variants.forEach { (strength, result) ->
            compose.onNodeWithTag(aiPanelVariantTag(strength)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(result.text).assertIsDisplayed()
        }
        compose.onNodeWithText(EnglishStrings.aiInputLabel).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiCopy).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiReplace).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiStyleLabel).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiStrengthBalanced).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiStrengthStrong).assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiQuotaFormat.format(788, 800)).assertDoesNotExist()
        compose.onNodeWithTag(aiPanelCopyTag(AiStrength.Subtle)).performScrollTo().performClick()
        compose.onNodeWithTag(aiPanelReplaceTag(AiStrength.Strong)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AiStrength.Subtle, copied)
            assertEquals(AiStrength.Strong, replaced)
        }
    }

    @Test
    fun emptyStateExplainsHowToContinueAndReturnsToKeyboard() {
        var dismissed = false
        compose.setContent {
            CompositionLocalProvider(LocalAppStrings provides AmharicStrings) {
                TestKeyboardHost {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        AiPanel(
                            state = AiUiState(isVisible = true),
                            onDismiss = { dismissed = true },
                            onTabSelected = {},
                            onCopyVariant = {},
                            onReplaceVariant = {},
                            onSendLink = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithText(AmharicStrings.aiRephraseTitle).assertDoesNotExist()
        compose.onNodeWithText(AmharicStrings.aiRephraseSubtitle).assertDoesNotExist()
        compose.onNodeWithText(AmharicStrings.aiToneLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(AmharicStrings.back).assertIsDisplayed()
        compose.onNodeWithContentDescription(AmharicStrings.aiCloseDescription).assertDoesNotExist()
        compose.onNodeWithText(AmharicStrings.aiEmptyTitle).assertIsDisplayed()
        compose.onNodeWithText(AmharicStrings.aiEmptyMessage).assertIsDisplayed()
        compose.onNodeWithText(AmharicStrings.aiEmptyAction).assertIsDisplayed()
        compose.onNodeWithTag(AI_PANEL_EMPTY_ACTION_TAG).performClick()
        compose.runOnIdle {
            assertEquals(true, dismissed)
        }
    }

    @Test
    fun dashboardUsesBrandedPageShell() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setAiJwt(context, null)

        compose.setContent {
            TestAppHost {
                AiDashboardContent(onBack = {}, onLogout = {}, onSwitchToAuth = {})
            }
        }
        compose.onNodeWithText("AI account").assertIsDisplayed()
        compose.onNodeWithText("Your AI workspace").assertIsDisplayed()
        compose.onNodeWithText("Sign in to Addiyon AI").assertIsDisplayed()
    }

    @Test
    fun signedInDashboardShowsRemainingPercentageAndSignsOutWithoutStatusCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setAiJwt(context, "test-token")
        KeyboardPrefs.setAiEmail(context, "test@addiyon.com")
        KeyboardPrefs.setAiQuotaDay(context, todayIso())
        KeyboardPrefs.setAiDailyLimit(context, 50)
        KeyboardPrefs.setAiWordsUsedToday(context, 10)
        var loggedOut = false

        compose.setContent {
            TestAppHost {
                AiDashboardContent(
                    onBack = {},
                    onLogout = { loggedOut = true },
                    onSwitchToAuth = {}
                )
            }
        }

        compose.onNodeWithText("80%").assertIsDisplayed()
        compose.onNodeWithText("40 of 50 requests remaining").assertIsDisplayed()
        compose.onNodeWithText("Signed in").assertDoesNotExist()
        compose.onNodeWithText(EnglishStrings.aiSignOut).performClick()
        compose.runOnIdle {
            assertEquals(true, loggedOut)
            assertEquals(null, KeyboardPrefs.aiJwt(context))
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
}
