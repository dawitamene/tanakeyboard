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
import com.addiyon.keyboard.safeRun

internal interface VoiceRecognizerCallback {
    fun onReadyForSpeech()
    fun onBeginningOfSpeech()
    fun onEndOfSpeech()
    fun onError(kind: VoiceErrorKind)
    fun onResults(result: String?)
    fun onPartialResults(result: String?)
}

internal interface VoiceRecognizerHandle {
    fun setCallback(callback: VoiceRecognizerCallback?)
    fun startListening(languageTag: String)
    fun cancel()
    fun destroy()
}

internal interface VoiceRecognizerFactory {
    fun isAvailable(): Boolean
    fun create(): VoiceRecognizerHandle
}

internal interface VoiceScheduler {
    fun postDelayed(token: Runnable, delayMillis: Long)
    fun removeCallbacks(token: Runnable)
}

internal class AndroidVoiceScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : VoiceScheduler {
    override fun postDelayed(token: Runnable, delayMillis: Long) {
        handler.postDelayed(token, delayMillis)
    }

    override fun removeCallbacks(token: Runnable) {
        handler.removeCallbacks(token)
    }
}

internal class AndroidVoiceRecognizerFactory(
    private val context: Context
) : VoiceRecognizerFactory {
    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun create(): VoiceRecognizerHandle =
        AndroidVoiceRecognizerHandle(
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        )
}

private class AndroidVoiceRecognizerHandle(
    private val recognizer: SpeechRecognizer
) : VoiceRecognizerHandle {
    override fun setCallback(callback: VoiceRecognizerCallback?) {
        recognizer.setRecognitionListener(
            callback?.let(::AndroidRecognitionListener)
        )
    }

    override fun startListening(languageTag: String) {
        recognizer.startListening(recognizerIntent(languageTag))
    }

    override fun cancel() {
        recognizer.cancel()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}

private class AndroidRecognitionListener(
    private val callback: VoiceRecognizerCallback
) : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {
        callback.onReadyForSpeech()
    }

    override fun onBeginningOfSpeech() {
        callback.onBeginningOfSpeech()
    }

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        callback.onEndOfSpeech()
    }

    override fun onError(error: Int) {
        callback.onError(voiceErrorKind(error))
    }

    override fun onResults(results: Bundle?) {
        callback.onResults(bestHypothesis(results))
    }

    override fun onPartialResults(partialResults: Bundle?) {
        callback.onPartialResults(bestHypothesis(partialResults))
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun bestHypothesis(results: Bundle?): String? =
        safeRun(null) {
            results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
        }
}

private fun recognizerIntent(languageTag: String): Intent =
    safeRun(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1800
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1800
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                1500
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf("am-ET", "en-US")
                )
            }
        }
    }

internal fun voiceErrorKind(error: Int): VoiceErrorKind = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> VoiceErrorKind.AUDIO
    SpeechRecognizer.ERROR_CLIENT -> VoiceErrorKind.CLIENT
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorKind.PERMISSION
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceErrorKind.NETWORK
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceErrorKind.NO_SPEECH
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorKind.RECOGNIZER_BUSY
    SpeechRecognizer.ERROR_SERVER -> VoiceErrorKind.SERVER
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceErrorKind.SERVER_DISCONNECTED
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceErrorKind.TOO_MANY_REQUESTS
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> VoiceErrorKind.LANGUAGE_UNSUPPORTED
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceErrorKind.LANGUAGE_UNAVAILABLE
    else -> VoiceErrorKind.UNKNOWN
}
