package com.addiyon.keyboard.ui.manual

import kotlin.math.abs

/**
 * The magnification curve for the guide's fast-scroll rail: while a finger
 * scrubs the rail, each glyph swells by how close it is to the touch --
 * full [MAX_EXTRA_SCALE] under the finger, easing smoothly back to 1 at
 * [radius] away (a dock-style bubble). Pure math, JVM-unit-testable; the
 * composable animates toward these targets so entering/leaving the bubble
 * is smooth too.
 */
internal object RailMagnification {

    const val MAX_EXTRA_SCALE = 3f

    /**
     * Scale for a glyph whose center sits at [centerY] while the finger is at
     * [touchY]; 1 (no magnification) when idle ([touchY] null), before the
     * glyph has been laid out ([centerY] null), or outside [radius]. The
     * falloff is a smoothstep, so the bubble has no hard edge and no kink at
     * its peak.
     */
    fun scaleFor(touchY: Float?, centerY: Float?, radius: Float): Float {
        if (touchY == null || centerY == null || radius <= 0f) return 1f
        val distance = abs(touchY - centerY)
        if (distance >= radius) return 1f
        val t = 1f - distance / radius
        return 1f + MAX_EXTRA_SCALE * t * t * (3f - 2f * t)
    }
}
