package com.addiyon.tanakeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.addiyon.tanakeyboard.ui.home.HomeScreen
import com.addiyon.tanakeyboard.ui.manual.ManualScreen
import com.addiyon.tanakeyboard.ui.onboarding.OnboardingScreen
import com.addiyon.tanakeyboard.ui.theme.CustomKeyboardTheme

private enum class ScreenKey { Onboarding, Home, Manual }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            // A normal Activity DOES get reliable configuration updates,
            // so isSystemInDarkTheme() is fine to use here. The keyboard's
            // own IME window (TanaKeyboardView) tracks dark mode manually
            // instead -- see TanaKeyboardService.isDarkTheme for why.
            CustomKeyboardTheme(isDarkTheme = isSystemInDarkTheme()) {
                Scaffold { innerPadding ->
                    val status by rememberKeyboardStatus()
                    var screen by rememberSaveable {
                        mutableStateOf(
                            if (KeyboardStatus.isEnabled(this@MainActivity)) ScreenKey.Home
                            else ScreenKey.Onboarding
                        )
                    }

                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (screen) {
                            ScreenKey.Onboarding -> OnboardingScreen(
                                status = status,
                                onDone = { screen = ScreenKey.Home }
                            )
                            ScreenKey.Home -> HomeScreen(
                                status = status,
                                onOpenManual = { screen = ScreenKey.Manual }
                            )
                            ScreenKey.Manual -> ManualScreen(
                                onBack = { screen = ScreenKey.Home }
                            )
                        }
                    }
                }
            }
        }
    }
}
