package com.addiyon.keyboard.composing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionSelectionPolicyTest {

    @Test
    fun committedAmharicNeverResumesIntoAComposerBuffer() {
        assertFalse(allowsCommittedWordResume(isAmharic = true, isEmailField = false))
        assertTrue(allowsCommittedWordResume(isAmharic = false, isEmailField = false))
        assertTrue(allowsCommittedWordResume(isAmharic = true, isEmailField = true))
    }

    @Test
    fun collapsedSelectionAtComposingEndKeepsCompositionActive() {
        assertTrue(isSelectionAtComposingEnd(5, 5, 1, 5))
    }

    @Test
    fun cursorInMiddleIsNotAtComposingEnd() {
        assertFalse(isSelectionAtComposingEnd(3, 3, 1, 5))
    }

    @Test
    fun collapsedCursorInsideComposingWordReturnsItsBufferOffset() {
        assertEquals(2, composingCursorOffset(3, 3, 1, 5))
        assertEquals(4, composingCursorOffset(5, 5, 1, 5))
    }

    @Test
    fun selectionOutsideComposingWordHasNoBufferOffset() {
        assertNull(composingCursorOffset(2, 4, 1, 5))
        assertNull(composingCursorOffset(0, 0, 1, 5))
        assertNull(composingCursorOffset(3, 3, -1, -1))
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

    @Test
    fun expectedDeleteCallbackWithMissingCandidateBoundsIsIgnoredOnlyAfterOwnershipVerification() {
        assertTrue(
            isVerifiedStaleDeleteCallback(
                expectedDeleteSelection = true,
                selectionStart = 3,
                selectionEnd = 3,
                candidatesStart = -1,
                candidatesEnd = -1,
                ownedRegionMatches = true
            )
        )
        assertFalse(
            isVerifiedStaleDeleteCallback(
                expectedDeleteSelection = true,
                selectionStart = 3,
                selectionEnd = 3,
                candidatesStart = -1,
                candidatesEnd = -1,
                ownedRegionMatches = false
            )
        )
        assertFalse(
            isVerifiedStaleDeleteCallback(
                expectedDeleteSelection = true,
                selectionStart = 3,
                selectionEnd = 3,
                candidatesStart = 0,
                candidatesEnd = 3,
                ownedRegionMatches = true
            )
        )
    }
}
