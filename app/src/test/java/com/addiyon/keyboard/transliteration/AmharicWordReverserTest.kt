package com.addiyon.keyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmharicWordReverserTest {

    @Test
    fun reversesAWholeWordThatRoundTrips() {
        val fidel = Transliterator.transliterate("selam")
        val reversed = AmharicWordReverser.reverse(fidel)
        assertNotNull(reversed)
        assertEquals(fidel, Transliterator.transliterate(reversed!!))
    }

    @Test
    fun everyFamilyFormRoundTrips() {
        for ((key, family) in AmharicTable.families) {
            for (char in family.forms) {
                val reversed = AmharicWordReverser.reverse(char.toString())
                assertNotNull("no reversal for '$char' (family \"$key\")", reversed)
                assertEquals(
                    "reversal of '$char' didn't round-trip",
                    char.toString(),
                    Transliterator.transliterate(reversed!!)
                )
            }
            family.ua?.let { ua ->
                val reversed = AmharicWordReverser.reverse(ua.toString())
                assertNotNull("no reversal for ua form '$ua' (family \"$key\")", reversed)
                assertEquals(ua.toString(), Transliterator.transliterate(reversed!!))
            }
        }
    }

    @Test
    fun emptyStringIsNull() {
        assertNull(AmharicWordReverser.reverse(""))
    }

    @Test
    fun unreachableFidelIsRejected() {
        // The velar ኀ series has no direct Latin spelling -- it's only
        // reachable as a suggestion-strip alternate of "h" -- so a word made
        // of it can't be reversed and must be rejected outright, not
        // half-guessed.
        assertNull(AmharicWordReverser.reverse(AmharicTable.velarFamily.bare.toString()))
    }

    @Test
    fun aWordWhoseCharwiseGuessMisreadsAsADigraphIsRejected() {
        // ስ ("s" bare) followed by ሀ ("h" bare) reverses char-by-char to the
        // guess "s"+"h" = "sh" -- which transliterates GREEDILY as the ሽ
        // digraph, not back to ስሀ. Round-trip verification is exactly what
        // catches this: it must reject the guess rather than resume into the
        // wrong word.
        assertNull(AmharicWordReverser.reverse("ስሀ"))
    }
}
