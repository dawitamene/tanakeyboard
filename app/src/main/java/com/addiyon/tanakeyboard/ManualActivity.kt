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
import com.addiyon.tanakeyboard.ui.manual.ManualScreen
import com.addiyon.tanakeyboard.ui.theme.TanaBrandTheme

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
            TanaBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                Scaffold { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        ManualScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}
