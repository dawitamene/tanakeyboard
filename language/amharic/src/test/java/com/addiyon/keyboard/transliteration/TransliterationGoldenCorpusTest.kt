package com.addiyon.keyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

class TransliterationGoldenCorpusTest {

    @Test
    fun commonWordsTransliterateAsExpected() {
        val cases = mapOf(
            "selam" to "ሰላም",
            "ameseginalehu" to "አመሰጊናለሁ",
            "ethiopia" to "እትሂኦፒአ",
            "addis" to "አድዲስ",
            "abeba" to "አበባ",
            "fkr" to "ፍክር",
            "se`at" to "ሰዓት",
            "silt" to "ሲልት",
            "yene" to "የነ",
            "bet" to "በት"
        )

        for ((latin, fidel) in cases) {
            assertEquals(latin, fidel, Transliterator.transliterate(latin))
        }
    }

    @Test
    fun eachVowelOrderWorksAfterAConsonant() {
        val cases = mapOf(
            "le" to "ለ",
            "lu" to "ሉ",
            "li" to "ሊ",
            "la" to "ላ",
            "lie" to "ሌ",
            "l" to "ል",
            "lo" to "ሎ",
            "lua" to "ሏ"
        )

        for ((latin, fidel) in cases) {
            assertEquals(latin, fidel, Transliterator.transliterate(latin))
        }
    }

    @Test
    fun passThroughCharactersArePreserved() {
        assertEquals("ሰላም 2026!", Transliterator.transliterate("selam 2026!"))
        assertEquals("ሰላም፣ አበባ።", Transliterator.transliterate("selam, abeba."))
    }
}
