package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicNounMorphologyTest {
    private fun noun(
        surface: String,
        features: String = "'' [pos=N]",
        kind: Int = 0,
        frequency: Int = 800,
    ) = AmharicNounMorphology.Lexeme(kind, surface, features, frequency)

    private fun complete(
        typed: String,
        vararg lexemes: AmharicNounMorphology.Lexeme,
    ): List<String> = AmharicNounMorphology.complete(
        typed = typed,
        lexemes = lexemes.toList(),
        limit = 100,
    ).map { it.word }

    @Test
    fun generatesPrefixAndAccusativeFromTheBaseLexeme() {
        val person = noun("ሰው", "'' [g=m,pos=N]")
        assertTrue("የሰው" in complete("የሰ", person))
        assertTrue("ሰውን" in complete("ሰውን", person))
        assertTrue("የሰውን" in complete("የሰውን", person))
        assertTrue("ለሰው" in complete("ለሰ", person))
    }

    @Test
    fun queriesLexemesForBothBareAndPrefixedPartialStems() {
        val bare = AmharicNounMorphology.query("ቤ")
        val prefixed = AmharicNounMorphology.query("የሰ")
        assertEquals("ቤ", bare.completionStemPrefix)
        assertEquals("የ", prefixed.prefix)
        assertEquals("ሰ", prefixed.completionStemPrefix)
    }

    @Test
    fun appliesHornMorphoSuffixOrder() {
        val house = noun("ቤት", "'' [pos=N,-h]")
        val words = complete("የቤ", house)
        assertTrue("የቤቶች" in words)
        assertTrue("የቤቱ" in words)
        assertTrue("የቤቶቹን" in words)
        assertFalse("የቤትንው" in words)
    }

    @Test
    fun alternateHornMorphoNounClassUsesAnPlural() {
        val teacher = noun("መምህር", "'' [pos=N]", kind = 1)
        assertTrue("መምህራን" in complete("መምህራ", teacher))
    }

    @Test
    fun vowelFinalNounsUseWochPlural() {
        val place = noun("ቦታ", "'' [pos=N,-h]")
        assertTrue("ቦታዎች" in complete("ቦታዎ", place))
    }

    @Test
    fun parsesGeneratedFormsBackToTheirHornMorphoLexeme() {
        val person = noun("ሰው", "'' [g=m,pos=N]")
        val analyses = AmharicNounMorphology.analyze("የሰውን", listOf(person))
        assertEquals(listOf("ሰው"), analyses.map { it.surface })
    }

    @Test
    fun honorsHornMorphoBlockingFeatures() {
        val noPlural = noun("ምሳሌ", "'' [pos=N,-pl]")
        val noPrefix = noun("እራስ", "ራስ [pos=N,adp=0]")
        val noDefinite = noun("ራቁት", "'' [pos=ADJ,-def,p=0]")
        assertFalse(complete("ምሳሌዎ", noPlural).any { it.contains("ዎች") })
        assertTrue(complete("የእ", noPrefix).isEmpty())
        assertFalse("ራቁቱ" in complete("ራቁቱ", noDefinite))
    }

    @Test
    fun prefixFlagsDoNotAccidentallyBlockUnprefixedPossessives() {
        val noAdposition = noun("እራስ", "ራስ [pos=N,adp=0]")
        val noGenitive = noun("ገዛ", "'' [pos=ADJ,-gen]")
        val noDistributive = noun("ራስ", "'' [pos=N,-dis]")
        assertTrue("እራሱ" in complete("እራሱ", noAdposition))
        assertTrue(complete("የገ", noGenitive).isEmpty())
        assertTrue(complete("በየራ", noDistributive).isEmpty())
    }

    @Test
    fun minusDefStillAllowsTheSameSurfaceAsAThirdPersonPossessive() {
        val usuallyPossessed = noun("ራቁት", "'' [pos=ADJ,-def]")
        assertTrue("ራቁቱ" in complete("ራቁቱ", usuallyPossessed))
    }

    @Test
    fun properNamesDoNotReceivePluralOrPossessiveForms() {
        val abebe = noun("አበበ", features = "", kind = 2)
        val words = complete("አበበ", abebe)
        assertEquals(listOf("አበበ", "አበበን"), words)
    }

    @Test
    fun generatedFrequencyIsDiscountedBelowTheStem() {
        val result = AmharicNounMorphology.complete(
            typed = "የሰው",
            lexemes = listOf(noun("ሰው", frequency = 800)),
            limit = 3,
        ).first { it.word == "የሰው" }
        assertTrue(result.frequency in 1 until 800)
    }
}
