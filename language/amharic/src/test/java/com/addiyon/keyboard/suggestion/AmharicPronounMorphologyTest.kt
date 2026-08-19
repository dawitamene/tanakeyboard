package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicPronounMorphologyTest {
    @Test
    fun generatesContractedAndUncontractedPersonalPronounForms() {
        val eneLexeme = AmharicNounMorphology.Lexeme(
            kind = 0,
            surface = "እኔ",
            features = "'' [pos=PRON]",
            frequency = 1000,
            morphBits = NominalFeatureBits.PRODUCTIVE or NominalFeatureBits.POS_PRONOUN or NominalFeatureBits.POSSESSIVE or NominalFeatureBits.ALL_ADPOSITIONS or NominalFeatureBits.GENITIVE,
        )
        val yePrefix = NominalPrefixContext("የ", adposition = "የ")
        val completions = NominalRuleGraph.generate(eneLexeme, yePrefix, "የ", 10).map { it.word }
        assertTrue(completions.contains("የኔ") || completions.contains("የእኔ"))
    }

    @Test
    fun generatesDemonstrativeAdpositionForms() {
        val yihLexeme = AmharicNounMorphology.Lexeme(
            kind = 0,
            surface = "ይህ",
            features = "'' [pos=DET]",
            frequency = 1000,
            morphBits = NominalFeatureBits.PRODUCTIVE or NominalFeatureBits.POS_PRONOUN or NominalFeatureBits.ALL_ADPOSITIONS or NominalFeatureBits.GENITIVE,
        )
        val bePrefix = NominalPrefixContext("በ", adposition = "በ")
        val completions = NominalRuleGraph.generate(yihLexeme, bePrefix, "በ", 10).map { it.word }
        assertTrue(completions.contains("በዚህ"))
    }
}
