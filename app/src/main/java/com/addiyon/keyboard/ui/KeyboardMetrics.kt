package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.model.KeyData

private const val KEY_HEIGHT_REFERENCE_COLUMNS = 10f

/**
 * The computed sizing for every key on the keyboard.
 */
internal data class KeyboardMetrics(
    val keyWidth: Dp,
    val keyHeight: Dp
)

internal fun keyboardRowsHeight(keyHeight: Dp, rowCount: Int, rowSpacing: Dp = 6.dp): Dp =
    (keyHeight + rowSpacing) * rowCount

internal fun keyboardRowCount(numberRowEnabled: Boolean): Int =
    if (numberRowEnabled) 5 else 4

internal fun expandedKeyHeight(
    baseKeyHeight: Dp,
    targetRowCount: Int,
    actualRowCount: Int,
    targetRowSpacing: Dp = 6.dp,
    actualRowSpacing: Dp = 4.dp
): Dp = keyboardRowsHeight(
    keyHeight = baseKeyHeight,
    rowCount = targetRowCount,
    rowSpacing = targetRowSpacing
) / actualRowCount.toFloat() - actualRowSpacing

/**
 * Derives key sizing from the available width and the layout's rows.
 *
 * The row with the most letter keys (usually the top row, e.g. qwertyuiop)
 * defines the reference letter width — that row will exactly fill the
 * available width. Any row with fewer letters and no flexible key
 * (e.g. asdfghjkl) will fall short and get centered with gaps, while rows
 * containing a weighted special key (Shift, Space, etc.) stretch that key
 * to fill the remainder.
 *
 * [columns] overrides that Character-width heuristic with an explicit cell
 * count (see [com.addiyon.keyboard.model.KeyboardLayout.columns]).
 */
internal fun computeKeyboardMetrics(
    rows: List<List<KeyData>>,
    availableWidth: Dp,
    columns: Float? = null
): KeyboardMetrics {
    val cellCount = columns ?: rows.maxOf { row ->
        row.filterIsInstance<KeyData.Character>().sumOf { it.width.toDouble() }.toFloat()
    }.toFloat()
    val keyWidth = availableWidth / cellCount
    val heightReferenceWidth = availableWidth / KEY_HEIGHT_REFERENCE_COLUMNS
    val keyHeight = (heightReferenceWidth * 1.1f).coerceIn(36.dp, 46.dp) - 1.dp

    return KeyboardMetrics(
        keyWidth = keyWidth,
        keyHeight = keyHeight
    )
}
