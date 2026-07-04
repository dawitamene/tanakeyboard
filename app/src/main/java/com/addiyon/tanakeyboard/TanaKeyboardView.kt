package com.addiyon.tanakeyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.addiyon.tanakeyboard.ui.KeyboardScreen
import com.addiyon.tanakeyboard.ui.theme.CustomKeyboardTheme

class TanaKeyboardView(
    private val service: TanaKeyboardService
) : AbstractComposeView(service) {

    @Composable
    override fun Content() {
        CustomKeyboardTheme(isDarkTheme = service.isDarkTheme) {
            KeyboardScreen(service)
        }
    }
}