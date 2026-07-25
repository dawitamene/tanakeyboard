package com.addiyon.keyboard

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the auto-cap / email-field classification in
 * [InputTypePolicy] -- the rules that AddiyonKeyboardService.resolveAutoCap
 * applies per input session. A regression here would silently re-enable
 * sentence capitalization in email fields or strip the email-chip
 * suggestion pipeline from a properly-declared email field.
 */
class InputTypePolicyTest {

    private fun typeClassAndVariation(@Suppress("SameParameter") cls: Int, variation: Int): Int =
        cls or variation

    @Test
    fun emailAddressVariationIsRecognizedAsEmail() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        assertTrue(InputTypePolicy.isEmailInputType(t))
    }

    @Test
    fun webEmailVariationIsRecognizedAsEmail() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        assertTrue(InputTypePolicy.isEmailInputType(t))
    }

    @Test
    fun plainTextVariationIsNotEmail() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        assertFalse(InputTypePolicy.isEmailInputType(t))
    }

    @Test
    fun passwordVariationIsNotEmail() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        assertFalse(InputTypePolicy.isEmailInputType(t))
    }

    @Test
    fun numberClassIsNotEmail() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        // Email variation is meaningless on a non-text class.
        assertFalse(InputTypePolicy.isEmailInputType(t))
    }

    @Test
    fun emailVariationsDisableAutoCap() {
        val email = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val webEmail = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        assertFalse("email-typed field must not auto-cap", InputTypePolicy.allowsAutoCap(email))
        assertFalse("web-email field must not auto-cap", InputTypePolicy.allowsAutoCap(webEmail))
    }

    @Test
    fun passwordVariationsDisableAutoCap() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        ).forEach { variation ->
            val t = typeClassAndVariation(InputType.TYPE_CLASS_TEXT, variation)
            assertFalse(
                "password variation $variation must not auto-cap",
                InputTypePolicy.allowsAutoCap(t)
            )
        }
    }

    @Test
    fun uriAndFilterVariationsDisableAutoCap() {
        listOf(
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        ).forEach { variation ->
            val t = typeClassAndVariation(InputType.TYPE_CLASS_TEXT, variation)
            assertFalse(
                "variation $variation must not auto-cap",
                InputTypePolicy.allowsAutoCap(t)
            )
        }
    }

    @Test
    fun plainTextVariationAllowsAutoCap() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        assertTrue("plain text fields should auto-cap by default",
            InputTypePolicy.allowsAutoCap(t))
    }

    @Test
    fun numberClassDoesNotAllowAutoCap() {
        val t = typeClassAndVariation(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_TEXT_VARIATION_NORMAL
        )
        // Auto-cap is a text-class feature; numbers should opt out.
        assertFalse(InputTypePolicy.allowsAutoCap(t))
    }

    @Test
    fun nullEditorInfoMapsToNonTextAndDisablesAutoCap() {
        // 0 = no bits set: neither text nor number class. isEmailInputType
        // returns false (NOT a text class), allowsAutoCap returns false.
        assertFalse(InputTypePolicy.isEmailInputType(0))
        assertFalse(InputTypePolicy.allowsAutoCap(0))
    }

    @Test
    fun textAndNumericPasswordsArePrivate() {
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        ).forEach { assertTrue(InputTypePolicy.isPrivateInputType(it)) }
    }

    @Test
    fun ordinaryAndEmailFieldsAreNotPrivate() {
        assertFalse(
            InputTypePolicy.isPrivateInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            )
        )
        assertFalse(
            InputTypePolicy.isPrivateInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
    }
}
