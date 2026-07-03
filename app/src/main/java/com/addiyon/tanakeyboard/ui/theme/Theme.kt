package com.addiyon.tanakeyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TanaColorScheme = darkColorScheme()

@Composable
fun CustomKeyboardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TanaColorScheme,
        content = content
    )
}