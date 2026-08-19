package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicVerbalNounTest {
    @Test
    fun generatesVerbalNounPossessiveAndPluralForms() {
        val mehedeLexeme = AmharicNounMorphology.Lexeme(
            kind = 0,
            surface = "መሄድ",
            features = "'' [pos=N_V]",
            frequency = 3000,
            morphBits = NominalFeatureBits.PRODUCTIVE or NominalFeatureBits.POS_VERBAL_NOUN or NominalFeatureBits.ORDINARY_PLURAL or NominalFeatureBits.DEFINITE or NominalFeatureBits.POSSESSIVE or NominalFeatureBits.ALL_ADPOSITIONS,
        )
        val barePrefix = NominalPrefixContext("")
        val completions = NominalRuleGraph.generate(mehedeLexeme, barePrefix, "መሄ", 20).map { it.word }
        assertTrue(completions.contains("መሄዴ") || completions.contains("መሄዱ") || completions.contains("መሄዶች"))
    }
}
