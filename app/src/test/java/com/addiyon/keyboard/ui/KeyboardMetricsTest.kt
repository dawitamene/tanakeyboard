package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.GeezNumbersLayout
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
        assertEquals(36.dp, metrics.keyHeight)
    }

    @Test
    fun heightIsCappedAtTheUpperBoundOnWideScreens() {
        val metrics = computeKeyboardMetrics(AmharicLayout.rows, 1000.dp)
        assertEquals(100.dp, metrics.keyWidth)
        assertEquals(46.dp, metrics.keyHeight)
    }

    @Test
    fun heightIsCappedAtTheLowerBoundOnNarrowScreens() {
        val metrics = computeKeyboardMetrics(NumberLayout.rows, 240.dp)
        assertEquals(24.dp, metrics.keyWidth)
        assertEquals(36.dp, metrics.keyHeight)
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
