package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class NgramContextTest {

    private fun ctx(text: String?) = NgramContext.extract(text)

    @Test
    fun twoWordsBeforeCursorYieldBothContexts() {
        assertEquals(NgramContext.Context("እንደ", "ምን"), ctx("እንደ ምን "))
    }

    @Test
    fun singleWordYieldsPrev1Only() {
        assertEquals(NgramContext.Context(null, "ሰላም"), ctx("ሰላም "))
    }

    @Test
    fun cursorTouchingAWordYieldsNothing() {
        assertEquals(NgramContext.EMPTY, ctx("ሰላም"))
        assertEquals(NgramContext.EMPTY, ctx("ሰላም ዓለ"))
    }

    @Test
    fun emptyAndNullAreEmpty() {
        assertEquals(NgramContext.EMPTY, ctx(null))
        assertEquals(NgramContext.EMPTY, ctx(""))
        assertEquals(NgramContext.EMPTY, ctx("   "))
    }

    @Test
    fun sentencePunctuationKillsAllContext() {
        assertEquals(NgramContext.EMPTY, ctx("ሰላም ። "))
        assertEquals(NgramContext.EMPTY, ctx("ሰላም ፣ "))
        assertEquals(NgramContext.EMPTY, ctx("hello "))
        assertEquals(NgramContext.EMPTY, ctx("ቁጥር 42 "))
    }

    @Test
    fun boundaryBetweenWordsKillsOnlyPrev2() {
        assertEquals(NgramContext.Context(null, "ቤት"), ctx("ቡና ፣ ቤት "))
        assertEquals(NgramContext.Context(null, "ቤት"), ctx("ቡና። ቤት "))
        assertEquals(NgramContext.Context(null, "ቤት"), ctx("42 ቤት "))
    }

    @Test
    fun punctuationGluedToPrev2SideIsABoundary() {
        // "ቡና፣ ቤት " -- the ፣ touches ቡና; prev2 must not be read through it.
        assertEquals(NgramContext.Context(null, "ቤት"), ctx("ቡና፣ ቤት "))
    }

    @Test
    fun multipleSpacesAndOlderTextAreHandled()  {
        assertEquals(NgramContext.Context("እንደ", "ምን"), ctx("ሰላም ። እንደ  ምን  "))
    }

    @Test
    fun geminationMarksAreStripped() {
        assertEquals(NgramContext.Context(null, "ሰላም"), ctx("ሰላ፝ም "))
    }

    @Test
    fun wordTouchingTruncatedWindowStartIsDiscarded() {
        // Exactly WINDOW chars: the leading word may be cut off mid-word.
        val truncated = "ሀ".repeat(NgramContext.WINDOW - 4) + " ቤት "
        assertEquals(NgramContext.WINDOW, truncated.length)
        assertEquals(NgramContext.Context(null, "ቤት"), ctx(truncated))

        val truncatedPrev1 = "ሀ".repeat(NgramContext.WINDOW - 1) + " "
        assertEquals(NgramContext.EMPTY, ctx(truncatedPrev1))

        // One char shorter than the window -> complete field text, trusted.
        val complete = "ሀ".repeat(NgramContext.WINDOW - 5) + " ቤት "
        assertEquals(
            NgramContext.Context("ሀ".repeat(NgramContext.WINDOW - 5), "ቤት"),
            ctx(complete)
        )
    }
}
