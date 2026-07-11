package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRankerTest {

    private fun trie(vararg words: Pair<String, Int>) = WordTrie.build(words.toList())

    private fun rankAmharic(
        readings: List<String>,
        trie: WordTrie,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<CandidateRanker.FuzzyWord> = emptyList(),
        quirkReadings: Set<String> = emptySet(),
        ngramNext: Map<String, Int> = emptyMap()
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
            fuzzyWords = fuzzyWords,
            quirkReadings = quirkReadings,
            ngramNext = ngramNext
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
    fun aQuirkSplitDictionaryWordDoesNotHijackTheGreedyDefault() {
        // "me": greedy መ isn't a dictionary word, but the structural split ም+እ
        // (ምእ) is. The split must NOT become the default -- greedy መ stays the
        // literal default, and ምእ only rides along as a quirk/completion chip.
        val ranked = rankAmharic(
            readings = listOf("መ", "ምእ", "ምዕ"),
            trie = trie("ምእ" to 900, "ምዕ" to 900, "ምእመናን" to 500),
            visibleReadings = listOf("ምእ", "ምዕ"),
            quirkReadings = setOf("ምእ", "ምዕ")
        )
        assertEquals("መ", ranked.first())
        assertTrue("ምእ" in ranked)      // still offered as a chip
        assertTrue("ምእመናን" in ranked)   // split-prefix completion still works
    }

    @Test
    fun bestCommitCandidateIgnoresQuirkSplitWords() {
        // Same "me" case for the commit slot: space must land መ, not the split
        // dictionary word ምእ.
        val commit = CandidateRanker.bestCommitCandidate(
            listOf("መ", "ምእ", "ምዕ"),
            frequencyOf = mapOf("ምእ" to 900, "ምዕ" to 900)::get,
            quirkReadings = setOf("ምእ", "ምዕ")
        )
        assertEquals("መ", commit)
    }

    @Test
    fun bestCommitCandidateStillPromotesNonQuirkExactWords() {
        // "fkr": greedy ፍክር isn't a word, ፍቅር (a same-segmentation family swap,
        // not a quirk) is -- it must still win the commit.
        val commit = CandidateRanker.bestCommitCandidate(
            listOf("ፍክር", "ፍቅር"),
            frequencyOf = mapOf("ፍቅር" to 900)::get,
            quirkReadings = emptySet()
        )
        assertEquals("ፍቅር", commit)
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

    @Test
    fun ngramBoostReordersWithinTheExactTier() {
        // Both readings are words with frequencies 9_000 vs 10_000; a
        // context boost (max 10_000 > the 1_000 gap) flips the order.
        val words = trie("ሰላም" to 9_000, "ሠላም" to 10_000)
        val ranked = rankAmharic(
            listOf("ሰላም", "ሠላም"), words,
            ngramNext = mapOf("ሰላም" to 255)
        )
        assertEquals(listOf("ሰላም", "ሠላም"), ranked)
        // Same input without context keeps the frequency order -- proving
        // the flip above came from the boost, and that an empty map is a
        // strict no-op.
        assertEquals(
            listOf("ሠላም", "ሰላም"),
            rankAmharic(listOf("ሰላም", "ሠላም"), words)
        )
    }

    @Test
    fun ngramBoostAppliesToCompletions() {
        val words = trie("ቤተሰብ" to 9_000, "ቤተመንግስት" to 10_000)
        val ranked = rankAmharic(
            listOf("ቤተ"), words,
            ngramNext = mapOf("ቤተሰብ" to 200)
        )
        assertEquals("ቤተሰብ", ranked.first { it != "ቤተ" })
    }

    @Test
    fun ngramBoostCannotPromoteAcrossTiers() {
        // ቤቱ is an exact reading (weakest possible frequency); ቤተሰብ is only
        // a completion (of the alternate reading ቤተ). Even a maximal context
        // boost on a maximal-frequency completion must not displace the
        // exact word from the top.
        val words = trie("ቤቱ" to 1, "ቤተሰብ" to 30_000)
        val ranked = rankAmharic(
            listOf("ቤተ", "ቤቱ"), words,
            ngramNext = mapOf("ቤተሰብ" to 255)
        )
        assertEquals("ቤቱ", ranked.first())
    }
}
