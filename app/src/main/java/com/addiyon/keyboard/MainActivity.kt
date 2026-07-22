package com.addiyon.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.addiyon.keyboard.review.ReviewPromptPolicy
import com.google.android.play.core.review.ReviewManagerFactory
import com.addiyon.keyboard.ui.manual.ManualScreen
import com.addiyon.keyboard.ui.onboarding.OnboardingScreen
import com.addiyon.keyboard.ui.settings.AboutScreen
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.settings.SettingsScreen
import com.addiyon.keyboard.ui.settings.SoundVibrationScreen
import com.addiyon.keyboard.ui.settings.TestKeyboardScreen
import com.addiyon.keyboard.ui.settings.ThemesScreen
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme

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
        // The launch window uses Theme.AddiyonKeyboard.Splash (branded logo
        // background) so the first frame is the splash. Swap to the real app
        // theme before Compose draws.
        setTheme(R.style.Theme_AddiyonKeyboard)
        super.onCreate(savedInstanceState)

        screenRequest = intent?.getStringExtra(EXTRA_OPEN_SCREEN)

        enableEdgeToEdge()

        setContent {
            // The app's own UI uses the fixed Addiyon brand palette and follows
            // the system light/dark setting. The selectable keyboard palette
            // only themes the keyboard itself (AddiyonKeyboardView reads the pref
            // independently), not this settings UI.
            ProvideAppLocalization {
            AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
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

                    // The onboarding steps are the gate to the app: until Addiyon
                    // is the default keyboard, always show them (step 1 if not
                    // enabled, step 2 if enabled-but-not-default -- OnboardingScreen
                    // picks the phase from status). Becoming default is what lets
                    // the onboarding's own All-set flow hand off to Settings.
                    LaunchedEffect(status.isDefault) {
                        if (!status.isDefault) screen = ScreenKey.Onboarding
                    }

                    // Honor a screen requested by the keyboard toolbar (only
                    // once Addiyon is default, i.e. past onboarding), then consume
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

                    // Wires the device/gesture back button into the same
                    // in-app navigation goBack() already implements for the
                    // on-screen back buttons. Without this, system back falls
                    // through to the Activity's default behavior (finish) no
                    // matter which screen is showing -- e.g. opening Themes
                    // from Settings and pressing back would close the whole
                    // app instead of returning to Settings. Disabled on
                    // Settings/Onboarding so back there keeps the normal
                    // exit-app behavior.
                    BackHandler(enabled = screen != ScreenKey.Settings && screen != ScreenKey.Onboarding) {
                        goBack(screen)
                    }

                    // Smart in-app review prompt: opening the app's home
                    // screen after sustained keyboard use is a natural,
                    // non-interrupting moment to ask (never mid-onboarding,
                    // never mid-typing -- an IME can't host the dialog anyway).
                    LaunchedEffect(screen) {
                        if (screen == ScreenKey.Settings) maybeRequestReview()
                    }

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

    /**
     * Launches the Play in-app review flow once the user has enough keyboard
     * sessions behind them (see [ReviewPromptPolicy]). One-shot: marked
     * prompted before launching, because the Play API is quota-limited and
     * deliberately gives no signal about whether its dialog actually showed —
     * there is nothing to retry on. Failures (no Play Store, emulator) are
     * ignored; the user still has the explicit Rate button in Settings.
     */
    private fun maybeRequestReview() {
        val eligible = ReviewPromptPolicy.shouldPrompt(
            sessions = KeyboardPrefs.usageSessions(this),
            alreadyPrompted = KeyboardPrefs.reviewPrompted(this)
        )
        if (!eligible) return
        KeyboardPrefs.setReviewPrompted(this)
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            if (!isFinishing && !isDestroyed) manager.launchReviewFlow(this, info)
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
