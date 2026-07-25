package com.addiyon.keyboard.composing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionSelectionPolicyTest {

    @Test
    fun collapsedSelectionAtComposingEndKeepsCompositionActive() {
        assertTrue(isSelectionAtComposingEnd(5, 5, 1, 5))
    }

    @Test
    fun cursorInMiddleOfComposingWordEndsComposition() {
        assertFalse(isSelectionAtComposingEnd(3, 3, 1, 5))
    }

    @Test
    fun rangeSelectionAndMissingComposingBoundsEndComposition() {
        assertFalse(isSelectionAtComposingEnd(2, 4, 1, 5))
        assertFalse(isSelectionAtComposingEnd(5, 5, -1, -1))
    }

    @Test
    fun backspaceUsesComposerOnlyWhenItsFullTextIsImmediatelyBeforeCursor() {
        assertTrue(isComposerTextImmediatelyBeforeCursor("hello", "hello"))
        assertFalse(isComposerTextImmediatelyBeforeCursor("hello", "hel"))
        assertFalse(isComposerTextImmediatelyBeforeCursor("hello", null))
        assertFalse(isComposerTextImmediatelyBeforeCursor("", ""))
    }
}
