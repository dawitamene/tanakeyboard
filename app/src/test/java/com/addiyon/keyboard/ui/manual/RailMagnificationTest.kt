package com.addiyon.keyboard.ui.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailMagnificationTest {

    @Test
    fun idleAndUnmeasuredGlyphsStayAtNaturalSize() {
        assertEquals(1f, RailMagnification.scaleFor(null, 100f, 50f))
        assertEquals(1f, RailMagnification.scaleFor(100f, null, 50f))
        assertEquals(1f, RailMagnification.scaleFor(100f, 100f, 0f))
    }

    @Test
    fun glyphUnderTheFingerPeaksAndFallsOffToOneAtTheRadius() {
        val radius = 50f
        assertEquals(1f + RailMagnification.MAX_EXTRA_SCALE, RailMagnification.scaleFor(100f, 100f, radius))
        assertEquals(1f, RailMagnification.scaleFor(100f, 150f, radius))
        assertEquals(1f, RailMagnification.scaleFor(100f, 400f, radius))
    }

    @Test
    fun falloffIsSymmetricAndMonotonic() {
        val radius = 50f
        val near = RailMagnification.scaleFor(100f, 110f, radius)
        val far = RailMagnification.scaleFor(100f, 140f, radius)
        assertEquals(near, RailMagnification.scaleFor(100f, 90f, radius))
        assertTrue("closer glyph must be bigger: $near vs $far", near > far)
        assertTrue(far > 1f)
    }
}
