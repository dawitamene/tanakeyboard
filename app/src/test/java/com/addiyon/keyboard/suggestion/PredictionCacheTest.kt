package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class PredictionCacheTest {
    @Test
    fun lookupNormalizesEnglishCaseAndApostrophes() {
        val cache = PredictionCache<String>(4)
        cache.put(
            language = PredictionLanguage.ENGLISH,
            prev2 = "WE",
            prev1 = "DON’T",
            limit = 3,
            value = "hit",
        )

        assertEquals(
            "hit",
            cache.get(
                language = PredictionLanguage.ENGLISH,
                prev2 = "we",
                prev1 = "don't",
                limit = 3,
            )
        )
        assertNull(
            cache.get(
                language = PredictionLanguage.ENGLISH,
                prev2 = "we",
                prev1 = "don't",
                limit = 4,
            )
        )
    }

    @Test
    fun lookupNormalizesAmharicSpellingVariantsAndKeepsLanguagesSeparate() {
        val cache = PredictionCache<String>(4)
        cache.put(
            language = PredictionLanguage.AMHARIC,
            prev2 = null,
            prev1 = "ሐገር",
            limit = 3,
            value = "hit",
        )

        assertEquals(
            "hit",
            cache.get(
                language = PredictionLanguage.AMHARIC,
                prev2 = "",
                prev1 = "ሀገር",
                limit = 3,
            )
        )
        assertNull(
            cache.get(
                language = PredictionLanguage.ENGLISH,
                prev2 = null,
                prev1 = "ሀገር",
                limit = 3,
            )
        )
    }

    @Test
    fun emptyResultsAreCached() {
        val cache = PredictionCache<List<String>>(2)
        cache.put(
            language = PredictionLanguage.ENGLISH,
            prev2 = null,
            prev1 = "unmatched",
            limit = 3,
            value = emptyList(),
        )

        val cached = cache.get(
            language = PredictionLanguage.ENGLISH,
            prev2 = null,
            prev1 = "UNMATCHED",
            limit = 3,
        )

        assertTrue(cached != null)
        assertTrue(cached!!.isEmpty())
    }

    @Test
    fun leastRecentlyUsedEntryIsEvictedAndTrimKeepsNewest() {
        val cache = PredictionCache<Int>(3)
        cache.put(PredictionLanguage.ENGLISH, null, "one", 3, 1)
        cache.put(PredictionLanguage.ENGLISH, null, "two", 3, 2)
        cache.put(PredictionLanguage.ENGLISH, null, "three", 3, 3)
        assertEquals(1, cache.get(PredictionLanguage.ENGLISH, null, "one", 3))

        cache.put(PredictionLanguage.ENGLISH, null, "four", 3, 4)

        assertNull(cache.get(PredictionLanguage.ENGLISH, null, "two", 3))
        assertEquals(1, cache.get(PredictionLanguage.ENGLISH, null, "one", 3))
        cache.trimToSize(1)
        assertEquals(1, cache.size())
        assertEquals(1, cache.get(PredictionLanguage.ENGLISH, null, "one", 3))
        assertNull(cache.get(PredictionLanguage.ENGLISH, null, "three", 3))
        assertNull(cache.get(PredictionLanguage.ENGLISH, null, "four", 3))
    }

    @Test
    fun concurrentAccessAndClearsStayBounded() {
        val cache = PredictionCache<Int>(16)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val start = CountDownLatch(1)
        val threads = List(4) { worker ->
            Thread {
                try {
                    start.await()
                    repeat(1_000) { index ->
                        val word = "$worker-$index"
                        cache.put(PredictionLanguage.ENGLISH, null, word, 3, index)
                        cache.get(PredictionLanguage.ENGLISH, null, word, 3)
                        if (index % 127 == 0) {
                            cache.clear()
                        }
                    }
                } catch (throwable: Throwable) {
                    failures.add(throwable)
                }
            }
        }

        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)

        assertTrue(failures.toString(), failures.isEmpty())
        assertTrue(cache.size() <= 16)
        assertFalse(cache.size() < 0)
    }
}
