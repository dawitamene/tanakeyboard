package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CandidateRankerTest {

    private fun trie(vararg words: Pair<String, Int>) = WordTrie.build(words.toList())

    private fun rankAmharic(
        readings: List<String>,
        trie: WordTrie,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<CandidateRanker.FuzzyWord> = emptyList()
    ): List<String> =
        CandidateRanker.rankAmharic(
            readings = readings,
            limit = 10,
            frequencyOf = trie::frequencyOf,
            completionsForPrefix = { prefix, limit ->
                trie.suggestionEntries(prefix, limit).map {
                    CandidateRanker.DictionaryWord(it.word, it.frequency)
                }
            },
            visibleReadings = visibleReadings,
            fuzzyWords = fuzzyWords
        )

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

    @Test
    fun bestCommitCandidatePromotesTheHighestScoringExactWord() {
        val words = trie("መስጠት" to 900)
        val readings = listOf("መስተት", "መስጠጥ", "መስጠት", "መስተጥ")
        assertEquals("መስጠት", CandidateRanker.bestCommitCandidate(readings, words::frequencyOf))
    }

    @Test
    fun bestCommitCandidateFallsBackToTheGreedyReadingWithoutExactWords() {
        val words = trie("ስህተት" to 800)
        assertEquals("ሽ", CandidateRanker.bestCommitCandidate(listOf("ሽ", "ስህ"), words::frequencyOf))
    }

    @Test
    fun smartRankingHidesDeadAlternatesWhenAnExactWordExists() {
        val words = trie("መስጠት" to 900)
        val ranked = rankAmharic(
            listOf("መስተት", "መስጠጥ", "መስጠት", "መስተጥ"),
            words
        )
        assertEquals(listOf("መስጠት"), ranked)
    }

    @Test
    fun prefixOnlyReadingsStayAliveForCompletionsWithoutBeingShownAsWords() {
        val words = trie("ስህተት" to 800)
        val ranked = rankAmharic(listOf("ሽ", "ስህ"), words)
        assertEquals(listOf("ሽ", "ስህተት"), ranked)
        assertFalse("ስህ" in ranked)
    }

    @Test
    fun exactDictionaryEvidenceBeatsGreedyLiteralFallback() {
        val words = trie("ፍቅር" to 700)
        val ranked = rankAmharic(listOf("ፍክር", "ፍቅር"), words)
        assertEquals("ፍቅር", ranked.first())
    }

    @Test
    fun exactDictionaryWordBeatsTwoStructuralAlternates() {
        val words = trie("ጴንጤ" to 850)
        val ranked = rankAmharic(Transliterator.candidates("pientie"), words)
        assertEquals("ጴንጤ", ranked.first())
    }

    @Test
    fun visibleQuirkReadingsAppearWhenThereIsNoExactWord() {
        val ranked = rankAmharic(
            readings = listOf("ባ", "ብአ"),
            trie = trie(),
            visibleReadings = listOf("ብአ")
        )
        assertEquals(listOf("ባ", "ብአ"), ranked)
    }

    @Test
    fun exactWordsSuppressVisibleQuirkReadings() {
        val ranked = rankAmharic(
            readings = listOf("ሰላም", "ሰልአም"),
            trie = trie("ሰላም" to 900),
            visibleReadings = listOf("ሰልአም")
        )
        assertEquals(listOf("ሰላም"), ranked)
    }

    @Test
    fun fuzzyWordsRankBelowTheLiteralFallback() {
        val ranked = CandidateRanker.rankAmharic(
            readings = listOf("የተለይ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ -> emptyList() },
            fuzzyWords = listOf(CandidateRanker.FuzzyWord("የተለያዩ", 500, 1))
        )
        assertEquals(listOf("የተለይ", "የተለያዩ"), ranked)
    }
}
