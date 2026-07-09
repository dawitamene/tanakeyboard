package com.addiyon.keyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's [SpeechRecognizer], driving voice-to-text for
 * the keyboard's mic button. Uses the public `SpeechRecognizer` API backed by
 * Google's recognizer (the same engine Gboard's voice typing uses) rather
 * than a bundled offline model -- no API key or backend, and by far the best
 * available Amharic quality (see plan.md's Feature 3 research). Amharic
 * (`am-ET`) typically requires a network connection; that's expected, same
 * as Gboard.
 *
 * A single [ACTION_RECOGNIZE_SPEECH] session is inherently single-utterance:
 * the system finalizes it after a short trailing silence (historically as
 * little as ~1s), which read as "it only hears one word" for anything but
 * very fast, unbroken speech. Two things fix that here:
 *  1. The `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS` extras below ask the
 *     recognizer to tolerate longer pauses (e.g. taking a breath mid-sentence)
 *     before treating speech as complete.
 *  2. [restartIfNeeded] transparently starts a fresh session shortly
 *     after each one ends -- on a real final result *and* on a no-speech/
 *     timeout error -- so listening effectively continues across many
 *     utterances until the user explicitly taps stop. This mirrors Gboard's
 *     continuous dictation: each recognized phrase is delivered via
 *     [onFinalResult] and committed immediately rather than accumulated.
 *
 * One [SpeechRecognizer] instance per *session* (recreated on every restart,
 * torn down in [stop]) -- instances aren't meant to be reused indefinitely
 * across many start/stop cycles. Every method here must be called on the
 * main thread, per the underlying API's own contract; the service only ever
 * drives this from UI callbacks, so that's automatic.
 */
enum class VoiceInputState { IDLE, SPEAK_NOW, LISTENING }

class VoiceInputController(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (message: String) -> Unit,
    private val onVoiceStateChanged: (VoiceInputState) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var activeLanguageTag: String? = null
    private val restartHandler = Handler(Looper.getMainLooper())

    /**
     * Bumped every time a new recognizer session is created. Each
     * [RecognitionListener] instance captures the generation it was built
     * for and ignores callbacks once a newer session has superseded it --
     * [SpeechRecognizer.destroy] doesn't guarantee an in-flight callback
     * already queued on the main looper is cancelled, so without this guard
     * a late callback from a just-destroyed session (e.g. a trailing
     * onPartialResults for the word just finished) can land after the next
     * session's first partial already started composing, and the two
     * `setComposingText` calls race -- reading as "it heard a new word and
     * wiped out the one before it".
     */
    private var generation = 0

    /** Set on [stop] so a session ending right after doesn't auto-restart. */
    private var userStopped = false

    /**
     * Most recent interim guess for the utterance in progress, not yet
     * confirmed by [onFinalResult]. Every session that ends without a real
     * result (manual stop, no-match, timeout) finalizes this instead of
     * discarding it -- nothing that was heard should vanish silently.
     */
    private var lastPartial: String? = null

    /** False on devices/ROMs without a Google Speech Services-equivalent. */
    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts listening for [languageTag] (e.g. "am-ET" or "en-US"), replacing
     * any recognition already in progress. Keeps restarting automatically
     * (see class doc) until [stop] is called.
     */
    fun start(languageTag: String) {
        userStopped = false
        activeLanguageTag = languageTag
        startSession(languageTag)
    }

    /** Stops and releases the recognizer, if one is active. Safe to call repeatedly. */
    fun stop() {
        userStopped = true
        activeLanguageTag = null
        generation++
        restartHandler.removeCallbacksAndMessages(null)
        val current = recognizer ?: return
        recognizer = null
        current.setRecognitionListener(null)
        current.cancel()
        current.destroy()
        flushLastPartial()
        onListeningChanged(false)
        onVoiceStateChanged(VoiceInputState.IDLE)
    }

    /** Finalizes whatever interim guess is pending, if any, then clears it. */
    private fun flushLastPartial() {
        lastPartial?.takeIf { it.isNotBlank() }?.let(onFinalResult)
        lastPartial = null
    }

    private fun startSession(languageTag: String) {
        recognizer?.let {
            it.setRecognitionListener(null)
            it.destroy()
        }
        if (!isAvailable) {
            recognizer = null
            onListeningChanged(false)
            onError("Voice input isn't available on this device.")
            return
        }
        val sessionId = ++generation
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(createListener(sessionId))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Tolerate longer pauses before the recognizer decides an
            // utterance is complete -- the system defaults are tuned for
            // short search queries, not dictated sentences.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
        }
        newRecognizer.startListening(intent)
    }

    /**
     * Starts a new session shortly after the current one ends, unless the
     * user tapped stop. The short delay (rather than starting synchronously
     * from inside the previous session's own callback) gives the underlying
     * recognizer service a moment to actually release the microphone --
     * restarting instantly was the main source of spurious
     * `ERROR_RECOGNIZER_BUSY`/`ERROR_CLIENT` failures during continuous
     * dictation, which is why voice input used to surface an error so often.
     */
    private fun restartIfNeeded() {
        val languageTag = activeLanguageTag
        if (userStopped || languageTag == null) {
            onListeningChanged(false)
            onVoiceStateChanged(VoiceInputState.IDLE)
            return
        }
        restartHandler.postDelayed({
            if (!userStopped && activeLanguageTag == languageTag) {
                startSession(languageTag)
            }
        }, RESTART_DELAY_MILLIS)
    }

    /**
     * Builds a fresh listener tied to [sessionId]. [SpeechRecognizer.destroy]
     * doesn't guarantee an already-queued callback from the outgoing session
     * won't still fire, so every callback checks it's still the current
     * generation before touching any shared state -- otherwise a late
     * trailing result for the word just finished could land after the next
     * utterance has already started composing, and the two results race
     * (reading as the new word wiping out the previous one).
     */
    private fun createListener(sessionId: Int): RecognitionListener = object : RecognitionListener {
        private fun isCurrent() = sessionId == generation

        override fun onReadyForSpeech(params: Bundle?) {
            if (isCurrent()) {
                onListeningChanged(true)
                onVoiceStateChanged(VoiceInputState.SPEAK_NOW)
            }
        }
        override fun onBeginningOfSpeech() {
            if (isCurrent()) {
                onListeningChanged(true)
                onVoiceStateChanged(VoiceInputState.LISTENING)
            }
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (isCurrent()) {
                onVoiceStateChanged(VoiceInputState.SPEAK_NOW)
            }
        }

        override fun onError(error: Int) {
            if (!isCurrent()) return
            when (error) {
                // No speech heard / a pause was mistaken for the end of the
                // utterance, or the recognizer service was transiently busy
                // / cancelled by our own rapid restart -- keep the mic open
                // rather than surfacing this as a failure, exactly like
                // continuous dictation should. Finalize whatever interim
                // guess was showing first so it's not lost across the
                // restart.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    flushLastPartial()
                    restartIfNeeded()
                }
                else -> {
                    flushLastPartial()
                    onListeningChanged(false)
                    onVoiceStateChanged(VoiceInputState.IDLE)
                    onError(describeError(error))
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrent()) return
            lastPartial = null
            bestHypothesis(results)?.let(onFinalResult)
            restartIfNeeded()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrent()) return
            bestHypothesis(partialResults)?.let {
                lastPartial = it
                onPartialResult(it)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun bestHypothesis(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Didn't catch that -- try again."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice input needs a network connection."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required for voice input."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Voice input is busy -- try again in a moment."
        else -> "Voice input error."
    }

    private companion object {
        const val RESTART_DELAY_MILLIS = 300L
    }
}
