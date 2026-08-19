package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicGuesserTest {
    @Test
    fun generatesBoundedMorphologicalGuessesForUnknownRoots() {
        val guesses = AmharicGuesser.guess("ለኮምፒውተር", 10)
        assertTrue(guesses.isNotEmpty())
        val words = guesses.map { it.word }
        assertTrue(words.contains("ለኮምፒውተር"))
        assertTrue(words.contains("ለኮምፒውተርን") || words.contains("ለኮምፒውተርዎች"))
        assertEquals(CandidateRanker.CandidateSource.GUESSER_MORPHOLOGY, guesses.first().source)
    }

    @Test
    fun rejectsPunctuationOrInvalidStems() {
        val guesses = AmharicGuesser.guess("123", 5)
        assertTrue(guesses.isEmpty())
    }
}
