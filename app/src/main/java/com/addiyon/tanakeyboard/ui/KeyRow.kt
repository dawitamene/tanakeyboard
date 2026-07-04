package com.addiyon.tanakeyboard.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.model.KeyData
import com.addiyon.tanakeyboard.ui.keys.CharacterKey
import com.addiyon.tanakeyboard.ui.keys.DeleteKey
import com.addiyon.tanakeyboard.ui.keys.EnterKey
import com.addiyon.tanakeyboard.ui.keys.LanguageToggleKey
import com.addiyon.tanakeyboard.ui.keys.NumberToggleKey
import com.addiyon.tanakeyboard.ui.keys.ShiftKey
import com.addiyon.tanakeyboard.ui.keys.SpaceKey

/**
 * Renders one full row of the keyboard: dispatches each key in the row to
 * its matching key composable. Extracted out of KeyboardScreen so that
 * function is just orchestration (which rows, in what order), while this
 * one owns "how a single row is built".
 */
@Composable
internal fun KeyRow(
    row: List<KeyData>,
    isShift: Boolean,
    isAmharic: Boolean,
    metrics: KeyboardMetrics,
    service: TanaKeyboardService,
    ic: android.view.inputmethod.InputConnection?
) {
    KeyboardRow {
        row.forEach { key ->
            when (key) {

                is KeyData.Character -> {
                    CharacterKey(
                        key = key,
                        isShift = isShift,
                        isAmharic = isAmharic,
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        service = service,
                        ic = ic
                    )
                }

                KeyData.Shift -> {
                    ShiftKey(
                        shiftState = service.shiftState,
                        height = metrics.keyHeight,
                        onClick = { service.toggleShift() }
                    )
                }

                KeyData.Delete -> {
                    DeleteKey(
                        height = metrics.keyHeight,
                        onClick = { ic?.deleteSurroundingText(1, 0) }
                    )
                }

                KeyData.Space -> {
                    SpaceKey(
                        height = metrics.keyHeight,
                        onClick = { ic?.commitText(" ", 1) }
                    )
                }

                KeyData.Enter -> {
                    EnterKey(
                        height = metrics.keyHeight,
                        onClick = {
                            ic?.sendKeyEvent(
                                KeyEvent(
                                    KeyEvent.ACTION_DOWN,
                                    KeyEvent.KEYCODE_ENTER
                                )
                            )
                        }
                    )
                }

                KeyData.NumberToggle -> {
                    NumberToggleKey(
                        height = metrics.keyHeight,
                        onClick = { /* TODO: number layout later */ }
                    )
                }

                KeyData.LanguageToggle -> {
                    LanguageToggleKey(
                        isAmharic = isAmharic,
                        height = metrics.keyHeight,
                        onClick = { service.toggleLanguage() }
                    )
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