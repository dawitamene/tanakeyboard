package com.addiyon.keyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Drives Android's [SpeechRecognizer] for continuous dictation. The platform
 * recognizer is single-utterance, so "continuous" means tearing the session
 * down and starting a fresh one after every final result / recoverable error
 * -- all of that churn stays INSIDE this class. The outside world only sees
 * three things:
 *
 *  - [onPartial]: the latest refinement of the in-flight utterance.
 *  - [onFinal]: the finished utterance -- emitted EXACTLY ONCE per utterance
 *    that produced text, on every session-ending path (normal finals, blank
 *    finals falling back to the last partial, the speech-end fallback timer,
 *    and error recovery). This is the invariant the composing-region design
 *    in [VoiceComposer] relies on: text the user saw always gets finalized.
 *  - [onFatalError]: recognition can't continue (no recognizer, permission,
 *    repeated failures). No UI-state callback exists on purpose -- the
 *    service owns UI state and sets it per user action, not per recognizer
 *    callback.
 *
 * [stop] and [restartSession] emit nothing: the caller finalizes the field's
 * composing region itself (finishComposingText keeps whatever was showing),
 * so flushing here would double-commit.
 *
 * Every async edge is guarded by a [generation] token: each new session
 * bumps it, and stale listener callbacks / timers compare their captured id
 * before acting. Timers are individually-cancelled named tokens -- never
 * removeCallbacksAndMessages(null), which cancels unrelated pending work.
 */
class VoiceInputController(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFatalError: (VoiceErrorKind) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var activeLanguageTag: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var userStopped = false
    private var lastPartial = ""
    private var recoverableErrorCount = 0
    private var watchdogToken: Runnable? = null
    private var speechEndToken: Runnable? = null
    private var restartToken: Runnable? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        userStopped = false
        activeLanguageTag = languageTag
        lastPartial = ""
        recoverableErrorCount = 0
        startSession(languageTag)
    }

    /**
     * Ends dictation without emitting anything -- the caller preserves the
     * in-flight partial via finishComposingText, so the text the user saw
     * survives even though nothing is flushed here.
     */
    fun stop() {
        userStopped = true
        activeLanguageTag = null
        lastPartial = ""
        generation++
        cancelAllTimers()
        releaseRecognizer(cancel = true)
    }

    /**
     * Abandons the in-flight utterance (WITHOUT emitting it -- the caller
     * has already finalized the field) and starts a fresh session. Used when
     * the user moves the cursor mid-dictation: what was showing is locked in
     * at its old spot and recognition resumes cleanly at the new one.
     */
    fun restartSession() {
        val languageTag = activeLanguageTag ?: return
        if (userStopped) return
        lastPartial = ""
        generation++
        cancelAllTimers()
        releaseRecognizer(cancel = true)
        startSession(languageTag)
    }

    private fun startSession(languageTag: String) {
        cancelAllTimers()
        releaseRecognizer(cancel = false)

        if (!isAvailable) {
            activeLanguageTag = null
            onFatalError(VoiceErrorKind.UNAVAILABLE)
            return
        }

        val sessionId = ++generation
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(createListener(sessionId))
        newRecognizer.startListening(recognizerIntent(languageTag))
        scheduleStartWatchdog(sessionId)
    }

    private fun recognizerIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf("am-ET", "en-US")
                )
            }
        }

    private fun releaseRecognizer(cancel: Boolean) {
        val current = recognizer ?: return
        recognizer = null
        current.setRecognitionListener(null)
        if (cancel) current.cancel()
        current.destroy()
    }

    private fun createListener(sessionId: Int): RecognitionListener = object : RecognitionListener {
        private fun isCurrent() = sessionId == generation

        override fun onReadyForSpeech(params: Bundle?) {
            if (!isCurrent()) return
            cancelStartWatchdog()
            recoverableErrorCount = 0
        }

        override fun onBeginningOfSpeech() {
            if (!isCurrent()) return
            cancelStartWatchdog()
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!isCurrent()) return
            scheduleSpeechEndFallback(sessionId)
        }

        override fun onError(error: Int) {
            if (!isCurrent()) return
            cancelSpeechEndFallback()
            val kind = errorKind(error)
            if (isRecoverable(kind)) {
                recover(kind)
            } else {
                // The in-flight partial stays visible in the field; the
                // service's fatal handler finalizes the composing region.
                lastPartial = ""
                activeLanguageTag = null
                releaseRecognizer(cancel = false)
                onFatalError(kind)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrent()) return
            cancelSpeechEndFallback()
            // A blank final falls back to the last partial: the user already
            // saw that text, so it must be finalized, not dropped.
            val final = bestHypothesis(results)?.takeIf { it.isNotBlank() } ?: lastPartial
            if (final.isNotBlank()) {
                lastPartial = ""
                recoverableErrorCount = 0
                onFinal(final)
            }
            restartIfNeeded()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrent()) return
            bestHypothesis(partialResults)?.takeIf { it.isNotBlank() }?.let {
                lastPartial = it
                onPartial(it)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun recover(kind: VoiceErrorKind) {
        val languageTag = activeLanguageTag
        if (userStopped || languageTag == null) return

        flushLastPartial()
        recoverableErrorCount++
        if (recoverableErrorCount > MAX_RECOVERABLE_ERRORS) {
            activeLanguageTag = null
            releaseRecognizer(cancel = false)
            onFatalError(VoiceErrorKind.TOO_MANY_REQUESTS)
            return
        }

        // No error surfaced to the user: silent recovery is normal churn
        // (NO_SPEECH fires on every pause in speech). onReadyForSpeech
        // resets the error count once a session comes up healthy.
        releaseRecognizer(cancel = false)
        val delay = RECOVERY_DELAYS[(recoverableErrorCount - 1).coerceAtMost(RECOVERY_DELAYS.lastIndex)]
        scheduleRestart(delay, languageTag)
    }

    private fun flushLastPartial() {
        val partial = lastPartial
        if (partial.isBlank()) return
        lastPartial = ""
        onFinal(partial)
    }

    private fun scheduleStartWatchdog(sessionId: Int) {
        cancelStartWatchdog()
        val token = Runnable {
            watchdogToken = null
            if (sessionId == generation && !userStopped && activeLanguageTag != null) {
                recover(VoiceErrorKind.CLIENT)
            }
        }
        watchdogToken = token
        handler.postDelayed(token, START_WATCHDOG_MILLIS)
    }

    private fun cancelStartWatchdog() {
        watchdogToken?.let(handler::removeCallbacks)
        watchdogToken = null
    }

    private fun scheduleSpeechEndFallback(sessionId: Int) {
        cancelSpeechEndFallback()
        val token = Runnable {
            speechEndToken = null
            if (sessionId == generation && !userStopped) {
                // Bump the generation BEFORE flushing so a late onResults
                // from this session is stale and can't emit a second final.
                generation++
                flushLastPartial()
                restartIfNeeded()
            }
        }
        speechEndToken = token
        handler.postDelayed(token, SPEECH_END_COMMIT_GRACE_MILLIS)
    }

    private fun cancelSpeechEndFallback() {
        speechEndToken?.let(handler::removeCallbacks)
        speechEndToken = null
    }

    private fun restartIfNeeded() {
        val languageTag = activeLanguageTag
        if (userStopped || languageTag == null) return
        scheduleRestart(RESTART_DELAY_MILLIS, languageTag)
    }

    private fun scheduleRestart(delay: Long, languageTag: String) {
        cancelRestart()
        val token = Runnable {
            restartToken = null
            if (!userStopped && activeLanguageTag == languageTag) {
                startSession(languageTag)
            }
        }
        restartToken = token
        handler.postDelayed(token, delay)
    }

    private fun cancelRestart() {
        restartToken?.let(handler::removeCallbacks)
        restartToken = null
    }

    private fun cancelAllTimers() {
        cancelStartWatchdog()
        cancelSpeechEndFallback()
        cancelRestart()
    }

    private fun bestHypothesis(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorKind(error: Int): VoiceErrorKind = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> VoiceErrorKind.AUDIO
        SpeechRecognizer.ERROR_CLIENT -> VoiceErrorKind.CLIENT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorKind.PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceErrorKind.NETWORK
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceErrorKind.NO_SPEECH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorKind.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_SERVER -> VoiceErrorKind.SERVER
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceErrorKind.SERVER_DISCONNECTED
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceErrorKind.TOO_MANY_REQUESTS
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> VoiceErrorKind.LANGUAGE_UNSUPPORTED
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceErrorKind.LANGUAGE_UNAVAILABLE
        else -> VoiceErrorKind.UNKNOWN
    }

    private fun isRecoverable(kind: VoiceErrorKind): Boolean = when (kind) {
        VoiceErrorKind.CLIENT,
        VoiceErrorKind.NO_SPEECH,
        VoiceErrorKind.RECOGNIZER_BUSY,
        VoiceErrorKind.SERVER_DISCONNECTED -> true
        else -> false
    }

    private companion object {
        const val START_WATCHDOG_MILLIS = 4500L
        const val SPEECH_END_COMMIT_GRACE_MILLIS = 600L

        // The dead gap between one utterance's final and the next session
        // opening the mic. Kept short so mid-sentence words aren't lost;
        // RECOGNIZER_BUSY from restarting too fast is recoverable and backs
        // off via RECOVERY_DELAYS.
        const val RESTART_DELAY_MILLIS = 150L

        const val MAX_RECOVERABLE_ERRORS = 4
        val RECOVERY_DELAYS = longArrayOf(300L, 700L, 1500L, 2500L)
    }
}
