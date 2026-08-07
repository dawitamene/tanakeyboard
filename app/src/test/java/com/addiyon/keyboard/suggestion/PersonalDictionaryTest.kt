package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalDictionaryTest {
    @Test
    fun learnedWordsAreRankedBeforeBuiltInCandidatesByCaller() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("Zebra")
        dictionary.learn("Zebra")
        dictionary.learn("Zen")
        assertEquals(listOf("Zebra", "Zen"), dictionary.completions("z", 3))
    }

    @Test
    fun dictionaryRoundTripsAndKeepsEmailAddresses() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("me@example.com")
        val restored = PersonalDictionary.decode(dictionary.encode())
        assertEquals(listOf("me@example.com"), restored.emailAddresses())
    }
}
