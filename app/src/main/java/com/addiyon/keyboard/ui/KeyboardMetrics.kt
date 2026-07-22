package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.model.KeyData

private const val KEY_HEIGHT_REFERENCE_COLUMNS = 10f

/**
 * User-selectable multiplier applied to the auto-derived key height (see
 * [computeKeyboardMetrics]). 1.0 is the historical, width-derived default;
 * the bounds keep the keyboard usable at either extreme. Persisted via
 * [com.addiyon.keyboard.ui.settings.KeyboardPrefs.keyboardHeightScale] and
 * driven by the "Keyboard height" slider in the Preferences screen.
 */
const val KEYBOARD_HEIGHT_SCALE_MIN = 0.8f
const val KEYBOARD_HEIGHT_SCALE_MAX = 1.4f
const val KEYBOARD_HEIGHT_SCALE_DEFAULT = 1f

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
 *
 * [heightScale] is the user's "Keyboard height" preference, multiplied into the
 * final key height. It is applied AFTER the 36-46 dp clamp on purpose: the clamp
 * bounds the width-derived auto-size, while the user's scale is an explicit
 * override on top of it (so a larger scale can exceed 46 dp and a smaller one can
 * drop below 36 dp). Width is unaffected -- the keyboard always fills its width.
 */
internal fun computeKeyboardMetrics(
    rows: List<List<KeyData>>,
    availableWidth: Dp,
    columns: Float? = null,
    heightScale: Float = KEYBOARD_HEIGHT_SCALE_DEFAULT
): KeyboardMetrics {
    val cellCount = columns ?: rows.maxOf { row ->
        row.filterIsInstance<KeyData.Character>().sumOf { it.width.toDouble() }.toFloat()
    }.toFloat()
    val keyWidth = availableWidth / cellCount
    val heightReferenceWidth = availableWidth / KEY_HEIGHT_REFERENCE_COLUMNS
    val baseKeyHeight = (heightReferenceWidth * 1.1f).coerceIn(36.dp, 46.dp) - 1.dp

    return KeyboardMetrics(
        keyWidth = keyWidth,
        keyHeight = baseKeyHeight * heightScale
    )
}
