package com.addiyon.tanakeyboard.suggestion

import org.junit.Assert.assertEquals
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
}
