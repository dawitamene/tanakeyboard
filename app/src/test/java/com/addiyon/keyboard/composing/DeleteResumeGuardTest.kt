package com.addiyon.keyboard.composing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteResumeGuardTest {
    @Test
    fun expectedDeletionSelectionSuppressesWordResume() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        assertTrue(guard.onSelectionUpdate(4, 4, 3, 3))
        assertTrue(guard.guards(3))
    }

    @Test
    fun earlierSelectionCallbackDoesNotConsumeDeletionExpectation() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        assertFalse(guard.onSelectionUpdate(5, 5, 4, 4))
        assertTrue(guard.onSelectionUpdate(4, 4, 3, 3))
    }

    @Test
    fun duplicateCallbacksAtDeletedCursorStaySuppressed() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        assertTrue(guard.onSelectionUpdate(4, 4, 3, 3))
        assertTrue(guard.onSelectionUpdate(3, 3, 3, 3))
    }

    @Test
    fun deliberateCursorMoveReleasesProtectedPosition() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )
        assertTrue(guard.onSelectionUpdate(4, 4, 3, 3))

        assertFalse(guard.onSelectionUpdate(3, 3, 1, 1))
        assertFalse(guard.guards(3))
    }

    @Test
    fun repeatEndCanSeeExpectedCursorBeforeCallbackArrives() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        assertTrue(guard.guards(3))
    }

    @Test
    fun clearRemovesPendingSuppression() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        guard.clear()

        assertFalse(guard.onSelectionUpdate(4, 4, 3, 3))
        assertFalse(guard.guards(3))
    }
}
