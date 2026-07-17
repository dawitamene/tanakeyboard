package com.addiyon.keyboard.ui.manual

import com.addiyon.keyboard.transliteration.AmharicTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideModelTest {

    private val rows = GuideModel.build()

    @Test
    fun everyDistinctFamilyAppearsExactlyOnce() {
        // Aliases ("c"/"ch", "x"/"sh", "N"/"gn", "P"/"ph") must merge onto one
        // card each, and the velar ኀ row is appended on top of the families.
        val distinctFamilies = AmharicTable.families.values.distinct()
        assertEquals(distinctFamilies.size + 1, rows.size)
        assertEquals(rows.size, rows.distinctBy { it.cells.first().fidel }.size)
    }

    @Test
    fun aliasSpellingsMergeOntoTheDigraphCard() {
        assertEquals(listOf("c"), rows.first { it.label == "ch" }.aliases)
        assertEquals(listOf("x"), rows.first { it.label == "sh" }.aliases)
        assertEquals(listOf("N"), rows.first { it.label == "gn" }.aliases)
        assertEquals(listOf("P"), rows.first { it.label == "ph" }.aliases)
        assertTrue(rows.none { it.label == "c" || it.label == "x" })
    }

    @Test
    fun sevenFormsPerCardInOrder() {
        for (row in rows) {
            assertEquals("card ${row.label}", 7, row.cells.size)
        }
        val l = rows.first { it.label == "l" }
        assertEquals("ለሉሊላሌልሎ", l.cells.joinToString("") { it.fidel.toString() })
        assertEquals(
            listOf("le", "lu", "li", "la", "lie", "l", "lo"),
            l.cells.map { it.latin }
        )
        assertEquals('ሏ', l.ua?.fidel)
        assertEquals("lua", l.ua?.latin)
    }

    @Test
    fun bareVowelCardsDropTheSeraPrefix() {
        val glottal = rows.first { it.label == "a" }
        assertEquals('ኣ', glottal.cells[3].fidel)
        assertEquals("a", glottal.cells[3].latin)
        assertEquals("ie", glottal.cells[4].latin)

        val pharyngeal = rows.first { it.label == "A" }
        assertEquals('ዓ', pharyngeal.cells[3].fidel)
        assertEquals("a", pharyngeal.cells[3].latin)
        assertEquals('ዔ', pharyngeal.cells[4].fidel)
        assertEquals("ie", pharyngeal.cells[4].latin)
    }

    @Test
    fun chartFollowsTraditionalOrderIncludingQaAndVa() {
        assertEquals('ሀ', rows.first().cells[0].fidel)
        val baseOrder = rows.map { it.cells[0].fidel }
        // ቐ directly after ቀ and ቨ directly after በ (the old hardcoded order
        // dropped both series to the end of the guide).
        assertEquals(baseOrder.indexOf('ቀ') + 1, baseOrder.indexOf('ቐ'))
        assertEquals(baseOrder.indexOf('በ') + 1, baseOrder.indexOf('ቨ'))
    }

    @Test
    fun searchTextCoversLatinsFidelsAndAliases() {
        val ch = rows.first { it.label == "ch" }
        for (needle in listOf("ch", "c", "ቸ", "ቻ", "chie", "chua", "ቿ")) {
            assertTrue("missing \"$needle\"", ch.searchText.contains(needle))
        }
    }
}
