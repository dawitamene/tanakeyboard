package com.addiyon.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardNumericLayoutsTest {

    private val standardLayouts = listOf(
        "numbers" to StandardNumberLayout,
        "symbols" to StandardSymbolsLayout,
        "moreSymbols" to StandardMoreSymbolsLayout
    )

    @Test
    fun standardLayoutsHaveTheExpectedGridShape() {
        for ((name, layout) in standardLayouts) {
            assertEquals("$name row count", 4, layout.rows.size)
            assertTrue("$name has an empty row", layout.rows.all { it.isNotEmpty() })
            assertEquals("$name top row width", 10, layout.rows[0].size)
            assertTrue("$name bottom row contains space", KeyData.Space in layout.rows.last())
            assertTrue("$name bottom row contains enter", KeyData.Enter in layout.rows.last())
            assertTrue("$name bottom row contains number toggle", KeyData.NumberToggle in layout.rows.last())
            if (layout === StandardNumberLayout) {
                assertTrue(KeyData.KeypadToggle in layout.rows.last())
            } else {
                assertTrue(KeyData.LanguageToggle in layout.rows.last())
            }
        }
    }

    @Test
    fun everyCharacterKeyIsOneDisplayedSymbol() {
        for ((name, layout) in standardLayouts + ("keypad" to StandardKeypadLayout)) {
            for (key in layout.characters()) {
                assertEquals(
                    "$name key ${key.latin} must be one code point",
                    1,
                    key.latin.codePointCount(0, key.latin.length)
                )
            }
        }
    }

    @Test
    fun standardNumericLayoutsHaveExpectedControls() {
        for ((name, layout) in standardLayouts) {
            assertEquals("$name symbols toggle slot", KeyData.SymbolsToggle, layout.rows[2].first())
            assertEquals("$name delete slot", KeyData.Delete, layout.rows[2].last())
            assertFalse("$name must not contain shift", KeyData.Shift in layout.rows.flatten())
        }
    }

    @Test
    fun slashIsAvailableOnTheFirstNumericPage() {
        assertTrue(KeyData.Character("/") in StandardNumberLayout.rows.flatten())
    }

    @Test
    fun keypadIsAPhonePadWithAControlColumn() {
        assertEquals(4, StandardKeypadLayout.rows.size)
        assertEquals(
            listOf("+", "1", "2", "3", "/"),
            StandardKeypadLayout.rows[0].characters().map { it.latin }
        )
        assertEquals(
            listOf("-", "4", "5", "6"),
            StandardKeypadLayout.rows[1].characters().map { it.latin }
        )
        assertEquals(
            listOf("*", "7", "8", "9"),
            StandardKeypadLayout.rows[2].characters().map { it.latin }
        )
        assertEquals(
            listOf(",", "0", "=", "."),
            StandardKeypadLayout.rows[3].characters().map { it.latin }
        )
        assertEquals(KeyData.Space, StandardKeypadLayout.rows[1].last())
        assertEquals(KeyData.Delete, StandardKeypadLayout.rows[2].last())
        assertEquals(KeyData.NumberToggle, StandardKeypadLayout.rows[3].first())
        assertEquals(KeyData.SymbolsToggle, StandardKeypadLayout.rows[3][2])
        assertEquals(KeyData.Character("0", width = 1.3f), StandardKeypadLayout.rows[3][3])
        assertEquals(KeyData.Enter, StandardKeypadLayout.rows[3].last())
        assertEquals(listOf(5.3f, 5.3f, 5.3f, 5.3f), StandardKeypadLayout.rowColumns)
        assertTrue(
            StandardKeypadLayout.rows.flatten()
                .filterIsInstance<KeyData.Character>()
                .filterNot { it.latin.single().isDigit() }
                .all { it.isSpecial }
        )
        assertFalse(KeyData.Shift in StandardKeypadLayout.rows.flatten())
    }

    @Test
    fun enabledNumberRowStaysAtTheTopAndIsNotAddedToTheKeypad() {
        val layouts = listOf(
            NumbersMode.NUMBERS to StandardNumberLayout,
            NumbersMode.SYMBOLS to StandardSymbolsLayout,
            NumbersMode.MORE_SYMBOLS to StandardMoreSymbolsLayout,
            NumbersMode.GEEZ_NUMBERS to StandardMoreSymbolsLayout,
            NumbersMode.KEYPAD to StandardKeypadLayout
        )

        for ((mode, layout) in layouts) {
            val enabledRows = standardNumericRows(layout, mode, numberRowEnabled = true)
            val disabledRows = standardNumericRows(layout, mode, numberRowEnabled = false)
            assertEquals("$mode disabled row count", layout.rows.size, disabledRows.size)
            assertEquals("$mode disabled rows", layout.rows, disabledRows)
            when (mode) {
                NumbersMode.NUMBERS -> {
                    assertEquals(5, enabledRows.size)
                    assertEquals(StandardLatinNumberRow, enabledRows.first())
                    assertEquals(StandardCommonSymbolsRow, enabledRows[1])
                    assertEquals(StandardExtendedSymbolsRow, enabledRows[2])
                }
                NumbersMode.SYMBOLS -> {
                    assertEquals(5, enabledRows.size)
                    assertEquals(StandardLatinNumberRow, enabledRows.first())
                    assertEquals(StandardCommonSymbolsRow, enabledRows[1])
                    assertEquals(layout.rows.drop(1), enabledRows.drop(2))
                }
                NumbersMode.MORE_SYMBOLS,
                NumbersMode.GEEZ_NUMBERS -> {
                    assertEquals(5, enabledRows.size)
                    assertEquals(StandardLatinNumberRow, enabledRows.first())
                    assertEquals(layout.rows, enabledRows.drop(1))
                }
                NumbersMode.KEYPAD -> assertEquals(layout.rows, enabledRows)
                NumbersMode.OFF -> assertEquals(layout.rows, enabledRows)
            }
        }
    }

    @Test
    fun keypadIsReachableFromTheNumbersPage() {
        assertTrue(KeyData.KeypadToggle in StandardNumberLayout.rows.last())
    }

    private fun KeyboardLayout.characters(): List<KeyData.Character> =
        rows.flatten().filterIsInstance<KeyData.Character>()

    private fun List<KeyData>.characters(): List<KeyData.Character> =
        filterIsInstance<KeyData.Character>()
}
