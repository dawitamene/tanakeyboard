package com.addiyon.keyboard.model

import android.text.InputType
import android.view.inputmethod.EditorInfo

data class EnterActionResolution(
    val action: EnterAction,
    val editorActionId: Int
)

object EnterActionPolicy {
    val default = EnterActionResolution(
        action = EnterAction.NEWLINE,
        editorActionId = EditorInfo.IME_ACTION_UNSPECIFIED
    )

    fun resolve(inputType: Int, imeOptions: Int): EnterActionResolution {
        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        val declaredAction = when (actionId) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            else -> EnterAction.NEWLINE
        }
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiline = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val action = when {
            noEnterAction -> EnterAction.NEWLINE
            declaredAction != EnterAction.NEWLINE -> declaredAction
            multiline -> EnterAction.NEWLINE
            else -> EnterAction.NEWLINE
        }
        return EnterActionResolution(action, actionId)
    }
}
