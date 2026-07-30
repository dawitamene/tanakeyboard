package com.addiyon.keyboard.composing

internal fun allowsCommittedWordResume(
    isAmharic: Boolean,
    isEmailField: Boolean
): Boolean =
    !isAmharic || isEmailField

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

internal fun composingCursorOffset(
    selectionStart: Int,
    selectionEnd: Int,
    composingStart: Int,
    composingEnd: Int
): Int? =
    if (
        selectionStart == selectionEnd &&
        composingStart >= 0 &&
        composingEnd >= composingStart &&
        selectionStart in composingStart..composingEnd
    ) {
        selectionStart - composingStart
    } else {
        null
    }

internal fun isComposerTextImmediatelyBeforeCursor(
    composerText: String,
    textBeforeCursor: CharSequence?
): Boolean =
    composerText.isNotEmpty() && textBeforeCursor?.toString() == composerText

internal fun isVerifiedStaleDeleteCallback(
    expectedDeleteSelection: Boolean,
    selectionStart: Int,
    selectionEnd: Int,
    candidatesStart: Int,
    candidatesEnd: Int,
    ownedRegionMatches: Boolean
): Boolean =
    expectedDeleteSelection &&
        selectionStart == selectionEnd &&
        candidatesStart < 0 &&
        candidatesEnd < 0 &&
        ownedRegionMatches
