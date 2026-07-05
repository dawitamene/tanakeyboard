// ui/KeyRow.kt
package com.addiyon.tanakeyboard.ui

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
import com.addiyon.tanakeyboard.ui.keys.SymbolsToggleKey

/**
 * Renders one full row of the keyboard: dispatches each key in the row to
 * its matching key composable. Extracted out of KeyboardScreen so that
 * function is just orchestration (which rows, in what order), while this
 * one owns "how a single row is built".
 *
 * IMPORTANT: this composable no longer talks to the InputConnection at all.
 * Every action is routed through a method on the service (onCharacter,
 * onDelete, onSpace, onEnter, toggleShift, toggleLanguage). Two reasons:
 *
 *   1. In Amharic mode the buffer is stateful -- a keypress is not a
 *      commitText, it's a mutation of an underlined composing region --
 *      and only the service can keep that state consistent. Poking
 *      InputConnection from here would race the composer.
 *
 *   2. Even for the "trivial" keys, the service needs a chance to flush
 *      the composer at the right boundary (space, enter, language toggle)
 *      before the raw action fires.
 *
 * See the KeyboardScreen doc for the historical reason we never captured
 * an InputConnection reference at composition time either.
 */
@Composable
internal fun KeyRow(
    row: List<KeyData>,
    isShift: Boolean,
    isAmharic: Boolean,
    isNumberMode: Boolean,
    metrics: KeyboardMetrics,
    service: TanaKeyboardService
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
                        service = service
                    )
                }

                KeyData.Shift -> {
                    ShiftKey(
                        shiftState = service.shiftState,
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        onClick = { service.toggleShift() }
                    )
                }

                KeyData.Delete -> {
                    DeleteKey(
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        onClick = { service.onDelete() }
                    )
                }

                KeyData.Space -> {
                    SpaceKey(
                        isAmharic = isAmharic,
                        height = metrics.keyHeight,
                        onClick = { service.onSpace() }
                    )
                }

                KeyData.Enter -> {
                    EnterKey(
                        height = metrics.keyHeight,
                        onClick = { service.onEnter() }
                    )
                }

                KeyData.NumberToggle -> {
                    NumberToggleKey(
                        isNumberMode = isNumberMode,
                        height = metrics.keyHeight,
                        onClick = { service.toggleNumberMode() }
                    )
                }

                KeyData.SymbolsToggle -> {
                    SymbolsToggleKey(
                        numbersMode = service.numbersMode,
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        onClick = { service.toggleSymbolsPage() }
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
 * Row wrapper -- always fills full width. Rows made up only of fixed-width
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