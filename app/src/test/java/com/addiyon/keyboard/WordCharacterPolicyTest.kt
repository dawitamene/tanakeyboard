package com.addiyon.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCharacterPolicyTest {
    @Test
    fun punctuationEndsComposition() {
        assertFalse(isComposingWordCharacter("."))
        assertFalse(isComposingWordCharacter(","))
        assertFalse(isComposingWordCharacter("/"))
    }

    @Test
    fun seraWordCharactersContinueComposition() {
        assertTrue(isComposingWordCharacter("a"))
        assertTrue(isComposingWordCharacter("'"))
        assertTrue(isComposingWordCharacter("`"))
    }
}
