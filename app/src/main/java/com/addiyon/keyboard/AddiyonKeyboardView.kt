package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.addiyon.keyboard.ui.KeyboardScreen
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme

class AddiyonKeyboardView(
    private val service: AddiyonKeyboardService
) : AbstractComposeView(service) {

    @Composable
    override fun Content() {
        CustomKeyboardTheme(isDarkTheme = service.isDarkTheme, palette = service.palette) {
            KeyboardScreen(service)
        }
    }
}