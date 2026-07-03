package com.addiyon.tanakeyboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.addiyon.tanakeyboard.ui.KeyboardScreen

class TanaKeyboardView(
    private val service: TanaKeyboardService
) : AbstractComposeView(service) {

    @Composable
    override fun Content() {
        MaterialTheme {
            KeyboardScreen(service)
        }
    }
}