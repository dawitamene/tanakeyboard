package com.addiyon.tanakeyboard.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.layout.AmharicLayout
import com.addiyon.tanakeyboard.layout.EnglishLayout
import com.addiyon.tanakeyboard.model.KeyData

@Composable
fun KeyboardScreen(
    service: TanaKeyboardService
) {
    val ic = service.currentInputConnection

// LANGUAGE STATE
    var isAmharic by remember { mutableStateOf(false) }

// SELECT LAYOUT
    val layout = if (isAmharic) {
        AmharicLayout
    } else {
        EnglishLayout
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD1D5DB))
    ) {

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {

            layout.rows.forEach { row ->

                KeyboardRow {

                    row.forEach { key ->

                        when (key) {

                            is KeyData.Character -> {
                                KeyButton(
                                    primaryText = key.latin,
                                    secondaryText = key.amharic,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    ic?.commitText(key.latin, 1)
                                }
                            }

                            KeyData.Shift -> {
                                KeyButton(
                                    primaryText = "⇧",
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    // TODO shift later
                                }
                            }

                            KeyData.Delete -> {
                                KeyButton(
                                    primaryText = "⌫",
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    ic?.deleteSurroundingText(1, 0)
                                }
                            }

                            KeyData.Space -> {
                                KeyButton(
                                    primaryText = "space",
                                    modifier = Modifier.weight(5f)
                                ) {
                                    ic?.commitText(" ", 1)
                                }
                            }

                            KeyData.Enter -> {
                                KeyButton(
                                    primaryText = "return",
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    ic?.sendKeyEvent(
                                        KeyEvent(
                                            KeyEvent.ACTION_DOWN,
                                            KeyEvent.KEYCODE_ENTER
                                        )
                                    )
                                }
                            }

                            KeyData.NumberToggle -> {
                                KeyButton(
                                    primaryText = "123",
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    // TODO number layout later
                                }
                            }

                            KeyData.LanguageToggle -> {
                                KeyButton(
                                    primaryText = if (isAmharic) "EN" else "አ",
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    isAmharic = !isAmharic
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

}

/**

 * Row wrapper
 */
@Composable
private fun KeyboardRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
