package com.addiyon.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideAppLocalization {
                AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                    ManualScreen(onBack = { finish() })
                }
            }
        }
    }
}
