package com.addiyon.tanakeyboard.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.layout.AmharicLayout
import com.addiyon.tanakeyboard.layout.EnglishLayout
import com.addiyon.tanakeyboard.model.KeyData

@Composable
fun KeyboardScreen(
    service: TanaKeyboardService
) {

    val ic = service.currentInputConnection

    val isAmharic = service.isAmharic
    val isShift = service.isShiftEnabled

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

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {

            // The row with the most letter keys (usually the top row, e.g. qwertyuiop)
            // defines the reference letter width. That row will exactly fill the
            // available width; any row with fewer letters and no flexible key
            // (e.g. asdfghjkl) will fall short and get centered with gaps —
            // which is exactly what we want.
            val maxLetterCount = layout.rows.maxOf { row ->
                row.count { it is KeyData.Character }
            }
            val keyWidth: Dp = maxWidth / maxLetterCount

            // Comfortable height derived once from the fixed letter width.
            val keyHeight: Dp = (keyWidth * 1.25f).coerceIn(40.dp, 52.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {

                layout.rows.forEach { row ->

                    KeyboardRow {

                        row.forEach { key ->

                            when (key) {

                                is KeyData.Character -> {

                                    // Fixed width -> identical size on every row.
                                    KeyButton(
                                        primaryText = if (isShift && !isAmharic) {
                                            key.latin.uppercase()
                                        } else {
                                            key.latin.lowercase()
                                        },
                                        secondaryText = key.amharic,
                                        modifier = Modifier.width(keyWidth),
                                        height = keyHeight
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

                                // Everything below is a "special" key: it flexes
                                // with weight() to absorb leftover space in its row,
                                // so rows containing one of these stretch full width.

                                KeyData.Shift -> {
                                    KeyButton(
                                        icon = Icons.Outlined.KeyboardArrowUp,
                                        modifier = Modifier.weight(1.2f),
                                        height = keyHeight
                                    ) {
                                        service.toggleShift()
                                    }
                                }

                                KeyData.Delete -> {
                                    KeyButton(
                                        icon = Icons.Outlined.Backspace,
                                        modifier = Modifier.weight(1.2f),
                                        height = keyHeight
                                    ) {
                                        ic?.deleteSurroundingText(1, 0)
                                    }
                                }

                                KeyData.Space -> {
                                    KeyButton(
                                        primaryText = "space",
                                        modifier = Modifier.weight(5f),
                                        height = keyHeight
                                    ) {
                                        ic?.commitText(" ", 1)
                                    }
                                }

                                KeyData.Enter -> {
                                    KeyButton(
                                        icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
                                        modifier = Modifier.weight(1.5f),
                                        height = keyHeight
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
                                        modifier = Modifier.weight(1.5f),
                                        height = keyHeight
                                    ) {
                                        // TODO: number layout later
                                    }
                                }

                                KeyData.LanguageToggle -> {
                                    KeyButton(
                                        primaryText = if (isAmharic) "EN" else "አ",
                                        modifier = Modifier.weight(1.2f),
                                        height = keyHeight
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
}

/**
 * Row wrapper — always fills full width. Rows made up only of fixed-width
 * letter keys will naturally center with gaps if they have fewer letters
 * than the reference row; rows containing a weighted special key will
 * stretch that key to consume all remaining space.
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