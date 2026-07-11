package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.transliteration.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutInvariantTest {

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
            assertTrue("$name bottom row contains language toggle", KeyData.LanguageToggle in layout.rows.last())
            assertTrue("$name bottom row contains number toggle", KeyData.NumberToggle in layout.rows.last())
        }
    }

    @Test
    fun everyCharacterKeyIsOneDisplayedSymbol() {
        for ((name, layout) in allLayouts) {
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
