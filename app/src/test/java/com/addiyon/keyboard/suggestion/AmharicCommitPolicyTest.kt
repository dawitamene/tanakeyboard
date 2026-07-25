package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class AmharicCommitPolicyTest {
    @Test
    fun exactBufferWorkerCandidateWins() {
        assertEquals("መስጠት", AmharicCommitPolicy.resolve("mesTet", "መስጠት"))
    }

    @Test
    fun rapidCommitFallsBackToDeterministicGreedyReading() {
        assertEquals("ሰላም", AmharicCommitPolicy.resolve("selam", null))
    }

    @Test
    fun emptyBufferStaysEmpty() {
        assertEquals("", AmharicCommitPolicy.resolve("", "ignored"))
    }
}
