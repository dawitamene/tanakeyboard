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
    fun morphologyIdentityRoundTripsWithoutChangingExactSurfaceLearning() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn(
            "am-ET",
            "ለመደ",
            MorphologyIdentity("verb:a1", "verb:a1:f2"),
        )

        val restored = PersonalDictionary.decode(dictionary.encode())
        val entry = restored.completionEntries("am-ET", "ለ", 3).single()

        assertEquals("ለመደ", entry.word)
        assertEquals("verb:a1", entry.lemmaId)
        assertEquals("verb:a1:f2", entry.analysisId)
    }

    @Test
    fun analyzerValidatedLightVerbKeepsItsExactMultiwordSurface() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn(
            "am-ET",
            "አፈፍ አለ",
            MorphologyIdentity("verb:b2", "verb:b2:f3"),
        )

        val entry = PersonalDictionary.decode(dictionary.encode())
            .completionEntries("am-ET", "አፈፍ", 3)
            .single()

        assertEquals("አፈፍ አለ", entry.word)
        assertEquals("verb:b2:f3", entry.analysisId)
    }

    @Test
    fun malformedMorphologyIdentityCannotAuthorizeMultiwordLearning() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn(
            "am-ET",
            "not analyzer backed",
            MorphologyIdentity("verb:bad\tlemma", "verb:analysis"),
        )
        val decoded = PersonalDictionary.decode(
            "addiyon-personal-dictionary-v3\nlanguage:am-ET\t1\talso invalid\tbad id\tverb:analysis"
        )

        assertTrue(dictionary.allWords().isEmpty())
        assertTrue(decoded.allWords().isEmpty())
    }

    @Test
    fun versionTwoEncodingMigratesWithoutMorphologyMetadata() {
        val dictionary = PersonalDictionary.decode(
            "addiyon-personal-dictionary-v2\nlanguage:am-ET\t3\tለመደ"
        )

        val entry = dictionary.completionEntries("am-ET", "ለ", 3).single()

        assertEquals(3, entry.count)
        assertEquals(null, entry.lemmaId)
        assertEquals(null, entry.analysisId)
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

    @Test
    fun languageScopedWordsAndClearOperateOnTargetLanguageBucketOnly() {
        val dictionary = PersonalDictionary.decode(null)
        dictionary.learn("am-ET", "ሰላም")
        dictionary.learn("en-US", "hello")
        dictionary.learn("om-ET", "akkam")

        assertEquals(listOf("ሰላም"), dictionary.words("am-ET"))
        assertEquals(listOf("hello"), dictionary.words("en-US"))
        assertEquals(listOf("akkam"), dictionary.words("om-ET"))

        dictionary.remove("en-US", "hello")
        assertEquals(emptyList<String>(), dictionary.words("en-US"))
        assertEquals(listOf("ሰላም"), dictionary.words("am-ET"))

        dictionary.clear("am-ET")
        assertEquals(emptyList<String>(), dictionary.words("am-ET"))
        assertEquals(listOf("akkam"), dictionary.words("om-ET"))
    }
}
