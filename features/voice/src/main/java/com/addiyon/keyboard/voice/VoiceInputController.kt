package com.addiyon.keyboard.voice

import android.content.Context

/**
 * Drives Android's [android.speech.SpeechRecognizer] for continuous dictation. The platform
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
 * Every async edge is guarded by [VoiceSessionOwnership]: each new session
 * gets one ticket, and stale listener callbacks / timers are rejected before
 * acting. Timers are individually-cancelled named tokens -- never
 * removeCallbacksAndMessages(null), which cancels unrelated pending work.
 */
class VoiceInputController internal constructor(
    private val recognizerFactory: VoiceRecognizerFactory,
    private val scheduler: VoiceScheduler,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFatalError: (VoiceErrorKind) -> Unit
) {
    constructor(
        context: Context,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFatalError: (VoiceErrorKind) -> Unit
    ) : this(
        recognizerFactory = AndroidVoiceRecognizerFactory(context),
        scheduler = AndroidVoiceScheduler(),
        onPartial = onPartial,
        onFinal = onFinal,
        onFatalError = onFatalError
    )

    private var recognizer: VoiceRecognizerHandle? = null
    private var activeLanguageTag: String? = null
    private var activeAllowedLanguageTags: List<String> = emptyList()
    private val ownership = VoiceSessionOwnership()
    private var userStopped = false
    private var lastPartial = ""
    private var recoverableErrorCount = 0
    private var watchdogToken: Runnable? = null
    private var speechEndToken: Runnable? = null
    private var restartToken: Runnable? = null

    val isAvailable: Boolean
        get() = safeRun(false) { recognizerFactory.isAvailable() }

    fun start(
        languageTag: String,
        allowedLanguageTags: Collection<String> = listOf(languageTag)
    ) {
        safeApply {
            userStopped = false
            activeLanguageTag = languageTag
            activeAllowedLanguageTags = (sequenceOf(languageTag) + allowedLanguageTags.asSequence())
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .toList()
            lastPartial = ""
            recoverableErrorCount = 0
            startSession(languageTag)
        }
    }

    /**
     * Ends dictation without emitting anything -- the caller preserves the
     * in-flight partial via finishComposingText, so the text the user saw
     * survives even though nothing is flushed here.
     */
    fun stop() {
        safeApply {
            userStopped = true
            activeLanguageTag = null
            activeAllowedLanguageTags = emptyList()
            lastPartial = ""
            ownership.invalidate()
            cancelAllTimers()
            releaseRecognizer(cancel = true)
        }
    }

    fun destroy() {
        safeApply {
            userStopped = true
            activeLanguageTag = null
            activeAllowedLanguageTags = emptyList()
            lastPartial = ""
            ownership.destroy()
            cancelAllTimers()
            releaseRecognizer(cancel = true)
        }
    }

    /**
     * Abandons the in-flight utterance (WITHOUT emitting it -- the caller
     * has already finalized the field) and starts a fresh session. Used when
     * the user moves the cursor mid-dictation: what was showing is locked in
     * at its old spot and recognition resumes cleanly at the new one.
     */
    fun restartSession() {
        safeApply {
            val languageTag = activeLanguageTag ?: return@safeApply
            if (userStopped) return@safeApply
            lastPartial = ""
            ownership.invalidate()
            cancelAllTimers()
            releaseRecognizer(cancel = true)
            startSession(languageTag)
        }
    }

    private fun startSession(languageTag: String) {
        safeApply {
            cancelAllTimers()
            releaseRecognizer(cancel = false)

            if (!isAvailable) {
                failSession(VoiceErrorKind.UNAVAILABLE)
                return@safeApply
            }

            val session = ownership.begin() ?: return@safeApply
            val newRecognizer = try {
                recognizerFactory.create()
            } catch (oom: OutOfMemoryError) {
                logVoiceFailure(oom, "createSpeechRecognizer OOM")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            } catch (t: Throwable) {
                logVoiceFailure(t, "createSpeechRecognizer")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            }
            recognizer = newRecognizer
            try {
                newRecognizer.setCallback(createListener(session))
            } catch (oom: OutOfMemoryError) {
                logVoiceFailure(oom, "setRecognitionListener OOM")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            } catch (t: Throwable) {
                logVoiceFailure(t, "setRecognitionListener")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            }
            try {
                newRecognizer.startListening(languageTag, activeAllowedLanguageTags)
            } catch (oom: OutOfMemoryError) {
                logVoiceFailure(oom, "startListening OOM")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            } catch (t: Throwable) {
                logVoiceFailure(t, "startListening")
                failSession(VoiceErrorKind.UNKNOWN)
                return@safeApply
            }
            scheduleStartWatchdog(session)
        }
    }

    private fun releaseRecognizer(cancel: Boolean) {
        safeApply {
            val current = recognizer ?: return@safeApply
            recognizer = null
            try {
                current.setCallback(null)
            } catch (oom: OutOfMemoryError) {
                logVoiceFailure(
                    oom,
                    "releaseRecognizer setRecognitionListener OOM"
                )
            } catch (t: Throwable) {
                logVoiceFailure(
                    t,
                    "releaseRecognizer setRecognitionListener"
                )
            }
            if (cancel) {
                try {
                    current.cancel()
                } catch (oom: OutOfMemoryError) {
                    logVoiceFailure(oom, "releaseRecognizer cancel OOM")
                } catch (t: Throwable) {
                    logVoiceFailure(t, "releaseRecognizer cancel")
                }
            }
            try {
                current.destroy()
            } catch (oom: OutOfMemoryError) {
                logVoiceFailure(oom, "releaseRecognizer destroy OOM")
            } catch (t: Throwable) {
                logVoiceFailure(t, "releaseRecognizer destroy")
            }
        }
    }

    private fun createListener(session: VoiceSessionTicket): VoiceRecognizerCallback =
        object : VoiceRecognizerCallback {
        private fun isCurrent() = safeRun(false) { ownership.isCurrent(session) }

        override fun onReadyForSpeech() {
            safeApply {
                if (!isCurrent()) return@safeApply
                cancelStartWatchdog()
                recoverableErrorCount = 0
            }
        }

        override fun onBeginningOfSpeech() {
            safeApply {
                if (!isCurrent()) return@safeApply
                cancelStartWatchdog()
            }
        }

        override fun onEndOfSpeech() {
            safeApply {
                if (!isCurrent()) return@safeApply
                scheduleSpeechEndFallback(session)
            }
        }

        override fun onError(kind: VoiceErrorKind) {
            safeApply {
                if (!isCurrent()) return@safeApply
                cancelSpeechEndFallback()
                if (VoiceRecoveryPolicy.isRecoverable(kind)) {
                    recover()
                } else {
                    failSession(kind)
                }
            }
        }

        override fun onResults(result: String?) {
            safeApply {
                if (!isCurrent()) return@safeApply
                cancelSpeechEndFallback()
                // A blank final falls back to the last partial: the user already
                // saw that text, so it must be finalized, not dropped.
                val final = result?.takeIf { it.isNotBlank() } ?: lastPartial
                if (final.isNotBlank()) {
                    lastPartial = ""
                    recoverableErrorCount = 0
                    onFinal(final)
                }
                val restartGuard = ownership.invalidate()
                releaseRecognizer(cancel = false)
                restartIfNeeded(restartGuard)
            }
        }

        override fun onPartialResults(result: String?) {
            safeApply {
                if (!isCurrent()) return@safeApply
                result?.takeIf { it.isNotBlank() }?.let {
                    lastPartial = it
                    onPartial(it)
                }
            }
        }
    }

    private fun recover() {
        safeApply {
            val languageTag = activeLanguageTag
            if (userStopped || languageTag == null) return@safeApply

            flushLastPartial()
            recoverableErrorCount++
            if (!VoiceRecoveryPolicy.allowsRetry(recoverableErrorCount)) {
                failSession(VoiceErrorKind.TOO_MANY_REQUESTS)
                return@safeApply
            }

            val restartGuard = ownership.invalidate()
            releaseRecognizer(cancel = false)
            val delay = VoiceRecoveryPolicy.delayMillis(recoverableErrorCount)
            scheduleRestart(delay, languageTag, restartGuard)
        }
    }

    private fun failSession(kind: VoiceErrorKind) {
        userStopped = true
        activeLanguageTag = null
        activeAllowedLanguageTags = emptyList()
        lastPartial = ""
        ownership.invalidate()
        cancelAllTimers()
        releaseRecognizer(cancel = true)
        onFatalError(kind)
    }

    private fun flushLastPartial() {
        safeApply {
            val partial = lastPartial
            if (partial.isBlank()) return@safeApply
            lastPartial = ""
            onFinal(partial)
        }
    }

    private fun scheduleStartWatchdog(session: VoiceSessionTicket) {
        safeApply {
            cancelStartWatchdog()
            val token = Runnable {
                watchdogToken = null
                safeApply {
                    if (ownership.isCurrent(session) &&
                        !userStopped &&
                        activeLanguageTag != null
                    ) {
                        recover()
                    }
                }
            }
            watchdogToken = token
            try {
                scheduler.postDelayed(token, START_WATCHDOG_MILLIS)
            } catch (t: Throwable) {
                logVoiceFailure(t, "scheduleStartWatchdog")
            }
        }
    }

    private fun cancelStartWatchdog() {
        safeApply {
            watchdogToken?.let { token ->
                try {
                    scheduler.removeCallbacks(token)
                } catch (t: Throwable) {
                    logVoiceFailure(t, "cancelStartWatchdog")
                }
            }
            watchdogToken = null
        }
    }

    private fun scheduleSpeechEndFallback(session: VoiceSessionTicket) {
        safeApply {
            cancelSpeechEndFallback()
            val token = Runnable {
                speechEndToken = null
                safeApply {
                    if (ownership.isCurrent(session) && !userStopped) {
                        val restartGuard = ownership.invalidate()
                        flushLastPartial()
                        releaseRecognizer(cancel = false)
                        restartIfNeeded(restartGuard)
                    }
                }
            }
            speechEndToken = token
            try {
                scheduler.postDelayed(token, SPEECH_END_COMMIT_GRACE_MILLIS)
            } catch (t: Throwable) {
                logVoiceFailure(t, "scheduleSpeechEndFallback")
            }
        }
    }

    private fun cancelSpeechEndFallback() {
        safeApply {
            speechEndToken?.let { token ->
                try {
                    scheduler.removeCallbacks(token)
                } catch (t: Throwable) {
                    logVoiceFailure(t, "cancelSpeechEndFallback")
                }
            }
            speechEndToken = null
        }
    }

    private fun restartIfNeeded(restartGuard: VoiceSessionTicket) {
        safeApply {
            val languageTag = activeLanguageTag
            if (userStopped || languageTag == null) return@safeApply
            scheduleRestart(RESTART_DELAY_MILLIS, languageTag, restartGuard)
        }
    }

    private fun scheduleRestart(
        delay: Long,
        languageTag: String,
        restartGuard: VoiceSessionTicket
    ) {
        safeApply {
            cancelRestart()
            val token = Runnable {
                restartToken = null
                safeApply {
                    if (!userStopped &&
                        activeLanguageTag == languageTag &&
                        ownership.isGeneration(restartGuard)
                    ) {
                        startSession(languageTag)
                    }
                }
            }
            restartToken = token
            try {
                scheduler.postDelayed(token, delay)
            } catch (t: Throwable) {
                logVoiceFailure(t, "scheduleRestart")
            }
        }
    }

    private fun cancelRestart() {
        safeApply {
            restartToken?.let { token ->
                try {
                    scheduler.removeCallbacks(token)
                } catch (t: Throwable) {
                    logVoiceFailure(t, "cancelRestart")
                }
            }
            restartToken = null
        }
    }

    private fun cancelAllTimers() {
        safeApply {
            cancelStartWatchdog()
            cancelSpeechEndFallback()
            cancelRestart()
        }
    }

    private companion object {
        const val START_WATCHDOG_MILLIS = 4500L
        const val SPEECH_END_COMMIT_GRACE_MILLIS = 600L

        // The dead gap between one utterance's final and the next session
        // opening the mic. Kept short so mid-sentence words aren't lost;
        // RECOGNIZER_BUSY from restarting too fast is recoverable and backs
        // off via RECOVERY_DELAYS.
        const val RESTART_DELAY_MILLIS = 150L

    }
}
