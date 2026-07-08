package com.addiyon.tanakeyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicTableTest {

    // The "y" family is የዩዪያዬይዮ: የ=order0, ያ=order3, ይ=order5(bare).
    private val ye = 'የ'
    private val ya = 'ያ'
    private val yi = 'ይ'

    @Test
    fun fidelOfSameConsonantShareOneFamilyId() {
        val id = AmharicTable.fidelFamilyId[yi]
        assertNotNull(id)
        assertEquals(id, AmharicTable.fidelFamilyId[ya])
        assertEquals(id, AmharicTable.fidelFamilyId[ye])
    }

    @Test
    fun fidelOfDifferentConsonantsHaveDifferentIds() {
        // ተ (t family) vs የ (y family).
        assertNotEquals(AmharicTable.fidelFamilyId['ተ'], AmharicTable.fidelFamilyId[ye])
    }

    @Test
    fun velarSeriesIsIndexedEvenThoughItIsNotDirectlyTypeable() {
        // ኀ series is deliberately kept out of `families`; the reverse index
        // still must cover it so fuzzy matching can reason about it.
        assertNotNull(AmharicTable.fidelFamilyId['ኅ'])
    }

    @Test
    fun labializedFormIsIndexedWithItsFamily() {
        // ሏ is the "ua" form of the "l" family (ለ…); it must share l's id.
        assertEquals(AmharicTable.fidelFamilyId['ለ'], AmharicTable.fidelFamilyId['ሏ'])
    }

    @Test
    fun substitutionCostIsZeroForIdenticalFidel() {
        assertEquals(0, AmharicTable.fidelSubstitutionCost(ya, ya))
    }

    @Test
    fun substitutionCostIsCheapWithinAFamily() {
        // ይ -> ያ (same consonant, different vowel order) is the cheap near-miss.
        assertEquals(
            AmharicTable.SAME_FAMILY_SUBSTITUTION_COST,
            AmharicTable.fidelSubstitutionCost(yi, ya)
        )
        assertTrue(
            AmharicTable.SAME_FAMILY_SUBSTITUTION_COST <
                AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST
        )
    }

    @Test
    fun substitutionCostIsExpensiveAcrossConsonants() {
        // የ -> ተ (wrong consonant entirely) must stay out of a 1-edit budget.
        assertEquals(
            AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
            AmharicTable.fidelSubstitutionCost(ye, 'ተ')
        )
    }

    @Test
    fun nonFidelCharOnlyMatchesItself() {
        assertEquals(0, AmharicTable.fidelSubstitutionCost('።', '።'))
        assertNull(AmharicTable.fidelFamilyId['።'])
        assertEquals(
            AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
            AmharicTable.fidelSubstitutionCost('።', ye)
        )
    }
}
