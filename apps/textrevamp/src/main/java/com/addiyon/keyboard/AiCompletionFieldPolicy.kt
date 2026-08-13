package com.addiyon.keyboard

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo

internal object AiCompletionFieldPolicy {
    fun isEligible(editorInfo: EditorInfo?): Boolean {
        editorInfo ?: return false
        if (editorInfo.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) {
            return false
        }
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        if (variation in EXCLUDED_VARIATIONS) return false
        if (
            editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 36 && !editorInfo.isWritingToolsEnabled) return false
        return true
    }

    private val EXCLUDED_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_FILTER,
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )
}
