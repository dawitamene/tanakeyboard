package com.addiyon.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCompletionFieldPolicyTest {
    @Test
    fun normalTextIsEligible() {
        assertTrue(
            AiCompletionFieldPolicy.isEligible(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL)
            )
        )
    }

    @Test
    fun privateStructuredAndNonTextFieldsAreExcluded() {
        val inputTypes = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE
        )

        inputTypes.forEach { inputType ->
            assertFalse(AiCompletionFieldPolicy.isEligible(editorInfo(inputType)))
        }
    }

    @Test
    fun noPersonalizedLearningOptOutIsHonored() {
        val info = editorInfo(InputType.TYPE_CLASS_TEXT).apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        assertFalse(AiCompletionFieldPolicy.isEligible(info))
    }

    private fun editorInfo(inputType: Int): EditorInfo = EditorInfo().apply {
        this.inputType = inputType
    }
}
