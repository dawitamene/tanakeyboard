package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicLightVerbTest {
    @Test
    fun completesLightVerbPhrasesForCommonPreverbs() {
        val zimCompletions = AmharicLightVerb.complete("ዝም", 20).map { it.word }
        assertTrue(zimCompletions.contains("ዝም አለ"))
        assertTrue(zimCompletions.contains("ዝም ይላል"))
        assertTrue(zimCompletions.contains("ዝም ብሎ"))

        val bdgCompletions = AmharicLightVerb.complete("ብድግ ብ", 5).map { it.word }
        assertTrue(bdgCompletions.contains("ብድግ ብሎ"))
    }

    @Test
    fun recognizesPreverbsAccurately() {
        assertTrue(AmharicLightVerb.isPreverb("ዝም"))
        assertTrue(AmharicLightVerb.isPreverb("ቁጭ"))
        assertTrue(AmharicLightVerb.isPreverb("ደስ"))
    }
}
