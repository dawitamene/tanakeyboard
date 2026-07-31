package com.addiyon.keyboard.composing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The completion/email chip-tap validation. The bug it pins down: the previous
 * check compared the current caret against the position captured at publish
 * time, so once the user typed even one more letter (or, worse, the
 * editor emitted a redundant selection callback), the captured token went
 * stale and every chip tap was rejected silently. That is the no-op the user
 * reported in Samsung Notes and every rich-text editor backed by Compose or a
 * WebView.
 */
class ChipTapValidationTest {

    @Test
    fun composingIsValidRegardlessOfPublishedCaretWord() {
        assertTrue(isCompletionChipTapValid(composing = true, currentCaretWord = null, publishedCaretWord = null))
        assertTrue(isCompletionChipTapValid(composing = true, currentCaretWord = "bree", publishedCaretWord = "bree"))
        assertTrue(isCompletionChipTapValid(composing = true, currentCaretWord = "bree", publishedCaretWord = "info"))
    }

    @Test
    fun carriedCompletionAfterMultipleKeystrokesIsValid() {
        assertTrue(
            isCompletionChipTapValid(
                composing = true,
                currentCaretWord = null,
                publishedCaretWord = null
            )
        )
    }

    @Test
    fun caretWordMatchIsValid() {
        assertTrue(
            isCompletionChipTapValid(
                composing = false,
                currentCaretWord = "bree",
                publishedCaretWord = "bree"
            )
        )
    }

    @Test
    fun caretWordMismatchIsRejected() {
        assertFalse(
            isCompletionChipTapValid(
                composing = false,
                currentCaretWord = "cool",
                publishedCaretWord = "bree"
            )
        )
    }

    @Test
    fun caretMovedIntoAMiddleIsRejected() {
        assertFalse(
            isCompletionChipTapValid(
                composing = false,
                currentCaretWord = null,
                publishedCaretWord = "bree"
            )
        )
    }

    @Test
    fun notComposingAndNoPublishedCaretWordIsRejected() {
        assertFalse(
            isCompletionChipTapValid(
                composing = false,
                currentCaretWord = null,
                publishedCaretWord = null
            )
        )
    }
}