package com.addiyon.keyboard

import android.text.InputType

/**
 * Centralizes the EditorInfo -> keyboard-behavior classification used by
 * AddiyonKeyboardService.resolveAutoCap, so the rules can be exercised from
 * JVM unit tests without spinning up an EditorInfo. Pure functions over the
 * raw [inputType] bitfield, no Android Context or actual EditorInfo
 * required -- callers pass the bitfield value they read from
 * EditorInfo.inputType.
 */
internal object InputTypePolicy {

    /**
     * Text-class / email-variation check. True when the field's inputType
     * declares a TEXT class AND the variation is one of the two email
     * variations Android uses for email addresses. Matches what
     * [AddiyonKeyboardService.resolveAutoCap] sets into `isEmailField`.
     */
    fun isEmailInputType(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    /**
     * Whether English sentence auto-capitalization should arm itself in the
     * given field. Defaults ON for text-class fields, OFF for password /
     * email / URI / filter variations. Matches the gate
     * [AddiyonKeyboardService.maybeAutoCapitalize] reads via
     * `fieldAllowsAutoCap`.
     */
    fun allowsAutoCap(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation !in NO_AUTOCAP_VARIATIONS_FOR_TESTS
    }
}

// Mirror of AddiyonKeyboardService.NO_AUTOCAP_VARIATIONS, kept in sync so
// JVM unit tests can reference it without reaching into a private top-level
// field of the service. The deny-list is what gates auto-cap OFF.
private val NO_AUTOCAP_VARIATIONS_FOR_TESTS = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_URI,
    InputType.TYPE_TEXT_VARIATION_FILTER,
)