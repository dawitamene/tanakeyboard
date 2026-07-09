package com.addiyon.keyboard.voice

/**
 * The keyboard-visible voice mode state. Deliberately coarse: the service
 * sets it once per USER action (mic tap, back arrow, fatal error), never per
 * recognizer callback -- the recognizer restarts a session after every
 * utterance and errors/recovers constantly, and mirroring that churn into
 * the UI is what made the old status label flicker. Recognized text never
 * appears here either; it streams straight into the field's composing
 * region (see [VoiceComposer]).
 */
sealed interface VoiceUiState {
    data object Idle : VoiceUiState

    /** Actively dictating -- covers session restarts and silent recovery. */
    data object Listening : VoiceUiState

    data object Paused : VoiceUiState
    data object PermissionRequired : VoiceUiState
    data class Unavailable(val message: String) : VoiceUiState
}

val VoiceUiState.isVoiceMode: Boolean
    get() = this !is VoiceUiState.Idle

val VoiceUiState.isRecording: Boolean
    get() = this is VoiceUiState.Listening

enum class VoiceErrorKind(val userMessage: String) {
    AUDIO("Microphone audio error."),
    CLIENT("Voice input is recovering."),
    LANGUAGE_UNAVAILABLE("Voice language is not available yet."),
    LANGUAGE_UNSUPPORTED("Voice language is not supported on this device."),
    NETWORK("Voice input needs an internet connection."),
    NO_SPEECH("Speak now."),
    PERMISSION("Microphone permission is required for voice input."),
    RECOGNIZER_BUSY("Voice input is warming up."),
    SERVER("Voice input service error."),
    SERVER_DISCONNECTED("Voice input is reconnecting."),
    TOO_MANY_REQUESTS("Voice input needs a short break. Tap mic to try again."),
    UNAVAILABLE("Voice input is not available on this device."),
    UNKNOWN("Voice input error.")
}
