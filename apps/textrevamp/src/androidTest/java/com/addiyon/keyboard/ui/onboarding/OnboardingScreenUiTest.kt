package com.addiyon.keyboard.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.res.stringResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.features.appshell.KeyboardAppStatus
import com.addiyon.keyboard.features.appshell.KeyboardOnboardingCopy
import com.addiyon.keyboard.features.appshell.KeyboardOnboardingScreen
import com.addiyon.keyboard.features.appshell.R as AppShellR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun disabledKeyboardShowsActivationStep() {
        compose.setContent {
            TestAppHost {
                onboardingScreen(
                    status = KeyboardAppStatus(enabled = false, isDefault = false)
                )
            }
        }

        compose.onNodeWithText("Activate TextRevamp AI Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Step 1").assertIsDisplayed()
        compose.onNodeWithText("Open keyboard settings").assertIsDisplayed()
    }

    @Test
    fun enabledButNotDefaultKeyboardShowsPickerStep() {
        compose.setContent {
            TestAppHost {
                onboardingScreen(
                    status = KeyboardAppStatus(enabled = true, isDefault = false)
                )
            }
        }

        compose.onNodeWithText("Enable TextRevamp AI Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Step 2").assertIsDisplayed()
        compose.onNodeWithText("Switch keyboard").assertIsDisplayed()
    }

    @Test
    fun defaultKeyboardShowsAllSetThenCallsDone() {
        compose.mainClock.autoAdvance = false
        var done = false
        compose.setContent {
            TestAppHost {
                onboardingScreen(
                    status = KeyboardAppStatus(enabled = true, isDefault = true),
                    onDone = { done = true }
                )
            }
        }

        compose.onNodeWithText("All set!").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1700)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(done) }
        compose.mainClock.autoAdvance = true
    }

    @androidx.compose.runtime.Composable
    private fun onboardingScreen(
        status: KeyboardAppStatus,
        onDone: () -> Unit = {}
    ) {
        KeyboardOnboardingScreen(
            status = status,
            copy = KeyboardOnboardingCopy(
                activateTitle = stringResource(AppShellR.string.keyboard_activate_title),
                activateDescription = stringResource(AppShellR.string.keyboard_activate_description),
                openSettings = stringResource(AppShellR.string.open_keyboard_settings),
                activateFootnote = stringResource(AppShellR.string.keyboard_activate_footnote),
                enableTitle = stringResource(AppShellR.string.keyboard_enable_title),
                enableDescription = stringResource(AppShellR.string.keyboard_enable_description),
                switchKeyboard = stringResource(AppShellR.string.switch_keyboard),
                stepFormat = stringResource(AppShellR.string.keyboard_setup_step),
                allSet = stringResource(AppShellR.string.keyboard_all_set),
                allSetSubtitle = stringResource(AppShellR.string.keyboard_all_set_subtitle)
            ),
            header = {},
            onOpenSettings = {},
            onShowPicker = {},
            onDone = onDone
        )
    }
}
