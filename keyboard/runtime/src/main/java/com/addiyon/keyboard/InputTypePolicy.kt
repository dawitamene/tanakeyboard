package com.addiyon.keyboard

import android.text.InputType

/**
 * Centralizes the EditorInfo -> keyboard-behavior classification used by
 * PackKeyboardService.resolveAutoCap, so the rules can be exercised from
 * JVM unit tests without spinning up an EditorInfo. Pure functions over the
 * raw [inputType] bitfield, no Android Context or actual EditorInfo
 * required -- callers pass the bitfield value they read from
 * EditorInfo.inputType.
 */
object InputTypePolicy {

    /**
     * Text-class / email-variation check. True when the field's inputType
     * declares a TEXT class AND the variation is one of the two email
     * variations Android uses for email addresses. Matches what
     * [PackKeyboardService.resolveAutoCap] sets into `isEmailField`.
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
     * [PackKeyboardService.maybeAutoCapitalize] reads via
     * `fieldAllowsAutoCap`.
     */
    fun allowsAutoCap(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation !in NO_AUTOCAP_VARIATIONS
    }

    fun isPrivateInputType(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation in PRIVATE_TEXT_VARIATIONS
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}

private val NO_AUTOCAP_VARIATIONS = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    InputType.TYPE_TEXT_VARIATION_URI,
    InputType.TYPE_TEXT_VARIATION_FILTER,
)

private val PRIVATE_TEXT_VARIATIONS = setOf(
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
)
