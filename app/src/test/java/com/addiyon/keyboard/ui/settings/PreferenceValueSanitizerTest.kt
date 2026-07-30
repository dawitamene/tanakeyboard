package com.addiyon.keyboard.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceValueSanitizerTest {

    @Test
    fun booleansAcceptLegacyRepresentationsAndRejectUnknownValues() {
        assertTrue(PreferenceValueSanitizer.boolean("yes", false))
        assertTrue(PreferenceValueSanitizer.boolean(1, false))
        assertFalse(PreferenceValueSanitizer.boolean("off", true))
        assertTrue(PreferenceValueSanitizer.boolean("unknown", true))
        assertFalse(PreferenceValueSanitizer.boolean(null, false))
        assertTrue(PreferenceValueSanitizer.boolean(Double.NaN, true))
        assertFalse(PreferenceValueSanitizer.boolean(Double.POSITIVE_INFINITY, false))
    }

    @Test
    fun booleansCoverEverySupportedAliasAndNumericShape() {
        listOf("true", "1", "yes", "on", " TRUE ", "On").forEach {
            assertTrue(PreferenceValueSanitizer.boolean(it, false))
        }
        listOf("false", "0", "no", "off", " FALSE ", "Off").forEach {
            assertFalse(PreferenceValueSanitizer.boolean(it, true))
        }
        assertTrue(PreferenceValueSanitizer.boolean(true, false))
        assertFalse(PreferenceValueSanitizer.boolean(false, true))
        assertFalse(PreferenceValueSanitizer.boolean(0, true))
        assertTrue(PreferenceValueSanitizer.boolean(-1L, false))
        assertTrue(PreferenceValueSanitizer.boolean(0.5f, false))
        assertFalse(PreferenceValueSanitizer.boolean(Float.NaN, false))
        assertTrue(PreferenceValueSanitizer.boolean(Float.NEGATIVE_INFINITY, true))
        assertTrue(PreferenceValueSanitizer.boolean(Any(), true))
    }

    @Test
    fun floatsRejectNonFiniteValuesAndClampTheSupportedRange() {
        assertEquals(
            1f,
            PreferenceValueSanitizer.float(Float.NaN, 1f, 0.8f, 1.2f),
            0f
        )
        assertEquals(
            1f,
            PreferenceValueSanitizer.float(Float.POSITIVE_INFINITY, 1f, 0.8f, 1.2f),
            0f
        )
        assertEquals(
            1.2f,
            PreferenceValueSanitizer.float("9.0", 1f, 0.8f, 1.2f),
            0f
        )
        assertEquals(
            0.8f,
            PreferenceValueSanitizer.float(-5, 1f, 0.8f, 1.2f),
            0f
        )
    }

    @Test
    fun integersHandleWrongTypesOverflowAndBounds() {
        assertEquals(25, PreferenceValueSanitizer.int("25", 0, 0, 100))
        assertEquals(100, PreferenceValueSanitizer.int(Long.MAX_VALUE, 0, 0, 100))
        assertEquals(0, PreferenceValueSanitizer.int(-10, 0, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int("bad", 7, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int(Double.NaN, 7, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int(Double.NEGATIVE_INFINITY, 7, 0, 100))
    }

    @Test
    fun integersAcceptEveryNumericInputPathAndRejectNonFiniteFloatingPoint() {
        assertEquals(25, PreferenceValueSanitizer.int(25f, 0, 0, 100))
        assertEquals(25, PreferenceValueSanitizer.int(25.9, 0, 0, 100))
        assertEquals(25, PreferenceValueSanitizer.int(25.toShort(), 0, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int(Float.NaN, 7, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int(Float.POSITIVE_INFINITY, 7, 0, 100))
        assertEquals(7, PreferenceValueSanitizer.int(Any(), 7, 0, 100))
        assertEquals(0, PreferenceValueSanitizer.int("-1", 7, 0, 100))
        assertEquals(100, PreferenceValueSanitizer.int("101", 7, 0, 100))
    }

    @Test
    fun stringsRejectWrongTypesAndBoundRestoredPayloads() {
        assertEquals("abcd", PreferenceValueSanitizer.string("abcdefgh", null, 4))
        assertEquals("default", PreferenceValueSanitizer.string(42, "default", 20))
        assertNull(PreferenceValueSanitizer.string(null, null, 20))
        assertEquals("ab", PreferenceValueSanitizer.string("ab🙂cd", null, 3))
        assertEquals("ab🙂", PreferenceValueSanitizer.string("ab🙂cd", null, 4))
        assertEquals("", PreferenceValueSanitizer.string("abc", null, -1))
    }

    @Test
    fun stringsPreserveBoundedValuesAndOnlyBackOffForACompleteSurrogatePair() {
        assertEquals("abc", PreferenceValueSanitizer.string("abc", null, 3))
        assertEquals("abc", PreferenceValueSanitizer.string("abc", null, 8))
        assertEquals("", PreferenceValueSanitizer.string("abc", null, 0))
        assertEquals(
            "ab\uD83D",
            PreferenceValueSanitizer.string("ab\uD83Dx", null, 3)
        )
    }

    @Test
    fun numericSanitizersAlwaysReturnValuesInsideTheirSupportedRange() {
        val floats = listOf<Any?>(
            null,
            "bad",
            "-1000",
            "0.95",
            "1000",
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Long.MIN_VALUE,
            Long.MAX_VALUE
        )
        floats.forEach {
            val value = PreferenceValueSanitizer.float(it, 1f, 0.8f, 1.2f)
            assertTrue(value.isFinite())
            assertTrue(value in 0.8f..1.2f)
        }

        val integers = listOf<Any?>(
            null,
            "bad",
            Long.MIN_VALUE.toString(),
            "-1",
            "50",
            "101",
            Long.MAX_VALUE.toString(),
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY
        )
        integers.forEach {
            assertTrue(PreferenceValueSanitizer.int(it, 7, 0, 100) in 0..100)
        }
    }
}
