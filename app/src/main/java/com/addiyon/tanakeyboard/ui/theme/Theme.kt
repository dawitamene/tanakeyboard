package com.addiyon.tanakeyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Keyboard-specific colors, kept separate from generic Material role names
 * so key composables can reference something semantically meaningful
 * (e.g. "specialKey") instead of guessing which Material role happens to
 * map to the right shade.
 */
object KeyboardColors {
    val trayLight = Color(0xFFECEEF0)
    val keyLight = Color(0xFFFFFFFF)
    val specialKeyLight = Color(0xFFD3D8DC)
    val textLight = Color(0xFF1C1C1E)

    val trayDark = Color(0xFF1E1F22)
    val keyDark = Color(0xFF3A3B3E)
    val specialKeyDark = Color(0xFF232427)
    val textDark = Color(0xFFF2F2F2)
}

private val LightScheme = lightColorScheme(
    background = KeyboardColors.trayLight,
    surface = KeyboardColors.keyLight,
    surfaceVariant = KeyboardColors.specialKeyLight,
    onSurface = KeyboardColors.textLight,
    onSurfaceVariant = KeyboardColors.textLight
)

private val DarkScheme = darkColorScheme(
    background = KeyboardColors.trayDark,
    surface = KeyboardColors.keyDark,
    surfaceVariant = KeyboardColors.specialKeyDark,
    onSurface = KeyboardColors.textDark,
    onSurfaceVariant = KeyboardColors.textDark
)

/**
 * Wraps the keyboard content in a MaterialTheme using colors tuned for a
 * modern, Gboard/iOS-style keyboard.
 *
 * IMPORTANT: this does NOT use isSystemInDarkTheme() to decide the scheme.
 * InputMethodService runs in its own window, separate from any Activity,
 * and Compose's isSystemInDarkTheme() reads LocalConfiguration, which
 * isn't reliably updated for an IME window when the system theme changes.
 * Instead, the caller (TanaKeyboardService) tracks dark mode itself via
 * onConfigurationChanged and passes the current value in explicitly, so
 * this composable just reacts to whatever state it's given.
 */
@Composable
fun CustomKeyboardTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val scheme = if (isDarkTheme) DarkScheme else LightScheme

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}