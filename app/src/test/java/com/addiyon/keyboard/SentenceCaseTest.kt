package com.addiyon.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceCaseTest {

    private fun starts(text: String?) = SentenceCase.startsNewSentence(text)

    @Test
    fun emptyFieldIsSentenceStart() {
        assertTrue(starts(""))
        assertTrue(starts("   "))
    }

    @Test
    fun unreadableFieldIsNotSentenceStart() {
        // null = getTextBeforeCursor failed (keyboard just opened, or the app
        // withholds surrounding text). The caret could be mid-sentence, so
        // never guess a capital -- distinct from "" (genuinely at the start).
        assertFalse(starts(null))
    }

    @Test
    fun afterSentenceTerminatorPlusSpaceIsStart() {
        assertTrue(starts("Hello. "))
        assertTrue(starts("Really?  "))
        assertTrue(starts("Wow!\t"))
        assertTrue(starts("Done.   "))
    }

    @Test
    fun freshLineIsStart() {
        assertTrue(starts("First line\n"))
        assertTrue(starts("First line\r"))
    }

    @Test
    fun midSentenceIsNotStart() {
        assertFalse(starts("Hello world "))
        assertFalse(starts("the "))
        assertFalse(starts("I saw him. Then we "))
    }

    @Test
    fun caretTouchingATokenIsNotStart() {
        // No trailing space -> we're at/inside a word or number.
        assertFalse(starts("Hello"))
        assertFalse(starts("Hello."))
        assertFalse(starts("www.google"))
        assertFalse(starts("3.14"))
    }

    @Test
    fun terminatorWithoutSpaceIsNotStartUntilTheSpaceArrives() {
        // "e.g" mid-token stays lowercase; only "End. " flips it.
        assertFalse(starts("e.g"))
        assertTrue(starts("End. "))
    }
}
