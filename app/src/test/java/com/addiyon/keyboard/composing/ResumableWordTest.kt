package com.addiyon.keyboard.composing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumableWordTest {

    // ---- Latin (English pipeline) ----

    @Test
    fun latinWordEndingAtCaretIsExtracted() {
        assertEquals("cana", ResumableWord.latinWordEndingAtCursor("hello cana", ""))
        assertEquals("cana", ResumableWord.latinWordEndingAtCursor("cana", ""))
    }

    @Test
    fun apostropheBelongsToTheLatinWord() {
        assertEquals("don't", ResumableWord.latinWordEndingAtCursor("i said don't", ""))
    }

    @Test
    fun latinInteriorCaretIsNotAWordEnd() {
        assertNull(ResumableWord.latinWordEndingAtCursor("in", "form"))
        assertNull(ResumableWord.latinWordEndingAtCursor("don", "'t"))
        assertNull(ResumableWord.latinWordEndingAtCursor("say in", "form now"))
    }

    @Test
    fun latinCaretBeforeAWordIsNotAWordEnd() {
        assertNull(ResumableWord.latinWordEndingAtCursor("say ", "hello "))
    }

    @Test
    fun latinWordEndRequiresAVisibleBoundary() {
        assertNull(ResumableWord.latinWordEndingAtCursor("hello cana ", ""))
        assertNull(ResumableWord.latinWordEndingAtCursor("hello.", ""))
        assertNull(ResumableWord.latinWordEndingAtCursor("abc123", ""))
        assertNull(ResumableWord.latinWordEndingAtCursor("", ""))
        assertEquals(
            "inform",
            ResumableWord.latinWordEndingAtCursor("say inform", " now")
        )
    }

    @Test
    fun fidelNeverCountsAsLatin() {
        assertNull(ResumableWord.latinWordEndingAtCursor("ሰላም", ""))
        // ...but a Latin word AFTER fidel stops at the script boundary.
        assertEquals("abc", ResumableWord.latinWordEndingAtCursor("ሰላምabc", ""))
    }

    // ---- Amharic (fidel pipeline) ----

    @Test
    fun amharicWordEndingAtCaretIsExtracted() {
        assertEquals("ሰላም", ResumableWord.amharicWordEndingAtCursor("ሰላም", ""))
        assertEquals("ሰላም", ResumableWord.amharicWordEndingAtCursor("ጤና ሰላም", ""))
    }

    @Test
    fun amharicInteriorCaretAndLatinAreNotWordEnds() {
        assertNull(ResumableWord.amharicWordEndingAtCursor("ሰ", "ላም"))
        assertNull(ResumableWord.amharicWordEndingAtCursor("selam", ""))
        assertNull(ResumableWord.amharicWordEndingAtCursor("ሰላም ", ""))
    }

    // ---- Email ----

    @Test
    fun emailWordEndingAtCaretIncludesTheWholeAddressToken() {
        assertEquals(
            "test@example.com",
            ResumableWord.emailWordEndingAtCursor("to test@example.com", " next")
        )
        assertEquals(
            "o'neil`9@example.com",
            ResumableWord.emailWordEndingAtCursor("to o'neil`9@example.com", "-next")
        )
    }

    @Test
    fun emailInteriorCaretIsNotAWordEnd() {
        assertNull(ResumableWord.emailWordEndingAtCursor("to test@", "example.com next"))
        assertNull(ResumableWord.emailWordEndingAtCursor("name-", "_host"))
    }

    // ---- Shared window guard ----

    @Test
    fun wordFillingTheWholeLookbehindWindowIsRejected() {
        val full = "a".repeat(ResumableWord.LOOKBEHIND)
        assertNull(ResumableWord.latinWordEndingAtCursor(full, ""))
        // A window-length text with a boundary inside still yields the word:
        // the start is visible, so it is not a fragment.
        val bounded = " " + "a".repeat(ResumableWord.LOOKBEHIND - 1)
        assertEquals(
            "a".repeat(ResumableWord.LOOKBEHIND - 1),
            ResumableWord.latinWordEndingAtCursor(bounded, "")
        )
        // Shorter than the window and all word chars: the field simply starts
        // with the word, adopt it whole.
        assertEquals("abc", ResumableWord.latinWordEndingAtCursor("abc", ""))
    }
}
