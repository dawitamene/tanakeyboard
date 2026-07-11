package com.addiyon.keyboard.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.KeyboardStatusSnapshot
import com.addiyon.keyboard.TestAppHost
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
    fun settingsRowsCallNavigationCallbacksAndFeedbackSheetOpens() {
        var opened = ""

        compose.setContent {
            TestAppHost {
                SettingsScreen(
                    status = KeyboardStatusSnapshot(enabled = true, isDefault = true),
                    onOpenManual = { opened = "manual" },
                    onOpenSoundVibration = { opened = "preferences" },
                    onOpenTestKeyboard = { opened = "test" },
                    onOpenAbout = { opened = "about" },
                    onOpenThemes = { opened = "themes" }
                )
            }
        }

        compose.onNodeWithText("Themes").performClick()
        compose.runOnIdle { assertEquals("themes", opened) }
        compose.onNodeWithText("Typing Guide").performClick()
        compose.runOnIdle { assertEquals("manual", opened) }
        compose.onNodeWithText("Preferences").performClick()
        compose.runOnIdle { assertEquals("preferences", opened) }
        compose.onNodeWithText("Test Keyboard").performClick()
        compose.runOnIdle { assertEquals("test", opened) }
        compose.onNodeWithText("About").performClick()
        compose.runOnIdle { assertEquals("about", opened) }

        compose.onNodeWithText("Feedback").performClick()
        compose.onNodeWithText("Send feedback").assertIsDisplayed()
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Telegram").assertIsDisplayed()
    }

    @Test
    fun soundVibrationTogglesPersistToKeyboardPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setVibrateOnKeypress(context, false)
        KeyboardPrefs.setSoundOnKeypress(context, false)
        KeyboardPrefs.setNumberRow(context, false)

        compose.setContent {
            TestAppHost {
                SoundVibrationScreen(onBack = {})
            }
        }

        compose.onNodeWithText("Vibrate on keypress").performClick()
        compose.onNodeWithText("Sound on keypress").performClick()
        compose.onNodeWithText("Number row").performClick()

        compose.runOnIdle {
            assertTrue(KeyboardPrefs.vibrateOnKeypress(context))
            assertTrue(KeyboardPrefs.soundOnKeypress(context))
            assertTrue(KeyboardPrefs.numberRow(context))
        }
    }

    @Test
    fun themesScreenPersistsSelectedPaletteAndCallsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KeyboardPrefs.setPalette(context, KeyboardPalette.CLASSIC)
        var chosen = false
        var back = false

        compose.setContent {
            TestAppHost {
                ThemesScreen(
                    onBack = { back = true },
                    onPaletteChosen = { chosen = true }
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
}
