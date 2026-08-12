package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette

@Composable
fun TestAppHost(content: @Composable () -> Unit) {
    ProvideTextRevampLocalization {
        AddiyonBrandTheme(isDarkTheme = false) {
            content()
        }
    }
}

@Composable
fun TestKeyboardHost(content: @Composable () -> Unit) {
    CustomKeyboardTheme(isDarkTheme = false, palette = KeyboardPalette.CLASSIC) {
        content()
    }
}
