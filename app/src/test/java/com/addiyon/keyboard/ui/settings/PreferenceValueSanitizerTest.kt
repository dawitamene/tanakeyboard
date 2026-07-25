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
    }

    @Test
    fun stringsRejectWrongTypesAndBoundRestoredPayloads() {
        assertEquals("abcd", PreferenceValueSanitizer.string("abcdefgh", null, 4))
        assertEquals("default", PreferenceValueSanitizer.string(42, "default", 20))
        assertNull(PreferenceValueSanitizer.string(null, null, 20))
    }
}
