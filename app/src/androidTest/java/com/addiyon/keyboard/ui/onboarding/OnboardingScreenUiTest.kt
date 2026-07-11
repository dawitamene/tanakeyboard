package com.addiyon.keyboard.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.KeyboardStatusSnapshot
import com.addiyon.keyboard.TestAppHost
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
                OnboardingScreen(
                    status = KeyboardStatusSnapshot(enabled = false, isDefault = false),
                    onDone = {}
                )
            }
        }

        compose.onNodeWithText("Activate Addiyon Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Step 1").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun enabledButNotDefaultKeyboardShowsPickerStep() {
        compose.setContent {
            TestAppHost {
                OnboardingScreen(
                    status = KeyboardStatusSnapshot(enabled = true, isDefault = false),
                    onDone = {}
                )
            }
        }

        compose.onNodeWithText("Enable Addiyon Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Step 2").assertIsDisplayed()
        compose.onNodeWithText("Switch Keyboard").assertIsDisplayed()
    }

    @Test
    fun defaultKeyboardShowsAllSetThenCallsDone() {
        compose.mainClock.autoAdvance = false
        var done = false

        compose.setContent {
            TestAppHost {
                OnboardingScreen(
                    status = KeyboardStatusSnapshot(enabled = true, isDefault = true),
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
}
