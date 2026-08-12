package com.addiyon.keyboard.ui.manual

import kotlin.math.abs

internal object RailMagnification {

    const val MAX_EXTRA_SCALE = 3f

    fun scaleFor(touchY: Float?, centerY: Float?, radius: Float): Float {
        if (touchY == null || centerY == null || radius <= 0f) return 1f
        val distance = abs(touchY - centerY)
        if (distance >= radius) return 1f
        val t = 1f - distance / radius
        return 1f + MAX_EXTRA_SCALE * t * t * (3f - 2f * t)
    }
}
