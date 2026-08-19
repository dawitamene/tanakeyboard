package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicCopulaTest {
    @Test
    fun generatesNominalCopulaCliticForms() {
        val sewLexeme = AmharicNounMorphology.Lexeme(
            kind = 0,
            surface = "ሰው",
            features = "'' [pos=N]",
            frequency = 5000,
        )
        val barePrefix = NominalPrefixContext("")
        val completions = NominalRuleGraph.generate(sewLexeme, barePrefix, "ሰውነ", 20).map { it.word }
        assertTrue(completions.contains("ሰውነው") || completions.contains("ሰውነኝ"))
    }
}
