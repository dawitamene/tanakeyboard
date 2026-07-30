package com.addiyon.keyboard.composing

import com.addiyon.keyboard.EditorToken

internal enum class CommittedWordSource {
    CURSOR_OBSERVATION,
    EXPLICIT_DELETE
}

internal data class CommittedWordSnapshot(
    val editorToken: EditorToken,
    val selectionStart: Int,
    val selectionEnd: Int,
    val spanStart: Int,
    val spanEnd: Int,
    val word: String,
    val cursorOffset: Int,
    val isAmharic: Boolean,
    val isEmailField: Boolean,
    val isPrivateField: Boolean,
    val isNumberMode: Boolean,
    val source: CommittedWordSource
) {
    fun matchesPolicy(
        isAmharic: Boolean,
        isEmailField: Boolean,
        isPrivateField: Boolean,
        isNumberMode: Boolean
    ): Boolean =
        this.isAmharic == isAmharic &&
            this.isEmailField == isEmailField &&
            this.isPrivateField == isPrivateField &&
            this.isNumberMode == isNumberMode
}
