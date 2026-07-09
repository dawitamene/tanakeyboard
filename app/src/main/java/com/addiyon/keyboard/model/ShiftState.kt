package com.addiyon.keyboard.model

/**
 * The three states a real keyboard's shift key cycles through:
 *
 * OFF        - lowercase, default state.
 * SHIFT      - next single character is capitalized, then auto-resets to OFF.
 * CAPS_LOCK  - all characters capitalized until shift is tapped again.
 *
 * Tapping shift cycles OFF -> SHIFT -> CAPS_LOCK -> OFF. Typing a character
 * only consumes the one-shot SHIFT state; CAPS_LOCK is untouched by typing
 * and must be explicitly turned off.
 */
enum class ShiftState {
    OFF,
    SHIFT,
    CAPS_LOCK
}