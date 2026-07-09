package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.model.KeyData

/**
 * The computed sizing for every key on the keyboard.
 */
internal data class KeyboardMetrics(
    val keyWidth: Dp,
    val keyHeight: Dp
)

/**
 * Derives key sizing from the available width and the layout's rows.
 *
 * The row with the most letter keys (usually the top row, e.g. qwertyuiop)
 * defines the reference letter width — that row will exactly fill the
 * available width. Any row with fewer letters and no flexible key
 * (e.g. asdfghjkl) will fall short and get centered with gaps, while rows
 * containing a weighted special key (Shift, Space, etc.) stretch that key
 * to fill the remainder.
 */
internal fun computeKeyboardMetrics(
    rows: List<List<KeyData>>,
    availableWidth: Dp
): KeyboardMetrics {
    val maxLetterCount = rows.maxOf { row ->
        row.count { it is KeyData.Character }
    }
    val keyWidth = availableWidth / maxLetterCount
    val keyHeight = (keyWidth * 1.1f).coerceIn(36.dp, 46.dp)

    return KeyboardMetrics(
        keyWidth = keyWidth,
        keyHeight = keyHeight
    )
}