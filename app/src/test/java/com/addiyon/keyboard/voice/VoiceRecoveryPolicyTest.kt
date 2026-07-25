package com.addiyon.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecoveryPolicyTest {
    @Test
    fun silenceBusyClientAndDisconnectCanRetry() {
        assertTrue(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.NO_SPEECH))
        assertTrue(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.RECOGNIZER_BUSY))
        assertTrue(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.CLIENT))
        assertTrue(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.SERVER_DISCONNECTED))
    }

    @Test
    fun permissionLanguageNetworkAndServerFailuresStop() {
        assertFalse(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.PERMISSION))
        assertFalse(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.LANGUAGE_UNSUPPORTED))
        assertFalse(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.LANGUAGE_UNAVAILABLE))
        assertFalse(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.NETWORK))
        assertFalse(VoiceRecoveryPolicy.isRecoverable(VoiceErrorKind.SERVER))
    }

    @Test
    fun retriesAreBoundedAndBackoffIsCapped() {
        assertTrue(VoiceRecoveryPolicy.allowsRetry(1))
        assertTrue(VoiceRecoveryPolicy.allowsRetry(4))
        assertFalse(VoiceRecoveryPolicy.allowsRetry(5))
        assertEquals(300L, VoiceRecoveryPolicy.delayMillis(1))
        assertEquals(2500L, VoiceRecoveryPolicy.delayMillis(100))
    }
}
