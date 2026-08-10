package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun ranksByEditDistanceThenFrequencyDescending() {
        val m = FuzzyMatcher(maxEdits = 1)
        val candidates = listOf(
            "cat" to 10,
            "cot" to 100,
            "car" to 500,
            "bat" to 800,
        )
        val matches = m.rank("cat", candidates)
        // "cat" is exact (distance 0), then "car"/"cot" (1 edit) sorted by
        // frequency. "bat" is also 1 edit away (sub b->c) at high frequency
        // -- it should appear, not be excluded.
        assertEquals(listOf("cat", "bat", "car", "cot"), matches.map { it.word })
        assertEquals(0, matches[0].editDistance)
    }

    @Test
    fun rejectsCandidatesBeyondTheBudget() {
        val m = FuzzyMatcher(maxEdits = 1)
        val candidates = listOf("house" to 100)
        assertEquals(emptyList<FuzzyMatch>(), m.rank("mouze", candidates))
    }

    @Test
    fun zeroBudgetStillIncludesExactMatch() {
        val m = FuzzyMatcher(maxEdits = 0)
        // Distance 0 (exact match) is always included; only non-exact
        // matches are excluded by a 0-edit budget.
        assertEquals(listOf(FuzzyMatch("the", 0, 1)), m.rank("the", listOf("the" to 1)))
        assertEquals(emptyList<FuzzyMatch>(), m.rank("tho", listOf("the" to 1)))
    }

    @Test
    fun emptyPrefixReturnsEmpty() {
        val m = FuzzyMatcher(maxEdits = 2)
        assertEquals(emptyList<FuzzyMatch>(), m.rank("", listOf("a" to 1)))
    }

    @Test
    fun respectsInsertAndDeleteCosts() {
        // Default ins/del = 1; "informtion" (missing 'a') is one insertion
        // from "information" -> distance 1 -> match.
        val m = FuzzyMatcher(maxEdits = 1)
        val matches = m.rank("informtion", listOf("information" to 500, "informer" to 50))
        assertEquals(listOf("information"), matches.map { it.word })
    }

    @Test
    fun substitutionCostCallbackIsHonored() {
        // a<->o is free, everything else costs 2. "cot" -> "cat" is free
        // (distance 0), "cut" -> "cat" is cost 2 (excluded at budget 1).
        val cheapAO = SubstitutionCost { a, b ->
            when {
                a == b -> 0
                setOf(a, b) == setOf('a', 'o') -> 0
                else -> 2
            }
        }
        val m = FuzzyMatcher(maxEdits = 1, substitutionCost = cheapAO)
        val candidates = listOf("cat" to 1, "cut" to 1)
        // "cot" -> "cat" is free (a<->o swap), so "cat" matches with distance 0.
        assertEquals(listOf("cat"), m.rank("cot", candidates).map { it.word })
        // "cut" -> "cut" is exact (distance 0); "cut" -> "cat" is cost 2
        // (a->u is not the cheap swap), so "cat" is excluded at maxEdits = 1.
        // The only survivor is the exact "cut" match.
        assertEquals(listOf("cut"), m.rank("cut", candidates).map { it.word })
    }
}
