package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.suggestion.CandidateRanker.DictionaryWord
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicPrefixCompletionTest {

    /** A trie-backed lookup over a small stem lexicon, folded like the real one. */
    private fun lookupOf(vararg words: Pair<String, Int>): (String, Int) -> List<DictionaryWord> {
        val trie = WordTrie.build(words.toList(), EthiopicNormalizer::normalize)
        return { prefix, limit ->
            trie.suggestionEntries(prefix, limit).map { DictionaryWord(it.word, it.frequency) }
        }
    }

    private fun complete(
        prefix: String,
        limit: Int = 3,
        alreadyFound: List<DictionaryWord> = emptyList(),
        lookup: (String, Int) -> List<DictionaryWord>,
    ) = AmharicPrefixCompletion.complete(prefix, limit, alreadyFound, lookup)

    @Test
    fun stripsThePrefixCompletesTheStemAndReattaches() {
        // የቤ finds nothing directly, but ቤ completes to ቤት/ቤቶች -> የቤት/የቤቶች.
        val lookup = lookupOf("ቤት" to 800, "ቤቶች" to 400)
        val words = complete("የቤ", lookup = lookup).map { it.word }
        assertEquals(listOf("የቤት", "የቤቶች"), words)
    }

    @Test
    fun synthesizedFrequencyIsDiscountedNotTheStemFrequency() {
        val lookup = lookupOf("ቤት" to 800)
        val result = complete("የቤ", lookup = lookup).single()
        assertTrue(
            "expected a discounted frequency, got ${result.frequency}",
            result.frequency in 1 until 800
        )
    }

    @Test
    fun longerCompoundPrefixStripsBeforeItsSingleCharPrefix() {
        // በየቀ: the distributive በየ strips first, so በየ+ቀን leads; the single
        // በ strip (remainder የቀ) contributes nothing extra here.
        val lookup = lookupOf("ቀን" to 500)
        val words = complete("በየቀ", lookup = lookup).map { it.word }
        assertEquals(listOf("በየቀን"), words)
    }

    @Test
    fun multiplePrefixReadingsBothContributeInOrder() {
        // ከየ is both the compound ከየ (remainder needed) and ከ + የ…: with only
        // ከየስ typed, the compound strips to ስራ; ከ strips to የስ, completing
        // from stored የስልክ.
        val lookup = lookupOf("ስራ" to 900, "የስልክ" to 300)
        val words = complete("ከየስ", lookup = lookup).map { it.word }
        assertEquals(listOf("ከየስራ", "ከየስልክ"), words)
    }

    @Test
    fun dedupesAgainstDirectResultsByFoldedKey() {
        // The direct pass already produced የሀገር (spelled ሀ); the synthesized
        // የ+ሃገር folds to the same key and must not duplicate it.
        val lookup = lookupOf("ሃገር" to 700, "ሀብት" to 200)
        val words = complete(
            "የሀ",
            alreadyFound = listOf(DictionaryWord("የሀገር", 50)),
            lookup = lookup,
        ).map { it.word }
        assertEquals(listOf("የሀብት"), words)
    }

    @Test
    fun neverSynthesizesTheTypedPrefixItself() {
        // ስራ completes ስ exactly at የስራ == the typed prefix -> skipped;
        // only the longer completion surfaces.
        val lookup = lookupOf("ስራ" to 900, "ስራዎች" to 100)
        val words = complete("የስራ", lookup = lookup).map { it.word }
        assertEquals(listOf("የስራዎች"), words)
    }

    @Test
    fun bareOrUnknownPrefixSynthesizesNothing() {
        val lookup = lookupOf("ቤት" to 800)
        assertEquals(emptyList<DictionaryWord>(), complete("የ", lookup = lookup))
        assertEquals(emptyList<DictionaryWord>(), complete("ተቤ", lookup = lookup))
        assertEquals(emptyList<DictionaryWord>(), complete("", lookup = lookup))
    }

    @Test
    fun honorsTheLimit() {
        val lookup = lookupOf("ቤት" to 800, "ቤቶች" to 400, "ቤተሰብ" to 300)
        assertEquals(1, complete("የቤ", limit = 1, lookup = lookup).size)
        assertEquals(0, complete("የቤ", limit = 0, lookup = lookup).size)
    }
}
