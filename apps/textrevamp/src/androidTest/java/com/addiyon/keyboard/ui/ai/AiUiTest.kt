package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.EnglishTextRevampStrings
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.asAiUiStrings
import com.addiyon.keyboard.ai.AiInput
import com.addiyon.keyboard.ai.AiPreferences
import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.ai.AiResult
import com.addiyon.keyboard.ai.AiSnapshot
import com.addiyon.keyboard.ai.AiSource
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ai.todayIso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onDismiss = {},
                        onTabSelected = { tone ->
                            state = state.copy(selectedTab = tone, isLoading = true)
                        },
                        onCopyVariant = {},
                        onReplaceVariant = {},
                        onOpenDashboard = {}
                    )
                }
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiToneHumanize).assertIsNotSelected()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneProfessional).assertIsNotSelected()
        compose.onNodeWithText("TextRevamp AI").assertDoesNotExist()
        compose.onNodeWithTag(AI_PANEL_BACK_ACTION_TAG)
            .assertHeightIsEqualTo(44.dp)
            .assertWidthIsEqualTo(44.dp)
        AiToneTab.DefaultTabs.forEach { tone ->
            compose.onNodeWithTag(
                aiPanelToneIconTag(tone),
                useUnmergedTree = true
            ).fetchSemanticsNode()
        }
        val backBounds = compose.onNodeWithTag(AI_PANEL_BACK_ACTION_TAG)
            .fetchSemanticsNode().boundsInRoot
        val toneBounds = compose.onNodeWithTag(
            aiPanelToneIconTag(AiToneTab.Humanize),
            useUnmergedTree = true
        )
            .fetchSemanticsNode().boundsInRoot
        assertTrue(toneBounds.left >= backBounds.right)
        assertTrue(toneBounds.center.y in backBounds.top..backBounds.bottom)
        compose.onNodeWithText(EnglishTextRevampStrings.aiSelectToneMessage).assertIsDisplayed()
        compose.onNodeWithTag(AI_PANEL_SELECT_TONE_ICON_TAG).assertIsDisplayed()
        compose.onNodeWithTag(AI_PANEL_SKELETON_TAG).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneProfessional).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneProfessional).assertIsSelected()
        compose.onNodeWithTag(AI_PANEL_SKELETON_TAG).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiLoading).assertDoesNotExist()
    }

    @Test
    fun fixedHeightResultShowsThreeStackedVersionsWithoutExtraControls() {
        var copied: AiStrength? = null
        var replaced: AiStrength? = null
        val input = AiInput(
            text = "A short message to refine.",
            wordCount = 5,
            source = AiSource.Sentence,
            snapshot = AiSnapshot(1L)
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
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onDismiss = {},
                        onTabSelected = {},
                        onCopyVariant = { copied = it },
                        onReplaceVariant = { replaced = it },
                        onOpenDashboard = {}
                    )
                }
            }
        }

        variants.forEach { (strength, result) ->
            compose.onNodeWithTag(aiPanelVariantTag(strength)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(result.text).assertIsDisplayed()
        }
        compose.onNodeWithText(EnglishTextRevampStrings.aiInputLabel).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiCopy).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiReplace).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiStyleLabel).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiStrengthBalanced).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiStrengthStrong).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiQuotaFormat.format(788, 800)).assertDoesNotExist()
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
            TestKeyboardHost {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AiPanel(
                        state = AiUiState(isVisible = true),
                        strings = EnglishTextRevampStrings.asAiUiStrings(),
                        onDismiss = { dismissed = true },
                        onTabSelected = {},
                        onCopyVariant = {},
                        onReplaceVariant = {},
                        onOpenDashboard = {}
                    )
                }
            }
        }

        compose.onNodeWithText(EnglishTextRevampStrings.aiRephraseTitle).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiRephraseSubtitle).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiToneLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(EnglishTextRevampStrings.aiBack).assertIsDisplayed()
        compose.onNodeWithContentDescription(EnglishTextRevampStrings.aiCloseDescription).assertDoesNotExist()
        compose.onNodeWithText(EnglishTextRevampStrings.aiEmptyTitle).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiEmptyMessage).assertIsDisplayed()
        compose.onNodeWithText(EnglishTextRevampStrings.aiEmptyAction).assertIsDisplayed()
        compose.onNodeWithTag(AI_PANEL_EMPTY_ACTION_TAG).performClick()
        compose.runOnIdle {
            assertEquals(true, dismissed)
        }
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
        compose.onNodeWithText("40,000 of 50,000 tokens remaining").assertIsDisplayed()
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
}
