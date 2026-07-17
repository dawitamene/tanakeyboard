package com.addiyon.keyboard.composing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumableWordTest {

    // ---- Latin (English composer) ----

    @Test
    fun latinWordAtEndIsExtracted() {
        assertEquals("cana", ResumableWord.trailingLatinWord("hello cana"))
        assertEquals("cana", ResumableWord.trailingLatinWord("cana"))
    }

    @Test
    fun apostropheBelongsToTheLatinWord() {
        assertEquals("don't", ResumableWord.trailingLatinWord("i said don't"))
    }

    @Test
    fun trailingBoundaryCharacterMeansNoLatinWord() {
        assertNull(ResumableWord.trailingLatinWord("hello cana "))
        assertNull(ResumableWord.trailingLatinWord("hello."))
        assertNull(ResumableWord.trailingLatinWord("abc123"))
        assertNull(ResumableWord.trailingLatinWord(""))
    }

    @Test
    fun fidelNeverCountsAsLatin() {
        assertNull(ResumableWord.trailingLatinWord("ሰላም"))
        // ...but a Latin word AFTER fidel stops at the script boundary.
        assertEquals("abc", ResumableWord.trailingLatinWord("ሰላምabc"))
    }

    // ---- Ethiopic (Amharic composer) ----

    @Test
    fun fidelWordAtEndIsExtracted() {
        assertEquals("ሰላም", ResumableWord.trailingEthiopicWord("hello ሰላም"))
        assertEquals("ሰላም", ResumableWord.trailingEthiopicWord("ሰላም"))
    }

    @Test
    fun ethiopicPunctuationAndDigitsAreBoundaries() {
        assertNull(ResumableWord.trailingEthiopicWord("ሰላም።"))
        assertNull(ResumableWord.trailingEthiopicWord("ሰላም፣"))
        assertNull(ResumableWord.trailingEthiopicWord("ሰላም፩"))
        assertNull(ResumableWord.trailingEthiopicWord("ሰላም "))
    }

    @Test
    fun latinNeverCountsAsEthiopic() {
        assertNull(ResumableWord.trailingEthiopicWord("abc"))
        assertEquals("ሰላም", ResumableWord.trailingEthiopicWord("abcሰላም"))
    }

    // ---- Shared window guard ----

    @Test
    fun wordFillingTheWholeLookbehindWindowIsRejected() {
        val full = "a".repeat(ResumableWord.LOOKBEHIND)
        assertNull(ResumableWord.trailingLatinWord(full))
        // A window-length text with a boundary inside still yields the word:
        // the start is visible, so it is not a fragment.
        val bounded = " " + "a".repeat(ResumableWord.LOOKBEHIND - 1)
        assertEquals("a".repeat(ResumableWord.LOOKBEHIND - 1), ResumableWord.trailingLatinWord(bounded))
        // Shorter than the window and all word chars: the field simply starts
        // with the word, adopt it whole.
        assertEquals("abc", ResumableWord.trailingLatinWord("abc"))
    }
}
