package com.addiyon.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    }
}
