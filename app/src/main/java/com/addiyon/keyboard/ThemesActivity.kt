package com.addiyon.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.addiyon.keyboard.ui.settings.ThemesScreen
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme

/**
 * Standalone host for the theme picker when it's opened from the keyboard
 * toolbar. It renders [ThemesScreen] directly as its own first frame, so the
 * keyboard's "Themes" button lands straight on the picker instead of briefly
 * flashing [MainActivity]'s home screen on the way. Both back and picking a
 * palette just finish, returning to whatever the user was typing in.
 */
class ThemesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContent {
                ProvideAppLocalization {
                    AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                        ThemesScreen(
                            onBack = { finish() },
                            onPaletteChosen = { finish() }
                        )
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            com.addiyon.keyboard.SafeLog.e(oom, "ThemesActivity onCreate OOM")
            renderFallback()
        } catch (t: Throwable) {
            com.addiyon.keyboard.SafeLog.e(t, "ThemesActivity onCreate")
            renderFallback()
        }
    }

    private fun renderFallback() {
        try {
            setContent {
                androidx.compose.material3.Text("Addiyon Keyboard encountered a problem. Please reopen the app.")
            }
        } catch (_: Throwable) {
            finish()
        }
    }
}
