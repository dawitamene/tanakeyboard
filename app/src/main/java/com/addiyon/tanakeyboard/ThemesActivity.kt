package com.addiyon.tanakeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.addiyon.tanakeyboard.ui.settings.ThemesScreen
import com.addiyon.tanakeyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.tanakeyboard.ui.theme.TanaBrandTheme

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
                TanaBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                    Scaffold { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            ThemesScreen(
                                onBack = { finish() },
                                onPaletteChosen = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }
}
