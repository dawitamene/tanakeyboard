package com.addiyon.tanakeyboard.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick
    ) {
        Text(label)
    }
}
