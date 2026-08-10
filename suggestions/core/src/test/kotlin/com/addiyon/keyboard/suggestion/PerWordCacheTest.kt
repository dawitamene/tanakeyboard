package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerWordCacheTest {
    @Test
    fun capturesOnceForRepeatedPrefixesAndBackspacesInTheSameWord() {
        val cache = PerWordCache<String, String?>()
        var captures = 0

        repeat(6) {
            assertEquals(
                "prior context",
                cache.getOrCapture("session-1-english") {
                    captures += 1
                    "prior context"
                }
            )
        }

        assertEquals(1, captures)
    }

    @Test
    fun nullCaptureIsStillCachedForTheWord() {
        val cache = PerWordCache<String, String?>()
        var captures = 0

        repeat(3) {
            assertNull(
                cache.getOrCapture("session-1-amharic") {
                    captures += 1
                    null
                }
            )
        }

        assertEquals(1, captures)
    }

    @Test
    fun newSessionLanguageOrBoundaryForcesAnotherCapture() {
        val cache = PerWordCache<String, Int>()
        var captures = 0
        val capture = { ++captures }

        assertEquals(1, cache.getOrCapture("session-1-english", capture))
        assertEquals(2, cache.getOrCapture("session-1-amharic", capture))
        assertEquals(3, cache.getOrCapture("session-2-amharic", capture))
        cache.clear()
        assertEquals(4, cache.getOrCapture("session-2-amharic", capture))
    }
}
