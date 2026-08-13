package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDictionaryTest {

    @Test
    fun completionEntriesPreserveCountAndRecencySeparately() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("am-ET", "ሰው")
        dictionary.learn("am-ET", "ሰላም")
        dictionary.learn("am-ET", "ሰው")

        val entries = dictionary.completionEntries("am-ET", "ሰ", 5)

        assertEquals("ሰው", entries.first().word)
        assertEquals(2, entries.first().count)
        assertTrue(entries.first().recency > entries.last().recency)
    }

    @Test
    fun learnedWordsAreRankedBeforeBuiltInCandidatesByCaller() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("en-US", "Zebra")
        dictionary.learn("en-US", "Zebra")
        dictionary.learn("en-US", "Zen")
        assertEquals(listOf("Zebra", "Zen"), dictionary.completions("en-US", "z", 3))
    }

    @Test
    fun dictionaryRoundTripsAndKeepsEmailAddresses() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learnEmail("me@example.com")
        val restored = PersonalDictionary.decode(dictionary.encode())
        assertEquals(listOf("me@example.com"), restored.emailAddresses())
    }

    @Test
    fun oldFormatMigratesWordsIntoLanguageAndEmailBuckets() {
        val dictionary = PersonalDictionary.decode(
            "2\tHello\n3\tሰላም\n4\tme@example.com\n1\t---"
        )

        assertEquals(listOf("Hello"), dictionary.completions("en-US", "h", 3))
        assertEquals(listOf("ሰላም"), dictionary.completions("am-ET", "ሰ", 3))
        assertEquals(listOf("me@example.com"), dictionary.emailAddresses())
        assertEquals(listOf("Hello", "ሰላም", "me@example.com", "---"), dictionary.allWords())
    }

    @Test
    fun languageBucketsDoNotLeakAcrossPacks() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("en-US", "hello")
        dictionary.learn("om-ET", "hello")
        dictionary.learn("om-ET", "horoo")

        assertEquals(listOf("hello"), dictionary.completions("en-US", "h", 5))
        assertEquals(listOf("hello", "horoo"), dictionary.completions("om-ET", "h", 5))
    }
}
