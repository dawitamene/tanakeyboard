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
import com.addiyon.tanakeyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.tanakeyboard.ui.theme.TanaBrandTheme

private enum class ScreenKey {
    Onboarding, Settings, Manual, SoundVibration, TestKeyboard, About, Themes
}

class MainActivity : ComponentActivity() {

    // Set from the launch intent (and any later onNewIntent) when the keyboard
    // asks to open a specific screen. Observable so a re-launch while the app
    // is already running still navigates.
    private var screenRequest by mutableStateOf<String?>(null)

    // True once the app was opened FROM the keyboard toolbar. Drives the
    // "return to keyboard" affordances (a back button on Settings, auto-finish
    // after picking a theme) instead of the normal in-app navigation.
    private var fromKeyboard by mutableStateOf(false)

    // The screen the keyboard opened us onto. Back FROM that entry screen
    // returns to the keyboard (finish); back from screens navigated to
    // afterwards falls through to normal in-app navigation.
    private var keyboardEntryScreen by mutableStateOf<ScreenKey?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenRequest = intent.getStringExtra(EXTRA_OPEN_SCREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch window uses Theme.TanaKeyboard.Splash (branded logo
        // background) so the first frame is the splash. Swap to the real app
        // theme before Compose draws.
        setTheme(R.style.Theme_TanaKeyboard)
        super.onCreate(savedInstanceState)

        screenRequest = intent?.getStringExtra(EXTRA_OPEN_SCREEN)

        enableEdgeToEdge()

        setContent {
            // The app's own UI uses the fixed Tana brand palette and follows
            // the system light/dark setting. The selectable keyboard palette
            // only themes the keyboard itself (TanaKeyboardView reads the pref
            // independently), not this settings UI.
            ProvideAppLocalization {
            TanaBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                Scaffold { innerPadding ->
                    val status by rememberKeyboardStatus()
                    var screen by rememberSaveable {
                        mutableStateOf(
                            when {
                                // Not the default keyboard yet -> onboarding gate.
                                !KeyboardStatus.isDefault(this@MainActivity) ->
                                    ScreenKey.Onboarding
                                // Launched from the keyboard toolbar onto a
                                // specific screen: land there on the FIRST frame
                                // (behind the splash) so the target screen isn't
                                // preceded by a visible flash of Settings.
                                else -> screenForRequest(screenRequest) ?: ScreenKey.Settings
                            }
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
                        val target = screenForRequest(screenRequest)
                        if (target != null && status.isDefault) {
                            screen = target
                            fromKeyboard = true
                            keyboardEntryScreen = target
                            screenRequest = null
                        }
                    }

                    // Back handler: from the keyboard-entry screen, return to
                    // the keyboard; otherwise navigate back to Settings.
                    fun goBack(from: ScreenKey) {
                        if (fromKeyboard && from == keyboardEntryScreen) finish()
                        else screen = ScreenKey.Settings
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
                                onBack = { goBack(ScreenKey.Manual) }
                            )
                            ScreenKey.SoundVibration -> SoundVibrationScreen(
                                onBack = { goBack(ScreenKey.SoundVibration) }
                            )
                            ScreenKey.TestKeyboard -> TestKeyboardScreen(
                                onBack = { goBack(ScreenKey.TestKeyboard) }
                            )
                            ScreenKey.About -> AboutScreen(
                                onBack = { goBack(ScreenKey.About) }
                            )
                            ScreenKey.Themes -> ThemesScreen(
                                onBack = { goBack(ScreenKey.Themes) },
                                // Picking a theme when Themes was opened from the
                                // keyboard returns straight to it.
                                onPaletteChosen = {
                                    if (fromKeyboard && keyboardEntryScreen == ScreenKey.Themes) finish()
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Maps a keyboard-toolbar screen request (or null) to the screen it should
    // open. Unknown/absent requests return null (no keyboard-initiated nav).
    private fun screenForRequest(req: String?): ScreenKey? = when (req) {
        SCREEN_THEMES -> ScreenKey.Themes
        SCREEN_GUIDE -> ScreenKey.Manual
        SCREEN_SETTINGS -> ScreenKey.Settings
        else -> null
    }

    companion object {
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val SCREEN_SETTINGS = "SETTINGS"
        const val SCREEN_THEMES = "THEMES"
        const val SCREEN_GUIDE = "GUIDE"
    }
}
