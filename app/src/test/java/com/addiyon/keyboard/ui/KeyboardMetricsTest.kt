package com.addiyon.keyboard.ui

import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.NumberLayout
import com.addiyon.keyboard.model.KeyData
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
}
