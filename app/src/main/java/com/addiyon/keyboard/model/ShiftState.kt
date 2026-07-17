package com.addiyon.keyboard.model

/**
 * The three states of the shift key:
 *
 * OFF        - lowercase, default state.
 * SHIFT      - next single character is capitalized, then auto-resets to OFF.
 * CAPS_LOCK  - all characters capitalized until shift is tapped again.
 *
 * A single tap toggles OFF <-> SHIFT; a quick double tap engages CAPS_LOCK
 * (see [onShiftTap]). Typing a character only consumes the one-shot SHIFT
 * state; CAPS_LOCK is untouched by typing and must be explicitly turned off.
 */
enum class ShiftState {
    OFF,
    SHIFT,
    CAPS_LOCK
}

/**
 * The state a shift-key tap transitions to (Gboard model):
 *
 *  - single tap: OFF -> SHIFT, and anything else -> OFF (a second slow tap
 *    DISARMS shift rather than escalating to caps lock, and caps lock always
 *    releases on tap).
 *  - double tap ([isDoubleTap]: this tap landed within the double-tap window
 *    of the previous shift tap): engage CAPS_LOCK -- except when already
 *    caps-locked, where the tap releases it like any other (so a triple tap
 *    ends OFF, not re-locked).
 */
fun ShiftState.onShiftTap(isDoubleTap: Boolean): ShiftState = when {
    isDoubleTap && this != ShiftState.CAPS_LOCK -> ShiftState.CAPS_LOCK
    this == ShiftState.OFF -> ShiftState.SHIFT
    else -> ShiftState.OFF
}
