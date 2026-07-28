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
    fun latinWordSurroundingCursorIncludesBothSides() {
        assertEquals(
            ResumableWord.AtCursor(word = "inform", cursorOffset = 2),
            ResumableWord.latinWordAtCursor(before = "in", after = "form")
        )
        assertEquals(
            ResumableWord.AtCursor(word = "don't", cursorOffset = 3),
            ResumableWord.latinWordAtCursor(before = "don", after = "'t")
        )
    }

    @Test
    fun latinWordAtCursorStopsAtVisibleBoundaries() {
        assertEquals(
            ResumableWord.AtCursor(word = "inform", cursorOffset = 2),
            ResumableWord.latinWordAtCursor(before = "say in", after = "form now")
        )
        assertNull(ResumableWord.latinWordAtCursor(before = "say ", after = " now"))
    }

    @Test
    fun emailWordAtCursorIncludesTheWholeAddressToken() {
        assertEquals(
            ResumableWord.AtCursor(word = "test@example.com", cursorOffset = 5),
            ResumableWord.emailWordAtCursor(
                before = "to test@",
                after = "example.com next"
            )
        )
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

    @Test
    fun wordFillingEitherCursorWindowIsRejected() {
        assertNull(
            ResumableWord.latinWordAtCursor(
                before = "a".repeat(ResumableWord.LOOKBEHIND),
                after = "bc"
            )
        )
        assertNull(
            ResumableWord.latinWordAtCursor(
                before = "ab",
                after = "c".repeat(ResumableWord.LOOKAHEAD)
            )
        )
    }
}
