package com.addiyon.keyboard.composing

internal fun isSelectionAtComposingEnd(
    selectionStart: Int,
    selectionEnd: Int,
    composingStart: Int,
    composingEnd: Int
): Boolean =
    selectionStart == selectionEnd &&
        composingStart >= 0 &&
        composingEnd >= composingStart &&
        selectionStart == composingEnd

internal fun isComposerTextImmediatelyBeforeCursor(
    composerText: String,
    textBeforeCursor: CharSequence?
): Boolean =
    composerText.isNotEmpty() && textBeforeCursor?.toString() == composerText
