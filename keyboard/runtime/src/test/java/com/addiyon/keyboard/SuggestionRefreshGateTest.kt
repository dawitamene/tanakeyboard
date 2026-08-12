package com.addiyon.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRefreshGateTest {
    @Test
    fun deleteGestureCoalescesRefreshRequestsUntilRelease() {
        val gate = SuggestionRefreshGate()

        gate.beginDeleteGesture()
        assertFalse(gate.requestRefresh())
        assertFalse(gate.requestRefresh())
        assertTrue(gate.endDeleteGesture())
        assertTrue(gate.requestRefresh())
        assertFalse(gate.endDeleteGesture())
    }
}
