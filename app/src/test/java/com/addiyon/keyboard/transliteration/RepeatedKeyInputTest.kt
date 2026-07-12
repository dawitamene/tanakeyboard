package com.addiyon.keyboard.transliteration

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatedKeyInputTest {
    @Test
    fun repeatedAKeysProduceRepeatedACharacters() {
        assertEquals("አአአ", Transliterator.transliterate("aaa"))
    }

    @Test
    fun repeatedConsonantKeysStayInTheSameFamily() {
        assertEquals("ክክ", Transliterator.transliterate("kk"))
        assertEquals("ህህ", Transliterator.transliterate("hh"))
    }
}
