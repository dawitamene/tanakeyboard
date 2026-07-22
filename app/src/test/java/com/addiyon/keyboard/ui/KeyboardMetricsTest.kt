package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.GeezNumbersLayout
import com.addiyon.keyboard.layout.KeypadLayout
import com.addiyon.keyboard.layout.MoreSymbolsLayout
import com.addiyon.keyboard.layout.NumberLayout
import com.addiyon.keyboard.layout.SymbolsLayout
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.ui.keys.KeyWeights
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardMetricsTest {

    @Test
    fun tenCharacterTopRowDefinesLetterWidth() {
        val metrics = computeKeyboardMetrics(EnglishLayout.rows, 320.dp)
        assertEquals(32.dp, metrics.keyWidth)
        assertEquals(35.dp, metrics.keyHeight)
    }

    @Test
    fun heightIsCappedAtTheUpperBoundOnWideScreens() {
        val metrics = computeKeyboardMetrics(AmharicLayout.rows, 1000.dp)
        assertEquals(100.dp, metrics.keyWidth)
        assertEquals(45.dp, metrics.keyHeight)
    }

    @Test
    fun heightIsCappedAtTheLowerBoundOnNarrowScreens() {
        val metrics = computeKeyboardMetrics(NumberLayout.rows, 240.dp)
        assertEquals(24.dp, metrics.keyWidth)
        assertEquals(35.dp, metrics.keyHeight)
    }

    @Test
    fun heightScaleEnlargesKeyHeightProportionally() {
        // Base height at 320.dp is 35.dp; the user scale multiplies it.
        val metrics = computeKeyboardMetrics(EnglishLayout.rows, 320.dp, heightScale = 1.2f)
        assertEquals(42.dp, metrics.keyHeight)
        // Width is unaffected by the height scale -- the keyboard still fills its width.
        assertEquals(32.dp, metrics.keyWidth)
    }

    @Test
    fun heightScaleAppliesAfterTheClamp() {
        // The 36-46dp clamp bounds the width-derived base (35.dp here); the user
        // scale then multiplies that clamped value, so a sub-1.0 scale can drop
        // the final height below the clamp floor.
        val metrics = computeKeyboardMetrics(EnglishLayout.rows, 320.dp, heightScale = 0.8f)
        assertEquals(28.dp, metrics.keyHeight)
    }

    @Test
    fun defaultHeightScaleLeavesKeyHeightUnscaled() {
        val scaled = computeKeyboardMetrics(EnglishLayout.rows, 320.dp, heightScale = 1f)
        val unscaled = computeKeyboardMetrics(EnglishLayout.rows, 320.dp)
        assertEquals(unscaled.keyHeight, scaled.keyHeight)
        assertEquals(35.dp, scaled.keyHeight)
    }

    @Test
    fun nonCharacterControlsDoNotAffectReferenceWidth() {
        val rows = listOf(
            listOf(KeyData.Character("A"), KeyData.Character("B"), KeyData.Character("C")),
            listOf(KeyData.Shift, KeyData.Character("D"), KeyData.Delete, KeyData.Space, KeyData.Enter)
        )
        val metrics = computeKeyboardMetrics(rows, 300.dp)
        assertEquals(100.dp, metrics.keyWidth)
    }

    @Test
    fun keypadColumnsOverrideTheCharacterCountHeuristic() {
        val metrics = computeKeyboardMetrics(KeypadLayout.rows, 420.dp, columns = KeypadLayout.columns)
        assertEquals(420.dp / 5.3f, metrics.keyWidth)
        assertEquals(45.dp, metrics.keyHeight)
    }

    @Test
    fun keypadFixedRowsFillItsDeclaredColumnsExactly() {
        val columns = KeypadLayout.columns!!
        assertEquals(columns, 0.7f + 3 * 1.3f + 0.7f, 0.0001f)
        assertEquals(columns, 0.7f + 0.55f + 0.75f + 1.3f + 0.65f + 0.65f + 0.7f, 0.0001f)
    }

    @Test
    fun keypadUsesTheSameKeyHeightAsStandardLayouts() {
        val keypad = computeKeyboardMetrics(KeypadLayout.rows, 320.dp, columns = KeypadLayout.columns)
        val symbols = computeKeyboardMetrics(SymbolsLayout.rows, 320.dp)
        assertEquals(symbols.keyHeight, keypad.keyHeight)
    }

    @Test
    fun fixedRowAreaHeightDoesNotDependOnTheVisibleRowCount() {
        assertEquals(205.dp, keyboardRowsHeight(35.dp, rowCount = 5))
        assertEquals(164.dp, keyboardRowsHeight(35.dp, rowCount = 4))
        assertEquals(205.dp, keyboardRowsHeight(37.dp, rowCount = 5, rowSpacing = 4.dp))
    }

    @Test
    fun fourRowKeypadExpandsToTheMainFiveRowHeight() {
        val keypadKeyHeight = expandedKeyHeight(
            baseKeyHeight = 35.dp,
            targetRowCount = keyboardRowCount(numberRowEnabled = true),
            actualRowCount = 4
        )
        assertEquals(47.25.dp, keypadKeyHeight)
        assertEquals(
            keyboardRowsHeight(35.dp, rowCount = 5),
            keyboardRowsHeight(keypadKeyHeight, rowCount = 4, rowSpacing = 4.dp)
        )
    }

    @Test
    fun everyControlRowFillsTheAvailableWidth() {
        for (row in listOf(
            AmharicLayout.rows[2],
            EnglishLayout.rows[2],
            NumberLayout.rows[2],
            SymbolsLayout.rows[2],
            MoreSymbolsLayout.rows[2],
            GeezNumbersLayout.rows[2]
        )) {
            val characterUnits = row.count { it is KeyData.Character }.toFloat()
            val leadingControlUnits = when (row.first()) {
                KeyData.Shift -> KeyWeights.SHIFT
                KeyData.SymbolsToggle -> KeyWeights.SYMBOLS_TOGGLE
                else -> 0f
            }
            val totalUnits = characterUnits + leadingControlUnits + KeyWeights.DELETE
            assertEquals(10f, totalUnits, 0f)
        }
    }
}
