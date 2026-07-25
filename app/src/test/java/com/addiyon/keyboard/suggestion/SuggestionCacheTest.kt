package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class SuggestionCacheTest {
    @Test
    fun concurrentReadsWritesAndClearsStayBounded() {
        val cache = SuggestionCache<Int>(16)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val start = CountDownLatch(1)
        val threads = List(4) { worker ->
            Thread {
                try {
                    start.await()
                    repeat(2_000) { index ->
                        val key = "${worker}_$index"
                        cache.put(key, index)
                        cache.get(key)
                        if (index % 127 == 0) cache.clear()
                    }
                } catch (t: Throwable) {
                    failures.add(t)
                }
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)
        assertTrue(failures.toString(), failures.isEmpty())
        assertTrue(cache.size() <= 16)
    }
}
