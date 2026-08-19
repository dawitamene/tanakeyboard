package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicMorphologyAnalyzerTest {
    @Test
    fun analyzesNominalSurfacesWithPrefixAndSuffixes() {
        val analyses = AmharicMorphologyAnalyzer.analyze("የቤቶቻችንን")
        assertTrue(analyses.isNotEmpty())
        val best = analyses.first()
        assertEquals("የ-ቤት-ኦች-አችን-ን", best.segmentation)
        assertEquals("ቤት", best.lemma)
        assertEquals(PartOfSpeech.NOUN, best.partOfSpeech)
        assertTrue(best.uniMorphTag.contains("PL"))
        assertTrue(best.uniMorphTag.contains("ACC"))
        assertTrue(best.uniMorphTag.contains("PSS1P"))
    }

    @Test
    fun analyzesCopulaCliticForms() {
        val analyses = AmharicMorphologyAnalyzer.analyze("ሰውነው")
        assertTrue(analyses.isNotEmpty())
        val best = analyses.first()
        assertEquals("ሰው-ነው", best.segmentation)
        assertTrue(best.uniMorphTag.contains("COP"))
    }

    @Test
    fun segmentProvidesCleanHyphenatedMorphemes() {
        val seg = AmharicMorphologyAnalyzer.segment("ለሰዎች")
        assertEquals("ለ-ሰው-ዎች", seg)
    }
}
