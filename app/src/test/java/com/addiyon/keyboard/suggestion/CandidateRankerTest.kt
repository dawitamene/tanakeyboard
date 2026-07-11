package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRankerTest {

    @Test
    fun promotesAnExactDictionaryMatchAheadOfAGreedyNonWord() {
        // The "fkr" scenario: ፍቅር is a real word, the structurally-greedy
        // ፍክር isn't -- ፍቅር must win the commit slot.
        val words = setOf("ፍቅር")
        val ranked = CandidateRanker.rank(listOf("ፍክር", "ፍቅር", "ፍኩር"), words::contains)
        assertEquals("ፍቅር", ranked.first())
    }

    @Test
    fun keepsStructuralOrderWhenNothingMatches() {
        val ranked = CandidateRanker.rank(listOf("ሽ", "ስህ"), isWord = { false })
        assertEquals(listOf("ሽ", "ስህ"), ranked)
    }

    @Test
    fun keepsStructuralOrderAmongMultipleExactMatches() {
        // No frequency reordering -- if two candidates are both real words,
        // the engine's own (structural) order among them is preserved.
        val words = setOf("ሰላም", "ሠላም")
        val ranked = CandidateRanker.rank(listOf("ሰላም", "ሠላም"), words::contains)
        assertEquals(listOf("ሰላም", "ሠላም"), ranked)
    }

    @Test
    fun isStableWithinEachPartition() {
        val words = setOf("b", "d")
        val ranked = CandidateRanker.rank(listOf("a", "b", "c", "d", "e"), words::contains)
        assertEquals(listOf("b", "d", "a", "c", "e"), ranked)
    }

    @Test
    fun emptyInputIsEmptyOutput() {
        assertEquals(emptyList<String>(), CandidateRanker.rank(emptyList(), isWord = { true }))
    }
}
