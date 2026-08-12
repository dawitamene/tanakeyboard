package com.addiyon.keyboard.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionOwnershipTest {
    @Test
    fun newSessionInvalidatesEarlierCallbacks() {
        val ownership = VoiceSessionOwnership()
        val first = requireNotNull(ownership.begin())
        val second = requireNotNull(ownership.begin())

        assertFalse(ownership.isCurrent(first))
        assertTrue(ownership.isCurrent(second))
    }

    @Test
    fun invalidationRejectsCallbacksButAllowsOneGuardedRestart() {
        val ownership = VoiceSessionOwnership()
        val active = requireNotNull(ownership.begin())
        val restartGuard = ownership.invalidate()

        assertFalse(ownership.isCurrent(active))
        assertTrue(ownership.isGeneration(restartGuard))

        val next = requireNotNull(ownership.begin())

        assertFalse(ownership.isGeneration(restartGuard))
        assertTrue(ownership.isCurrent(next))
    }

    @Test
    fun stopThenStartCreatesOneNewCurrentGeneration() {
        val ownership = VoiceSessionOwnership()
        val stopped = requireNotNull(ownership.begin())
        ownership.invalidate()
        val resumed = requireNotNull(ownership.begin())

        assertFalse(ownership.isCurrent(stopped))
        assertTrue(ownership.isCurrent(resumed))
    }

    @Test
    fun destructionPermanentlyRejectsCallbacksAndNewSessions() {
        val ownership = VoiceSessionOwnership()
        val active = requireNotNull(ownership.begin())

        ownership.destroy()

        assertFalse(ownership.isCurrent(active))
        assertNull(ownership.begin())
    }

    @Test
    fun sessionCanOnlyHaveOneCurrentTicket() {
        val ownership = VoiceSessionOwnership()
        val ticket = ownership.begin()

        assertNotNull(ticket)
        assertTrue(ownership.isCurrent(requireNotNull(ticket)))
    }
}
