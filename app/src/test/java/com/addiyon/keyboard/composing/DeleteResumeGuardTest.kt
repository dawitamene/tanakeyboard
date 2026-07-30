package com.addiyon.keyboard.composing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteResumeGuardTest {
    @Test
    fun invalidExpectationsClearEveryPendingGuard() {
        val invalidExpectations = listOf(
            Triple(-1, 4, 3),
            Triple(4, -1, 3),
            Triple(4, 4, -1)
        )
        invalidExpectations.forEach { (start, end, cursor) ->
            val guard = DeleteResumeGuard()
            guard.expect(4, 4, 3)

            guard.expect(start, end, cursor)

            assertFalse(guard.guards(3))
            assertFalse(guard.onSelectionUpdate(4, 4, 3, 3))
        }
    }

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
    fun partialCursorMatchesDoNotConsumeDeletionExpectation() {
        val guard = DeleteResumeGuard()
        guard.expect(
            sourceSelectionStart = 4,
            sourceSelectionEnd = 4,
            expectedCursor = 3
        )

        assertFalse(guard.onSelectionUpdate(9, 9, 3, 2))
        assertFalse(guard.onSelectionUpdate(9, 9, 2, 3))
        assertTrue(guard.onSelectionUpdate(4, 4, 3, 3))
    }

    @Test
    fun sourceSelectionChangeClearsExpectationForEitherChangedEndpoint() {
        val changedStart = DeleteResumeGuard().apply { expect(4, 4, 3) }
        assertFalse(changedStart.onSelectionUpdate(4, 4, 5, 4))
        assertFalse(changedStart.guards(3))

        val changedEnd = DeleteResumeGuard().apply { expect(4, 4, 3) }
        assertFalse(changedEnd.onSelectionUpdate(4, 4, 4, 5))
        assertFalse(changedEnd.guards(3))
    }

    @Test
    fun callbacksFromASelectionSharingOnlyOneSourceEndpointDoNotClearExpectation() {
        val changedOldEnd = DeleteResumeGuard().apply { expect(4, 4, 3) }
        assertFalse(changedOldEnd.onSelectionUpdate(4, 5, 8, 8))
        assertTrue(changedOldEnd.guards(3))

        val unchangedSource = DeleteResumeGuard().apply { expect(4, 4, 3) }
        assertFalse(unchangedSource.onSelectionUpdate(4, 4, 4, 4))
        assertTrue(unchangedSource.guards(3))
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
        assertFalse(guard.guards(-1))
    }
}
