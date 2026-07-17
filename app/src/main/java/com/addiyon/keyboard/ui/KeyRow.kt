// ui/KeyRow.kt
package com.addiyon.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.ui.keys.CharacterKey
import com.addiyon.keyboard.ui.keys.DeleteKey
import com.addiyon.keyboard.ui.keys.EnterKey
import com.addiyon.keyboard.ui.keys.KeypadToggleKey
import com.addiyon.keyboard.ui.keys.LanguageToggleKey
import com.addiyon.keyboard.ui.keys.NumberToggleKey
import com.addiyon.keyboard.ui.keys.ShiftKey
import com.addiyon.keyboard.ui.keys.SpaceKey
import com.addiyon.keyboard.ui.keys.SymbolsToggleKey

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
    service: AddiyonKeyboardService,
    vibrateOnKeypress: Boolean,
    soundOnKeypress: Boolean
) {
    val isKeypad = service.numbersMode == com.addiyon.keyboard.model.NumbersMode.KEYPAD
    KeyboardRow {
        row.forEach { key ->
            when (key) {

                is KeyData.Character -> {
                    // In an email field the letter layouts' comma key becomes
                    // an "@" key (Gboard-style): addresses need "@" constantly
                    // and "," never. A runtime remap rather than layout data,
                    // so both languages' layouts get it; the numeric pages
                    // keep their literal comma.
                    val effectiveKey =
                        if (key.latin == "," && !isNumberMode && service.isEmailField) {
                            key.copy(latin = "@", isSpecial = true)
                        } else {
                            key
                        }
                    CharacterKey(
                        key = effectiveKey,
                        isShift = isShift,
                        // Not raw isAmharic: on the numeric pages every key
                        // commits its literal character (no transliteration),
                        // so the fidel corner preview must be suppressed there
                        // even while Amharic mode is on -- otherwise the ","
                        // key would advertise ፣ but type ",".
                        isAmharic = isAmharic && !isNumberMode,
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        service = service,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress
                    )
                }

                KeyData.Shift -> {
                    ShiftKey(
                        shiftState = service.shiftState,
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.toggleShift() }
                    )
                }

                KeyData.Delete -> {
                    DeleteKey(
                        width = metrics.keyWidth,
                        widthMultiplier = if (isKeypad) 0.7f else com.addiyon.keyboard.ui.keys.KeyWeights.DELETE,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onPressStart = { service.onDeleteGestureStart() },
                        onPressEnd = { service.onDeleteGestureEnd() },
                        onClick = { service.onDelete() }
                    )
                }

                KeyData.Space -> {
                    SpaceKey(
                        isAmharic = isAmharic,
                        fixedWidth = if (isKeypad) metrics.keyWidth * 0.7f else null,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.onSpace() },
                        onSwipe = { service.toggleLanguage() }
                    )
                }

                KeyData.Enter -> {
                    EnterKey(
                        action = service.enterAction,
                        fixedWidth = if (isKeypad) metrics.keyWidth * 0.7f else null,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.onEnter() }
                    )
                }

                KeyData.NumberToggle -> {
                    NumberToggleKey(
                        isNumberMode = isNumberMode,
                        isAmharic = isAmharic,
                        fixedWidth = if (isKeypad) metrics.keyWidth * 0.7f else null,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.toggleNumberMode() }
                    )
                }

                KeyData.SymbolsToggle -> {
                    SymbolsToggleKey(
                        numbersMode = service.numbersMode,
                        isAmharic = service.isAmharic,
                        width = metrics.keyWidth,
                        widthMultiplier = if (isKeypad) 0.75f else com.addiyon.keyboard.ui.keys.KeyWeights.SYMBOLS_TOGGLE,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.toggleSymbolsPage() }
                    )
                }

                KeyData.KeypadToggle -> {
                    KeypadToggleKey(
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
                        onClick = { service.openKeypad() }
                    )
                }

                KeyData.LanguageToggle -> {
                    LanguageToggleKey(
                        height = metrics.keyHeight,
                        vibrateOnKeypress = vibrateOnKeypress,
                        soundOnKeypress = soundOnKeypress,
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
