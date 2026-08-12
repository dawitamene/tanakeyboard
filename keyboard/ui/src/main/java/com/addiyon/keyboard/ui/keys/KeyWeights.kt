package com.addiyon.keyboard.ui.keys

/**
 * Relative weights for special keys, expressed as multiples of a single
 * letter key's width. Kept in one place so the row-filling ratios are easy
 * to find and tune, instead of being scattered as magic numbers across
 * each key composable.
 */
object KeyWeights {
    const val SHIFT = 1.5f
    const val DELETE = 1.5f
    const val SPACE = 5f
    const val ENTER = 1.5f
    const val NUMBER_TOGGLE = 1.5f
    const val SYMBOLS_TOGGLE = 1.5f
    // Same as LANGUAGE_TOGGLE: the keypad key occupies that key's slot on
    // the numbers page, so the rest of the row must not shift.
    const val KEYPAD_TOGGLE = 1.2f
    const val LANGUAGE_TOGGLE = 1.2f
}
