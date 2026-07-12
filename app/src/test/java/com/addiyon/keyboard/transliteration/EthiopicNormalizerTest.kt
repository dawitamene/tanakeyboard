package com.addiyon.keyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

class EthiopicNormalizerTest {

    private fun n(s: String) = EthiopicNormalizer.normalize(s)

    @Test
    fun foldsTheHomophoneConsonantSeriesOntoTheirCanonicalSeries() {
        assertEquals("ሀሁሂሀሄህሆ", n("ሐሑሒሓሔሕሖ"))
        assertEquals("ሀሁሂሀሄህሆ", n("ኀኁኂኃኄኅኆ"))
        assertEquals("ሰሱሲሳሴስሶሷ", n("ሠሡሢሣሤሥሦሧ"))
        assertEquals("አኡኢአኤእኦ", n("ዐዑዒዓዔዕዖ"))
        assertEquals("ጸጹጺጻጼጽጾ", n("ፀፁፂፃፄፅፆ"))
    }

    @Test
    fun mergesOrderOneAndOrderFourOnLaryngeals() {
        // ሀ/ሃ and አ/ኣ sound identical in Amharic, so both spellings fold.
        assertEquals('ሀ', EthiopicNormalizer.normalize('ሃ'))
        assertEquals('አ', EthiopicNormalizer.normalize('ኣ'))
        // ...but the merge is laryngeal-only: ለ vs ላ is a real vowel contrast.
        assertEquals("ለላ", n("ለላ"))
    }

    @Test
    fun foldsTheLabializedHwaVariant() {
        assertEquals('ኋ', EthiopicNormalizer.normalize('ሗ'))
    }

    @Test
    fun spellingVariantsOfTheSameWordNormalizeEqual() {
        assertEquals(n("ሀገር"), n("ሃገር"))
        assertEquals(n("ሀገር"), n("ሐገር"))
        assertEquals(n("ሀገር"), n("ኀገር"))
        assertEquals(n("ጸሀይ"), n("ፀሐይ"))
        assertEquals(n("አለም"), n("ዓለም"))
        assertEquals(n("ሰራ"), n("ሠራ"))
    }

    @Test
    fun leavesEverythingElseAlone() {
        // ኸ writes a distinct sound in Amharic -- deliberately not folded.
        assertEquals("ኸበደ", n("ኸበደ"))
        // Canonical fidel, Latin, digits, punctuation: all identity.
        assertEquals("ሰላም ነው። abc 123", n("ሰላም ነው። abc 123"))
    }
}
