package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.transliteration.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutInvariantTest {

    // The keypad is deliberately absent: its phone-pad grid breaks
    // every 10-column expectation below and has its own dedicated tests.
    private val allLayouts = listOf(
        "amharic" to AmharicLayout,
        "english" to EnglishLayout,
        "numbers" to NumberLayout,
        "symbols" to SymbolsLayout,
        "moreSymbols" to MoreSymbolsLayout,
        "geezNumbers" to GeezNumbersLayout
    )

    @Test
    fun everyLayoutHasTheExpectedGridShape() {
        for ((name, layout) in allLayouts) {
            assertEquals("$name row count", 4, layout.rows.size)
            assertTrue("$name has an empty row", layout.rows.all { it.isNotEmpty() })
            assertEquals("$name top row width", 10, layout.rows[0].size)
            assertTrue("$name bottom row contains space", KeyData.Space in layout.rows.last())
            assertTrue("$name bottom row contains enter", KeyData.Enter in layout.rows.last())
            // The NUMBERS page gives the language-toggle slot to the keypad
            // key instead (language switching stays on the space-bar swipe).
            if (layout === NumberLayout) {
                assertTrue("numbers bottom row contains keypad toggle", KeyData.KeypadToggle in layout.rows.last())
            } else {
                assertTrue("$name bottom row contains language toggle", KeyData.LanguageToggle in layout.rows.last())
            }
            assertTrue("$name bottom row contains number toggle", KeyData.NumberToggle in layout.rows.last())
        }
    }

    @Test
    fun everyCharacterKeyIsOneDisplayedSymbol() {
        for ((name, layout) in allLayouts + ("keypad" to KeypadLayout)) {
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
    fun letterLayoutsKeepTheSamePhysicalKeyMap() {
        assertEquals(EnglishLayout.rows, AmharicLayout.rows)
        val expectedTop = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        val expectedHome = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
        val expectedBottomLetters = listOf("Z", "X", "C", "V", "B", "N", "M")

        assertEquals(expectedTop, AmharicLayout.rows[0].characters().map { it.latin })
        assertEquals(expectedHome, AmharicLayout.rows[1].characters().map { it.latin })
        assertEquals(expectedBottomLetters, AmharicLayout.rows[2].characters().map { it.latin })
        assertEquals(
            listOf(KeyData.NumberToggle, KeyData.Character(","), KeyData.LanguageToggle, KeyData.Space, KeyData.Character("."), KeyData.Enter),
            AmharicLayout.rows[3]
        )
    }

    @Test
    fun letterLayoutsHaveShiftAndDeleteOnlyWhereExpected() {
        for ((name, layout) in listOf("amharic" to AmharicLayout, "english" to EnglishLayout)) {
            assertEquals("$name shift slot", KeyData.Shift, layout.rows[2].first())
            assertEquals("$name delete slot", KeyData.Delete, layout.rows[2].last())
            assertEquals("$name shift count", 1, layout.rows.flatten().count { it == KeyData.Shift })
            assertEquals("$name delete count", 1, layout.rows.flatten().count { it == KeyData.Delete })
        }
    }

    @Test
    fun numericLayoutsHaveSymbolsToggleAndDeleteInTheControlRow() {
        for ((name, layout) in listOf(
            "numbers" to NumberLayout,
            "symbols" to SymbolsLayout,
            "moreSymbols" to MoreSymbolsLayout,
            "geezNumbers" to GeezNumbersLayout
        )) {
            assertEquals("$name symbols toggle slot", KeyData.SymbolsToggle, layout.rows[2].first())
            assertEquals("$name delete slot", KeyData.Delete, layout.rows[2].last())
            assertFalse("$name must not contain shift", layout.rows.flatten().contains(KeyData.Shift))
        }
    }

    @Test
    fun slashIsAvailableOnTheFirstNumericPage() {
        assertTrue(KeyData.Character("/") in NumberLayout.rows.flatten())
    }

    @Test
    fun keypadIsAPhonePadWithAControlColumn() {
        assertEquals(4, KeypadLayout.rows.size)
        assertEquals(listOf("+", "1", "2", "3", "/"), KeypadLayout.rows[0].characters().map { it.latin })
        assertEquals(listOf("-", "4", "5", "6"), KeypadLayout.rows[1].characters().map { it.latin })
        assertEquals(listOf("*", "7", "8", "9"), KeypadLayout.rows[2].characters().map { it.latin })
        assertEquals(listOf(",", "0", "=", "."), KeypadLayout.rows[3].characters().map { it.latin })
        assertEquals(KeyData.Space, KeypadLayout.rows[1].last())
        assertEquals(KeyData.Delete, KeypadLayout.rows[2].last())
        assertEquals(KeyData.NumberToggle, KeypadLayout.rows[3].first())
        assertEquals(KeyData.SymbolsToggle, KeypadLayout.rows[3][2])
        assertEquals(KeyData.Character("0", width = 1.3f), KeypadLayout.rows[3][3])
        assertEquals(KeyData.Enter, KeypadLayout.rows[3].last())
        assertEquals(listOf(5.3f, 5.3f, 5.3f, 5.3f), KeypadLayout.rowColumns)
        assertTrue(KeypadLayout.rows.flatten().filterIsInstance<KeyData.Character>()
            .filterNot { it.latin.single().isDigit() }.all { it.isSpecial })
        assertFalse("keypad must not contain shift", KeyData.Shift in KeypadLayout.rows.flatten())
    }

    @Test
    fun enabledNumberRowStaysAtTheTopAndIsNotAddedToTheKeypad() {
        val layouts = listOf(
            NumbersMode.NUMBERS to NumberLayout,
            NumbersMode.SYMBOLS to SymbolsLayout,
            NumbersMode.MORE_SYMBOLS to MoreSymbolsLayout,
            NumbersMode.GEEZ_NUMBERS to GeezNumbersLayout,
            NumbersMode.KEYPAD to KeypadLayout
        )
        for ((mode, layout) in layouts) {
            val enabledRows = numericRows(layout, mode, numberRowEnabled = true)
            val disabledRows = numericRows(layout, mode, numberRowEnabled = false)
            assertEquals("$mode disabled row count", layout.rows.size, disabledRows.size)
            assertEquals("$mode disabled rows", layout.rows, disabledRows)
            if (mode == NumbersMode.NUMBERS) {
                assertEquals("$mode enabled row count", 5, enabledRows.size)
                assertEquals("$mode number row", LatinNumberRow, enabledRows.first())
                assertEquals("$mode common row", CommonSymbolsRow, enabledRows[1])
                assertEquals("$mode extended row", ExtendedSymbolsRow, enabledRows[2])
            } else if (mode == NumbersMode.SYMBOLS) {
                assertEquals("$mode enabled row count", 5, enabledRows.size)
                assertEquals("$mode number row", LatinNumberRow, enabledRows.first())
                assertEquals("$mode common row", CommonSymbolsRow, enabledRows[1])
                assertEquals("$mode preserves remaining rows", layout.rows.drop(1), enabledRows.drop(2))
            } else if (mode == NumbersMode.MORE_SYMBOLS || mode == NumbersMode.GEEZ_NUMBERS) {
                assertEquals("$mode enabled row count", 5, enabledRows.size)
                assertEquals("$mode number row", LatinNumberRow, enabledRows.first())
                assertEquals("$mode preserves base rows", layout.rows, enabledRows.drop(1))
            } else if (mode == NumbersMode.KEYPAD) {
                assertEquals("$mode does not add a row", layout.rows, enabledRows)
            }
        }
        assertEquals(LatinNumberRow, numericRows(NumberLayout, NumbersMode.NUMBERS, true).first())
        assertEquals(CommonSymbolsRow, numericRows(SymbolsLayout, NumbersMode.SYMBOLS, true)[1])
        assertEquals(4, numericRows(KeypadLayout, NumbersMode.KEYPAD, true).size)
    }

    @Test
    fun keypadIsReachableFromTheNumbersPage() {
        assertTrue(KeyData.KeypadToggle in NumberLayout.rows.last())
    }

    @Test
    fun amharicLetterKeysAllHaveFidelPreviews() {
        val keys = AmharicLayout.rows.flatten().filterIsInstance<KeyData.Character>()
        for (key in keys) {
            val resolved = key.latin.lowercase()
            val preview = Transliterator.transliterate(resolved)
            assertTrue("missing preview for ${key.latin}", preview.isNotEmpty())
            assertFalse("Amharic key ${key.latin} passed through", preview == resolved)
        }
    }

    private fun KeyboardLayout.characters(): List<KeyData.Character> =
        rows.flatten().filterIsInstance<KeyData.Character>()

    private fun List<KeyData>.characters(): List<KeyData.Character> =
        filterIsInstance<KeyData.Character>()
}
