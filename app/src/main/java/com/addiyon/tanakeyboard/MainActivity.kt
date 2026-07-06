package com.addiyon.tanakeyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.addiyon.tanakeyboard.ui.manual.ManualScreen
import com.addiyon.tanakeyboard.ui.onboarding.OnboardingScreen
import com.addiyon.tanakeyboard.ui.settings.AboutScreen
import com.addiyon.tanakeyboard.ui.settings.SettingsScreen
import com.addiyon.tanakeyboard.ui.settings.SoundVibrationScreen
import com.addiyon.tanakeyboard.ui.settings.TestKeyboardScreen
import com.addiyon.tanakeyboard.ui.settings.ThemesScreen
import com.addiyon.tanakeyboard.ui.theme.CustomKeyboardTheme

private enum class ScreenKey {
    Onboarding, Settings, Manual, SoundVibration, TestKeyboard, About, Themes
}

class MainActivity : ComponentActivity() {

    // Set from the launch intent (and any later onNewIntent) when the keyboard
    // asks to open a specific screen. Observable so a re-launch while the app
    // is already running still navigates.
    private var screenRequest by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenRequest = intent.getStringExtra(EXTRA_OPEN_SCREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenRequest = intent?.getStringExtra(EXTRA_OPEN_SCREEN)

        enableEdgeToEdge()

        setContent {
            // The app's own UI always uses the default (Classic) palette and
            // follows the system light/dark setting. The selectable keyboard
            // palette only themes the keyboard itself (TanaKeyboardView reads
            // the pref independently), not this settings UI.
            CustomKeyboardTheme(isDarkTheme = isSystemInDarkTheme()) {
                Scaffold { innerPadding ->
                    val status by rememberKeyboardStatus()
                    var screen by rememberSaveable {
                        mutableStateOf(
                            if (KeyboardStatus.isDefault(this@MainActivity)) ScreenKey.Settings
                            else ScreenKey.Onboarding
                        )
                    }

                    // The onboarding steps are the gate to the app: until Tana
                    // is the default keyboard, always show them (step 1 if not
                    // enabled, step 2 if enabled-but-not-default -- OnboardingScreen
                    // picks the phase from status). Becoming default is what lets
                    // the onboarding's own All-set flow hand off to Settings.
                    LaunchedEffect(status.isDefault) {
                        if (!status.isDefault) screen = ScreenKey.Onboarding
                    }

                    // Honor a screen requested by the keyboard toolbar (only
                    // once Tana is default, i.e. past onboarding), then consume
                    // it so returning to the app later lands on Settings.
                    LaunchedEffect(screenRequest, status.isDefault) {
                        val req = screenRequest
                        if (req != null && status.isDefault) {
                            screen = when (req) {
                                SCREEN_THEMES -> ScreenKey.Themes
                                else -> ScreenKey.Settings
                            }
                            screenRequest = null
                        }
                    }

                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (screen) {
                            ScreenKey.Onboarding -> OnboardingScreen(
                                status = status,
                                onDone = { screen = ScreenKey.Settings }
                            )
                            ScreenKey.Settings -> SettingsScreen(
                                status = status,
                                onOpenManual = { screen = ScreenKey.Manual },
                                onOpenSoundVibration = { screen = ScreenKey.SoundVibration },
                                onOpenTestKeyboard = { screen = ScreenKey.TestKeyboard },
                                onOpenAbout = { screen = ScreenKey.About },
                                onOpenThemes = { screen = ScreenKey.Themes }
                            )
                            ScreenKey.Manual -> ManualScreen(
                                onBack = { screen = ScreenKey.Settings }
                            )
                            ScreenKey.SoundVibration -> SoundVibrationScreen(
                                onBack = { screen = ScreenKey.Settings }
                            )
                            ScreenKey.TestKeyboard -> TestKeyboardScreen(
                                onBack = { screen = ScreenKey.Settings }
                            )
                            ScreenKey.About -> AboutScreen(
                                onBack = { screen = ScreenKey.Settings }
                            )
                            ScreenKey.Themes -> ThemesScreen(
                                onBack = { screen = ScreenKey.Settings }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val SCREEN_SETTINGS = "SETTINGS"
        const val SCREEN_THEMES = "THEMES"
    }
}
