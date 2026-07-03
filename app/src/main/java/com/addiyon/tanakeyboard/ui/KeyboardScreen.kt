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

// IMPORTANT: this forces Compose to recompose when service state changes
    val isAmharic = service.isAmharic
    val isShift = service.isShiftEnabled

// SELECT LAYOUT
    val layout = if (isAmharic) {
        AmharicLayout
    } else {
        EnglishLayout
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFFD1D5DB))
    ) {

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .wrapContentHeight()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {

            layout.rows.forEach { row ->

                KeyboardRow {

                    row.forEach { key ->

                        when (key) {

                            is KeyData.Character -> {
                                KeyButton(
                                    primaryText = if (isShift && isAmharic.not()) {
                                        key.latin.uppercase()
                                    } else {
                                        key.latin.lowercase()
                                    },
                                    secondaryText = key.amharic,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val output = when {
                                        isShift && !isAmharic -> key.latin.uppercase()
                                        else -> key.latin
                                    }

                                    ic?.commitText(output, 1)

// auto reset shift (like real keyboards)
                                    if (service.isShiftEnabled) {
                                        service.toggleShift()
                                    }
                                }
                            }

                            KeyData.Shift -> {
                                KeyButton(
                                    primaryText = "⇧",
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    service.toggleShift()
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
                                    service.toggleLanguage()
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
