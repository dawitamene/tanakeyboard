package com.addiyon.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.addiyon.keyboard.ui.manual.ManualScreen
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme

/**
 * Standalone host for the typing guide when it's opened from the keyboard
 * toolbar. Like [ThemesActivity], it renders [ManualScreen] as its own first
 * frame so the keyboard's "Typing guide" button skips [MainActivity]'s home
 * screen entirely; back just finishes.
 */
class ManualActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContent {
                ProvideAppLocalization {
                    AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                        ManualScreen(onBack = { finish() })
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            com.addiyon.keyboard.SafeLog.e(oom, "ManualActivity onCreate OOM")
            renderFallback()
        } catch (t: Throwable) {
            com.addiyon.keyboard.SafeLog.e(t, "ManualActivity onCreate")
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
