package com.addiyon.keyboard.voice

internal object VoiceRecoveryPolicy {
    fun isRecoverable(kind: VoiceErrorKind): Boolean = when (kind) {
        VoiceErrorKind.CLIENT,
        VoiceErrorKind.NO_SPEECH,
        VoiceErrorKind.RECOGNIZER_BUSY,
        VoiceErrorKind.SERVER_DISCONNECTED -> true
        else -> false
    }

    fun allowsRetry(attempt: Int): Boolean =
        attempt in 1..MAX_RECOVERABLE_ERRORS

    fun delayMillis(attempt: Int): Long =
        RECOVERY_DELAYS[(attempt - 1).coerceIn(0, RECOVERY_DELAYS.lastIndex)]

    private const val MAX_RECOVERABLE_ERRORS = 4
    private val RECOVERY_DELAYS = longArrayOf(300L, 700L, 1500L, 2500L)
}
