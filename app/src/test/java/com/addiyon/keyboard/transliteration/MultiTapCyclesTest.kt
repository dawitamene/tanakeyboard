package com.addiyon.keyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [AmharicTable.multiTapCycles] data: each cycle's spellings must
 * transliterate to the alternate forms the multi-tap feature promises, and
 * the structural invariants the service's cycling logic relies on must hold.
 */
class MultiTapCyclesTest {

    @Test
    fun aCycleWalksTheFourStandaloneAForms() {
        assertEquals(
            listOf("አ", "ዓ", "ዐ", "ኣ"),
            AmharicTable.multiTapCycles.getValue("a").map(Transliterator::transliterate)
        )
    }

    @Test
    fun kCycleReachesTheQFamily() {
        assertEquals(
            listOf("ክ", "ቅ"),
            AmharicTable.multiTapCycles.getValue("k").map(Transliterator::transliterate)
        )
    }

    @Test
    fun consonantCyclesReachTheShiftFamilies() {
        assertEquals(listOf("ህ", "ሕ"),
            AmharicTable.multiTapCycles.getValue("h").map(Transliterator::transliterate))
        assertEquals(listOf("ስ", "ሥ"),
            AmharicTable.multiTapCycles.getValue("s").map(Transliterator::transliterate))
        assertEquals(listOf("ት", "ጥ"),
            AmharicTable.multiTapCycles.getValue("t").map(Transliterator::transliterate))
        assertEquals(listOf("ች", "ጭ"),
            AmharicTable.multiTapCycles.getValue("c").map(Transliterator::transliterate))
        assertEquals(listOf("ፕ", "ጵ"),
            AmharicTable.multiTapCycles.getValue("p").map(Transliterator::transliterate))
    }

    @Test
    fun vowelCyclesReachThePharyngealSeries() {
        assertEquals(listOf("ኡ", "ዑ"),
            AmharicTable.multiTapCycles.getValue("u").map(Transliterator::transliterate))
        assertEquals(listOf("ኢ", "ዒ"),
            AmharicTable.multiTapCycles.getValue("i").map(Transliterator::transliterate))
        assertEquals(listOf("ኦ", "ዖ"),
            AmharicTable.multiTapCycles.getValue("o").map(Transliterator::transliterate))
        assertEquals(listOf("እ", "ዕ"),
            AmharicTable.multiTapCycles.getValue("e").map(Transliterator::transliterate))
    }

    @Test
    fun everyCycleStartsWithItsOwnKeyAndRendersDistinctStandaloneForms() {
        for ((key, cycle) in AmharicTable.multiTapCycles) {
            assertEquals("cycle for \"$key\" must start with the key itself", key, cycle[0])
            val rendered = cycle.map(Transliterator::transliterate)
            assertEquals(
                "cycle for \"$key\" renders duplicate standalone forms: $rendered",
                rendered.size, rendered.distinct().size
            )
            assertTrue("cycle for \"$key\" needs at least 2 forms", cycle.size >= 2)
        }
    }

    @Test
    fun doubleTappedAAfterAConsonantReadsAsThePharyngealSyllable() {
        // "se" then a -> ሰአ; cycling that a to "`a" must give the ሰዓ of ሰዓት.
        assertEquals("ሰአ", Transliterator.transliterate("sea"))
        assertEquals("ሰዓ", Transliterator.transliterate("se`a"))
    }
}
