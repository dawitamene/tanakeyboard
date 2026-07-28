package com.addiyon.keyboard.composing

internal class DeleteResumeGuard {
    private var sourceSelectionStart: Int? = null
    private var sourceSelectionEnd: Int? = null
    private var expectedCursor: Int? = null
    private var protectedCursor: Int? = null

    fun expect(
        sourceSelectionStart: Int,
        sourceSelectionEnd: Int,
        expectedCursor: Int
    ) {
        if (
            sourceSelectionStart < 0 ||
            sourceSelectionEnd < 0 ||
            expectedCursor < 0
        ) {
            clear()
            return
        }
        this.sourceSelectionStart = sourceSelectionStart
        this.sourceSelectionEnd = sourceSelectionEnd
        this.expectedCursor = expectedCursor
        protectedCursor = null
    }

    fun onSelectionUpdate(
        oldSelectionStart: Int,
        oldSelectionEnd: Int,
        newSelectionStart: Int,
        newSelectionEnd: Int
    ): Boolean {
        val guardedCursor = expectedCursor ?: protectedCursor ?: return false
        if (
            newSelectionStart == guardedCursor &&
            newSelectionEnd == guardedCursor
        ) {
            expectedCursor = null
            protectedCursor = guardedCursor
            return true
        }
        if (protectedCursor != null) {
            clear()
            return false
        }
        if (
            oldSelectionStart == sourceSelectionStart &&
            oldSelectionEnd == sourceSelectionEnd &&
            (
                newSelectionStart != sourceSelectionStart ||
                    newSelectionEnd != sourceSelectionEnd
                )
        ) {
            clear()
        }
        return false
    }

    fun guards(cursorPosition: Int): Boolean =
        cursorPosition >= 0 &&
            (cursorPosition == expectedCursor || cursorPosition == protectedCursor)

    fun clear() {
        sourceSelectionStart = null
        sourceSelectionEnd = null
        expectedCursor = null
        protectedCursor = null
    }
}
