package com.addiyon.keyboard.model


data class KeyboardLayout(
    val rows: List<List<KeyData>>,
    /**
     * Explicit column-cell count for key sizing, in letter-key widths.
     * Null (every standard layout): the row with the most Character keys
     * defines the cell -- see computeKeyboardMetrics.
     */
    val columns: Float? = null,
    val rowColumns: List<Float?>? = null
)
