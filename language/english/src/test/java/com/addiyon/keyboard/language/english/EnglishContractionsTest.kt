package com.addiyon.keyboard.language.english

import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.matchCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishContractionsTest {

    @Test
    fun imExpandsToImWithHighFrequency() {
        val frequencies = mapOf("I'm" to 4_386_306)
        val candidates = EnglishContractions.candidatesFor("im") { frequencies[it] }
        assertEquals(1, candidates.size)
        assertEquals("I'm", candidates.first().word)
        assertEquals(4_386_306, candidates.first().frequency)
    }

    @Test
    fun illExpandsToIllWithHighFrequency() {
        val frequencies = mapOf("I'll" to 1_150_000)
        val candidates = EnglishContractions.candidatesFor("ill") { frequencies[it] }
        assertEquals(1, candidates.size)
        assertEquals("I'll", candidates.first().word)
        assertEquals(1_150_000, candidates.first().frequency)
    }

    @Test
    fun commonContractionsAreCovered() {
        val cases = listOf(
            "id" to "I'd",
            "ive" to "I've",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "didnt" to "didn't",
            "doesnt" to "doesn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
            "youre" to "you're",
            "theyre" to "they're",
            "were" to "we're",
            "youve" to "you've",
            "theyve" to "they've",
            "weve" to "we've",
            "youll" to "you'll",
            "theyll" to "they'll",
            "shell" to "she'll",
            "hell" to "he'll",
            "well" to "we'll",
            "shes" to "she's",
            "hes" to "he's",
            "thats" to "that's",
            "whats" to "what's",
            "wheres" to "where's",
            "hows" to "how's",
            "theres" to "there's",
            "heres" to "here's",
            "whos" to "who's",
            "couldve" to "could've",
            "shouldve" to "should've",
            "wouldve" to "would've",
            "mightve" to "might've",
            "mustve" to "must've",
            "mustnt" to "mustn't",
            "neednt" to "needn't",
            "aint" to "ain't",
            "lets" to "let's",
            "youd" to "you'd",
            "theyd" to "they'd",
            "shed" to "she'd",
            "hed" to "he'd",
            "wed" to "we'd",
            "itll" to "it'll",
            "its" to "it's"
        )
        for ((shortcut, expected) in cases) {
            val expansions = EnglishContractions.contractionsFor(shortcut)
            assertTrue("Expected $shortcut to expand to $expected, got $expansions", expected in expansions)
        }
    }

    @Test
    fun rankingImPlacesImAheadOfGeneralCompletions() {
        val contractions = EnglishContractions.candidatesFor("im") { 4_386_306 }
        val baseEntries = listOf(
            CandidateRanker.DictionaryWord("important", 164_386),
            CandidateRanker.DictionaryWord("imagine", 71_873),
            CandidateRanker.DictionaryWord("im", 6_553)
        )
        val pool = (contractions + baseEntries).distinctBy { it.word }
        val ranked = CandidateRanker.rankByContext(pool, emptyMap(), { it.lowercase() }, 15)
        assertEquals("I'm", ranked.first())
    }

    @Test
    fun rankingIllIncludesBothIllAndIll() {
        val contractions = EnglishContractions.candidatesFor("ill") { 1_150_000 }
        val baseEntries = listOf(
            CandidateRanker.DictionaryWord("ill", 24_763),
            CandidateRanker.DictionaryWord("illegal", 19_658),
            CandidateRanker.DictionaryWord("illness", 8_825)
        )
        val pool = (contractions + baseEntries).distinctBy { it.word }
        val ranked = CandidateRanker.rankByContext(pool, emptyMap(), { it.lowercase() }, 15)
        assertEquals("I'll", ranked[0])
        assertEquals("ill", ranked[1])
    }

    @Test
    fun casingPreservationMatchesExpectedPatterns() {
        assertEquals("I'm", matchCase("im", "I'm"))
        assertEquals("I'm", matchCase("Im", "I'm"))
        assertEquals("I'M", matchCase("IM", "I'm"))

        assertEquals("I'll", matchCase("ill", "I'll"))
        assertEquals("I'll", matchCase("Ill", "I'll"))
        assertEquals("I'LL", matchCase("ILL", "I'll"))

        assertEquals("don't", matchCase("dont", "don't"))
        assertEquals("Don't", matchCase("Dont", "don't"))
        assertEquals("DON'T", matchCase("DONT", "don't"))

        assertEquals("can't", matchCase("cant", "can't"))
        assertEquals("Can't", matchCase("Cant", "can't"))
        assertEquals("CAN'T", matchCase("CANT", "can't"))
    }

    @Test
    fun contextBoostForContractionsSupportsApostropheStrippedKeys() {
        val candidates = listOf(
            CandidateRanker.DictionaryWord("I'm", 4_386_306),
            CandidateRanker.DictionaryWord("important", 164_386)
        )
        val contextWithApostrophe = mapOf("i'm" to 200)
        val ranked1 = CandidateRanker.rankByContext(candidates, contextWithApostrophe, { it.lowercase() }, 15)
        assertEquals("I'm", ranked1.first())

        val contextWithoutApostrophe = mapOf("im" to 200)
        val ranked2 = CandidateRanker.rankByContext(candidates, contextWithoutApostrophe, { it.lowercase() }, 15)
        assertEquals("I'm", ranked2.first())
    }

    @Test
    fun contractionMatchingRecognizesContractions() {
        val contractions = EnglishContractions.candidatesFor("dont") { 100 }
        assertTrue(contractions.any { it.word.equals("don't", ignoreCase = true) })
    }
}
