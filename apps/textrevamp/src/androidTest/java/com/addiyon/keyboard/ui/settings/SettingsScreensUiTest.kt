package com.addiyon.keyboard.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.TestAppHost
import com.addiyon.keyboard.TextRevampKeyboardProduct
import com.addiyon.keyboard.features.appshell.KEYBOARD_HEIGHT_HANDLE_TAG
import com.addiyon.keyboard.features.appshell.KeyboardAppShell
import com.addiyon.keyboard.features.appshell.KeyboardAppStatus
import com.addiyon.keyboard.features.appshell.KeyboardHeightCopy
import com.addiyon.keyboard.features.appshell.KeyboardHeightScreen
import com.addiyon.keyboard.features.appshell.KeyboardPreferencesCopy
import com.addiyon.keyboard.features.appshell.KeyboardStandardPreferencesScreen
import com.addiyon.keyboard.features.appshell.KeyboardThemePickerScreen
import com.addiyon.keyboard.features.appshell.R as AppShellR
import com.addiyon.keyboard.textRevampAppShellConfig
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MAX
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MIN
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreensUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun textRevampSettingsOmitsTypingGuideAndFeedbackOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            TestAppHost {
                KeyboardAppShell(
                    config = textRevampAppShellConfig(TextRevampKeyboardProduct),
                    status = KeyboardAppStatus(enabled = true, isDefault = true),
                    requestedDestinationId = null,
                    onDestinationRequestConsumed = {},
                    onFinish = {},
                    onOnboardingCompleted = {},
                    onSettingsShown = {}
                )
            }
        }

        listOf(
            context.getString(AppShellR.string.menu_themes),
            context.getString(AppShellR.string.menu_preferences),
            context.getString(AppShellR.string.menu_test_keyboard),
            context.getString(AppShellR.string.menu_personal_dictionary)
        ).forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
        compose.onNodeWithText(context.getString(AppShellR.string.menu_typing_guide))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(AppShellR.string.menu_feedback))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(context.getString(AppShellR.string.send_feedback)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(AppShellR.string.email)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(AppShellR.string.telegram)).assertIsDisplayed()
    }

    @Test
    fun standardPreferenceTogglesPersistToKeyboardPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setSoundOnKeypress(context, false)
        KeyboardPrefs.setNumberRow(context, false)

        compose.setContent {
            TestAppHost {
                KeyboardStandardPreferencesScreen(
                    copy = preferencesCopy(),
                    onBack = {},
                    onOpenKeyboardHeight = {}
                )
            }
        }

        compose.onNodeWithText("Sound on keypress").performClick()
        compose.onNodeWithText("Number row").performClick()
        compose.runOnIdle {
            assertTrue(KeyboardPrefs.soundOnKeypress(context))
            assertTrue(KeyboardPrefs.numberRow(context))
        }
    }

    @Test
    fun preferencesKeyboardHeightRowNavigates() {
        var opened = false
        compose.setContent {
            TestAppHost {
                KeyboardStandardPreferencesScreen(
                    copy = preferencesCopy(),
                    onBack = {},
                    onOpenKeyboardHeight = { opened = true }
                )
            }
        }
        compose.onNodeWithText("Keyboard height").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun keyboardHeightDragPersistsScaleToKeyboardPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setKeyboardHeightScale(context, KEYBOARD_HEIGHT_SCALE_DEFAULT)
        compose.setContent {
            TestAppHost {
                KeyboardHeightScreen(
                    copy = KeyboardHeightCopy(
                        title = "Keyboard height",
                        back = "Back",
                        hint = "Drag to resize",
                        reset = "Reset",
                        done = "Done",
                        previewLanguageName = "English"
                    ),
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag(KEYBOARD_HEIGHT_HANDLE_TAG).performTouchInput { swipeUp() }
        compose.runOnIdle {
            val stored = KeyboardPrefs.keyboardHeightScale(context)
            assertTrue(stored > KEYBOARD_HEIGHT_SCALE_DEFAULT)
            assertTrue(stored in KEYBOARD_HEIGHT_SCALE_MIN..KEYBOARD_HEIGHT_SCALE_MAX)
        }
    }

    @Test
    fun themePickerPersistsSelectedPaletteAndCallsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setPalette(context, KeyboardPalette.CLASSIC)
        var chosen = false
        var back = false
        compose.setContent {
            TestAppHost {
                KeyboardThemePickerScreen(
                    title = "Themes",
                    backContentDescription = "Back",
                    selectedPalette = KeyboardPrefs.palette(context),
                    onPaletteSelected = {
                        KeyboardPrefs.setPalette(context, it)
                        chosen = true
                    },
                    onBack = { back = true }
                )
            }
        }

        compose.onNodeWithText("Minimal").assertIsDisplayed()
        compose.onNodeWithText("Graphite").performClick()
        compose.runOnIdle {
            assertEquals(KeyboardPalette.GRAPHITE, KeyboardPrefs.palette(context))
            assertTrue(chosen)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    private fun preferencesCopy() = KeyboardPreferencesCopy(
        title = "Preferences",
        back = "Back",
        keyboardHeight = "Keyboard height",
        vibration = "Vibrate on keypress",
        sound = "Sound on keypress",
        numberRow = "Number row"
    )
}
