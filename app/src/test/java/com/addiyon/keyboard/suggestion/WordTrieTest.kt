package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTrieTest {

    private fun trie(vararg words: Pair<String, Int>) = WordTrie.build(words.toList())

    @Test
    fun ranksByFrequencyDescendingAndHonorsLimit() {
        val t = trie("the" to 100, "them" to 50, "then" to 80, "there" to 10)
        assertEquals(listOf("the", "then", "them"), t.suggestions("the", limit = 3))
        assertEquals(listOf("the"), t.suggestions("the", limit = 1))
    }

    @Test
    fun emptyPrefixOrNoMatchReturnsEmpty() {
        val t = trie("the" to 100)
        assertEquals(emptyList<String>(), t.suggestions(""))
        assertEquals(emptyList<String>(), t.suggestions("xyz"))
    }

    @Test
    fun prefixMatchesRegardlessOfTypedCase() {
        val t = trie("England" to 500)
        assertEquals(listOf("England"), t.suggestions("engl"))
        assertEquals(listOf("England"), t.suggestions("ENGL"))
        assertEquals(listOf("England"), t.suggestions("Engl"))
    }

    @Test
    fun returnsCanonicalCasingNotThePrefixCasing() {
        val t = trie("London" to 300, "lonely" to 900)
        // "lon" reaches both; each keeps its stored canonical form.
        assertEquals(listOf("lonely", "London"), t.suggestions("lon", limit = 2))
    }

    @Test
    fun collisionOnLowercaseKeyKeepsHigherFrequencyForm() {
        // Same lowercased key "polish"; the higher-frequency form wins.
        val a = trie("polish" to 900, "Polish" to 100)
        assertEquals(listOf("polish"), a.suggestions("pol"))
        val b = trie("polish" to 100, "Polish" to 900)
        assertEquals(listOf("Polish"), b.suggestions("pol"))
    }

    @Test
    fun bestFirstMatchesBruteForceTopN() {
        // A wider fixture: best-first search must agree with a plain sort.
        val words = listOf(
            "apple" to 90, "apply" to 70, "apricot" to 65, "april" to 88,
            "apt" to 40, "apron" to 55, "append" to 30, "appeal" to 77,
            "approve" to 60, "apex" to 25
        )
        val t = WordTrie.build(words)
        val expected = words.filter { it.first.startsWith("ap") }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        assertEquals(expected, t.suggestions("ap", limit = 3))
    }

    @Test
    fun exactWordIsAlsoASuggestionForItsOwnPrefix() {
        val t = trie("go" to 100, "good" to 60, "google" to 40)
        assertEquals(listOf("go", "good", "google"), t.suggestions("go"))
    }

    // ---- sorted streaming build (the Sequence overload) ---------------------

    @Test
    fun sequenceBuildRejectsUnsortedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            WordTrie.build(sequenceOf("banana" to 1, "apple" to 1))
        }
        // A word followed by its own proper prefix is also out of order.
        assertThrows(IllegalArgumentException::class.java) {
            WordTrie.build(sequenceOf("apples" to 1, "apple" to 1))
        }
        // Sortedness is over the LOWERCASED key: "Apple" then "apple" is a
        // duplicate key, not a violation.
        WordTrie.build(sequenceOf("Apple" to 1, "apple" to 2))
    }

    @Test
    fun sequenceBuildAcceptsSortedInputAndMatchesListBuild() {
        val words = listOf("go" to 100, "good" to 60, "google" to 40, "gopher" to 5)
        val t = WordTrie.build(words.asSequence())
        assertEquals(listOf("go", "good", "google"), t.suggestions("go"))
        assertEquals(5, t.frequencyOf("gopher"))
    }

    @Test
    fun sequenceBuildDuplicateKeyKeepsHigherFrequencyForm() {
        val t = WordTrie.build(sequenceOf("Polish" to 900, "polish" to 100))
        assertEquals(listOf("Polish"), t.suggestions("pol"))
        assertEquals(900, t.frequencyOf("polish"))
    }

    @Test
    fun wordsWithNonLetterCharsAreStoredAndCompleted() {
        // Amharic abbreviations carry '.' and '/' on trie edges.
        val t = trie("ዓ.ም" to 10_000, "ዓመት" to 5_000)
        assertEquals(listOf("ዓ.ም", "ዓመት"), t.suggestions("ዓ", limit = 2))
        assertEquals(10_000, t.frequencyOf("ዓ.ም"))
    }

    // ---- frequencyOf --------------------------------------------------------

    @Test
    fun frequencyOfReturnsTheStoredFrequencyForAnExactWord() {
        val t = trie("selam" to 42)
        assertEquals(42, t.frequencyOf("selam"))
    }

    @Test
    fun frequencyOfIsNullForAMiss() {
        val t = trie("selam" to 42)
        assertEquals(null, t.frequencyOf("xyz"))
    }

    @Test
    fun frequencyOfIsNullForAPrefixThatIsNotItselfAWord() {
        val t = trie("selam" to 42)
        assertEquals(null, t.frequencyOf("sel"))
    }

    @Test
    fun frequencyOfIsNullForAnEmptyWord() {
        val t = trie("selam" to 42)
        assertEquals(null, t.frequencyOf(""))
    }

    @Test
    fun frequencyOfMatchesCaseInsensitively() {
        val t = trie("England" to 500)
        assertEquals(500, t.frequencyOf("england"))
        assertEquals(500, t.frequencyOf("ENGLAND"))
    }

    // ---- Ethiopic homoglyph-folded keying (the Amharic keyChar) ------------

    private fun amharicTrie(vararg words: Pair<String, Int>) =
        WordTrie.build(words.toList(), EthiopicNormalizer::normalize)

    @Test
    fun ethiopicPrefixMatchesAcrossHomoglyphSpellings() {
        // ኃይል is stored under the folded path ሀይል, so any variant spelling of
        // the prefix reaches it -- the fidel analogue of case-insensitivity.
        val t = amharicTrie("ኃይል" to 500)
        assertEquals(listOf("ኃይል"), t.suggestions("ሀይ"))
        assertEquals(listOf("ኃይል"), t.suggestions("ሃይ"))
        assertEquals(listOf("ኃይል"), t.suggestions("ሐይ"))
        assertEquals(listOf("ኃይል"), t.suggestions("ኃይ"))
    }

    @Test
    fun ethiopicSuggestionsReturnTheStoredCanonicalSpelling() {
        val t = amharicTrie("ፀሐይ" to 400, "ሰላም" to 900)
        // Typed with plain ጸ/ሀ, suggested with the stored ፀ/ሐ spelling.
        assertEquals(listOf("ፀሐይ"), t.suggestions("ጸሀ"))
    }

    @Test
    fun ethiopicFrequencyOfMatchesAcrossHomoglyphSpellings() {
        val t = amharicTrie("ሀገር" to 6_000)
        assertEquals(6_000, t.frequencyOf("ሃገር"))
        assertEquals(6_000, t.frequencyOf("ሐገር"))
        assertEquals(6_000, t.frequencyOf("ኀገር"))
    }

    @Test
    fun ethiopicCollisionKeepsHigherFrequencySpelling() {
        // Same folded key; the higher-frequency spelling wins the display
        // form -- exactly the "polish"/"Polish" rule over fidel.
        val t = amharicTrie("ሃገር" to 100, "ሀገር" to 900)
        assertEquals(listOf("ሀገር"), t.suggestions("ኀገ"))
    }

    @Test
    fun defaultKeyDoesNotFoldEthiopicVariants() {
        // Without the normalizer (the English trie's default), variant
        // spellings stay distinct entries.
        val t = trie("ሀገር" to 900)
        assertEquals(emptyList<String>(), t.suggestions("ሃገ"))
    }

    // ---- fuzzySuggestions -------------------------------------------------

    private fun WordTrie.fuzzyWords(prefix: String, maxEdits: Int, limit: Int = 3) =
        fuzzySuggestions(prefix, maxEdits, limit).map { it.word }

    @Test
    fun fuzzyCorrectsASingleInsertionTypo() {
        // "informtion" (missing an 'a') is one edit from "information".
        val t = trie("information" to 500, "informer" to 50)
        assertEquals(listOf("information"), t.fuzzyWords("informtion", maxEdits = 1))
    }

    @Test
    fun fuzzyCorrectsATransposition() {
        // Damerau: "teh" -> "the" is a single adjacent transposition, whereas
        // reaching "the" via plain substitutions would cost 2 ("teh"[e/h] ->
        // "th_" then "the"), so this only matches with transposition support.
        val t = trie("the" to 900, "cat" to 100)
        assertEquals(listOf("the"), t.fuzzyWords("teh", maxEdits = 1))
    }

    @Test
    fun fuzzyStillCompletesBeyondTheTypedPrefix() {
        // A one-edit prefix should still pull in longer completions.
        val t = trie("information" to 500, "informational" to 120)
        assertEquals(
            listOf("information", "informational"),
            t.fuzzyWords("informtion", maxEdits = 1, limit = 2)
        )
    }

    @Test
    fun fuzzyRanksByEditDistanceThenFrequency() {
        // "cat" (exact prefix, distance 0) must outrank "cot"/"car" (distance
        // 1) regardless of frequency; among distance-1, higher frequency wins.
        val t = trie("cat" to 10, "cot" to 100, "car" to 500)
        val matches = t.fuzzySuggestions("cat", maxEdits = 1, limit = 3)
        assertEquals(listOf("cat", "car", "cot"), matches.map { it.word })
        assertEquals(0, matches[0].editDistance)
        assertEquals(1, matches[1].editDistance)
    }

    @Test
    fun fuzzyReturnsNothingBeyondBudget() {
        // Two edits away with only a one-edit budget -> no match.
        val t = trie("house" to 100)
        assertEquals(emptyList<String>(), t.fuzzyWords("mouze", maxEdits = 1))
    }

    @Test
    fun fuzzyWithZeroBudgetIsEmpty() {
        val t = trie("the" to 100)
        assertEquals(emptyList<String>(), t.fuzzyWords("the", maxEdits = 0))
        assertEquals(emptyList<String>(), t.fuzzyWords("", maxEdits = 1))
    }

    @Test
    fun fuzzyUsesInjectedSubstitutionCost() {
        // A cost model that treats a<->o as free but everything else expensive:
        // "cot" should match "cat" for free, "cut" should not (cost 2).
        val cheapAO = WordTrie.SubstitutionCost { a, b ->
            when {
                a == b -> 0
                setOf(a, b) == setOf('a', 'o') -> 0
                else -> 2
            }
        }
        val t = trie("cat" to 100)
        assertEquals(
            listOf("cat"),
            t.fuzzySuggestions("cot", maxEdits = 1, substitutionCost = cheapAO).map { it.word }
        )
        assertEquals(
            emptyList<String>(),
            t.fuzzySuggestions("cut", maxEdits = 1, substitutionCost = cheapAO).map { it.word }
        )
    }

    @Test
    fun amharicCostModelSurfacesTheIntendedVowelNearMiss() {
        // User is typing toward የተለያዩ but the last syllable is still on the bare
        // ይ (የተለይ) before the vowel lands on ያ (የተለያ). With the fidel cost model
        // that's ONE cheap same-family edit, so the word should surface; a
        // wrong-consonant word (የተለሽ…) is two-cost and must not.
        val cost = WordTrie.SubstitutionCost(AmharicTable::fidelSubstitutionCost)
        val indel = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST
        val t = trie("የተለያዩ" to 500, "የተለያየ" to 300, "የተለሽም" to 400)
        val words = t.fuzzySuggestions(
            "የተለይ", maxEdits = 1, limit = 5,
            substitutionCost = cost, insertCost = indel, deleteCost = indel,
        ).map { it.word }
        assertTrue("የተለያዩ" in words)
        assertTrue("የተለያየ" in words)
        assertTrue("የተለሽም" !in words)
    }
}
