package com.addiyon.keyboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.features.appshell.FeedbackTestTags
import com.addiyon.keyboard.features.appshell.KeyboardAboutScreen
import com.addiyon.keyboard.features.appshell.KeyboardFeedbackOptions
import com.addiyon.keyboard.features.appshell.KeyboardGuideSection
import com.addiyon.keyboard.features.appshell.KeyboardTestScreen
import com.addiyon.keyboard.features.appshell.KeyboardTextGuideScreen
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
    fun guideScreenUsesConfiguredSectionsAndBackAction() {
        var back = false
        compose.setContent {
            TestAppHost {
                KeyboardTextGuideScreen(
                    title = "Typing Guide",
                    backContentDescription = "Back",
                    sections = listOf(
                        KeyboardGuideSection("AI rephrase", "Refine your words"),
                        KeyboardGuideSection("Smart suggestions", "Tap a suggestion to complete a word.")
                    ),
                    onBack = { back = true }
                )
            }
        }

        compose.onNodeWithText("Typing Guide").assertIsDisplayed()
        compose.onNodeWithText("AI rephrase").assertIsDisplayed()
        compose.onNodeWithText("Smart suggestions").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun testKeyboardScreenShowsScratchFieldAndBackAction() {
        var back = false
        compose.setContent {
            TestAppHost {
                KeyboardTestScreen(
                    title = "Test Keyboard",
                    backContentDescription = "Back",
                    placeholder = "Start writing",
                    onBack = { back = true }
                )
            }
        }

        compose.onNodeWithText("Test Keyboard").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("hello")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun aboutScreenShowsConfiguredIdentityAndBackAction() {
        var back = false
        compose.setContent {
            TestAppHost {
                KeyboardAboutScreen(
                    title = "About",
                    backContentDescription = "Back",
                    productName = "TextRevamp AI Keyboard",
                    versionText = "Version 1.0",
                    description = "English writing keyboard",
                    privacyPolicyLabel = "Privacy policy",
                    madeBy = "Made by Addiyon",
                    onPrivacyPolicy = {},
                    onBack = { back = true },
                    logo = {}
                )
            }
        }

        compose.onNodeWithText("TextRevamp AI Keyboard").assertIsDisplayed()
        compose.onNodeWithText("Made by Addiyon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun feedbackOptionsCallTheirHandlers() {
        var selected = ""
        compose.setContent {
            TestAppHost {
                Column {
                    KeyboardFeedbackOptions(
                        telegramLabel = "Telegram",
                        emailLabel = "Email",
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
