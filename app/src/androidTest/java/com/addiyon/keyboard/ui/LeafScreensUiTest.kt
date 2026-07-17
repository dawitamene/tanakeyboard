package com.addiyon.keyboard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.ui.feedback.FeedbackTestTags
import com.addiyon.keyboard.ui.feedback.FeedbackOptions
import com.addiyon.keyboard.ui.manual.ManualScreen
import com.addiyon.keyboard.ui.settings.AboutScreen
import com.addiyon.keyboard.ui.settings.TestKeyboardScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeafScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun manualScreenRendersAndFiltersTheTransliterationTable() {
        compose.setContent {
            TestAppHost {
                ManualScreen(onBack = {})
            }
        }

        compose.onNodeWithText("Typing Guide").assertIsDisplayed()
        compose.onNodeWithText("Search: he, sh, ላ ...").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("sh")
        compose.onAllNodesWithText("ሸ").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("sh").onFirst().assertIsDisplayed()
    }

    @Test
    fun testKeyboardScreenShowsScratchFieldAndBackAction() {
        var back = false

        compose.setContent {
            TestAppHost {
                TestKeyboardScreen(onBack = { back = true })
            }
        }

        compose.onNodeWithText("Test Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Type \"selam\" → ሰላም").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun aboutScreenShowsIdentityPrivacyAndBackAction() {
        var back = false

        compose.setContent {
            TestAppHost {
                AboutScreen(onBack = { back = true })
            }
        }

        compose.onNodeWithText("Addiyon Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Made by Addiyon").assertIsDisplayed()
        compose.onNodeWithText("Your privacy", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun feedbackOptionsCallTheirHandlers() {
        var selected = ""

        compose.setContent {
            TestAppHost {
                Column {
                    FeedbackOptions(
                        onTelegram = { selected = "telegram" },
                        onEmail = { selected = "email" }
                    )
                }
            }
        }

        compose.onNodeWithTag(FeedbackTestTags.TELEGRAM, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("telegram", selected) }
        compose.onNodeWithTag(FeedbackTestTags.EMAIL, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("email", selected) }
    }
}
