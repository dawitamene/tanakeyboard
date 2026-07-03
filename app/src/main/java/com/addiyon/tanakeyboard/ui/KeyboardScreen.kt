package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.TanaKeyboardService

@Composable
fun KeyboardScreen(
    service: TanaKeyboardService
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        ) {

            KeyboardLayout.qwerty.forEach { row ->

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        KeyButton(
                            label = key,
                            onClick = {
                                service.currentInputConnection
                                    ?.commitText(key, 1)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}