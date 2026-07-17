package com.addiyon.keyboard.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Transition table for [onShiftTap] -- the Gboard model: a single tap
 * toggles the one-shot shift on/off, only a quick double tap reaches caps
 * lock, and caps lock releases on any tap.
 */
class ShiftStateTest {

    @Test
    fun `single tap arms one-shot shift`() {
        assertEquals(ShiftState.SHIFT, ShiftState.OFF.onShiftTap(isDoubleTap = false))
    }

    @Test
    fun `second slow tap disarms shift instead of locking caps`() {
        assertEquals(ShiftState.OFF, ShiftState.SHIFT.onShiftTap(isDoubleTap = false))
    }

    @Test
    fun `double tap engages caps lock`() {
        // Second tap of a quick pair: the first already armed SHIFT.
        assertEquals(ShiftState.CAPS_LOCK, ShiftState.SHIFT.onShiftTap(isDoubleTap = true))
    }

    @Test
    fun `double tap from off engages caps lock too`() {
        // E.g. quickly re-tapping right after a tap disarmed an auto-cap
        // armed shift -- the gesture is still a double tap, so it locks.
        assertEquals(ShiftState.CAPS_LOCK, ShiftState.OFF.onShiftTap(isDoubleTap = true))
    }

    @Test
    fun `tap releases caps lock`() {
        assertEquals(ShiftState.OFF, ShiftState.CAPS_LOCK.onShiftTap(isDoubleTap = false))
    }

    @Test
    fun `third quick tap releases caps lock rather than re-locking`() {
        assertEquals(ShiftState.OFF, ShiftState.CAPS_LOCK.onShiftTap(isDoubleTap = true))
    }
}
