package com.addiyon.keyboard.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputControllerTest {
    @Test
    fun activeSessionPublishesRefinementsAndOnlyOneFinal() {
        val harness = harness()
        harness.controller.start("en-US")
        val recognizer = harness.factory.latest
        val callback = requireNotNull(recognizer.activeCallback)

        callback.onPartialResults(null)
        callback.onPartialResults(" ")
        callback.onPartialResults("hel")
        callback.onPartialResults("hel")
        callback.onEndOfSpeech()
        callback.onResults("hello")
        callback.onResults("duplicate")
        callback.onPartialResults("late")

        assertEquals(listOf("hel", "hel"), harness.partials)
        assertEquals(listOf("hello"), harness.finals)
        assertTrue(harness.fatals.isEmpty())
        assertTrue(recognizer.destroyed)
        assertFalse(recognizer.cancelled)
        assertNull(recognizer.activeCallback)

        harness.scheduler.advanceBy(150)

        assertEquals(2, harness.factory.recognizers.size)
    }

    @Test
    fun blankFinalFallsBackToTheLastVisiblePartial() {
        val harness = harness()
        harness.controller.start("en-US")
        val callback = requireNotNull(harness.factory.latest.activeCallback)

        callback.onPartialResults("visible draft")
        callback.onResults("   ")
        callback.onResults(null)

        assertEquals(listOf("visible draft"), harness.finals)
    }

    @Test
    fun restartStopAndDestroyInvalidateAllEarlierCallbacksAndTimers() {
        val harness = harness()
        harness.controller.start("en-US")
        val first = harness.factory.latest
        val firstCallback = requireNotNull(first.activeCallback)
        firstCallback.onPartialResults("first draft")

        harness.controller.restartSession()

        assertTrue(first.cancelled)
        assertTrue(first.destroyed)
        assertTrue(harness.finals.isEmpty())
        assertEquals(2, harness.factory.recognizers.size)
        firstCallback.onResults("late first")
        firstCallback.onPartialResults("late partial")

        val second = harness.factory.latest
        val secondCallback = requireNotNull(second.activeCallback)
        secondCallback.onPartialResults("second draft")
        harness.controller.stop()

        assertTrue(second.cancelled)
        assertTrue(second.destroyed)
        assertEquals(0, harness.scheduler.pendingCount)
        secondCallback.onResults("late second")
        assertTrue(harness.finals.isEmpty())

        harness.controller.start("am-ET")
        val third = harness.factory.latest
        harness.controller.destroy()

        assertTrue(third.cancelled)
        assertTrue(third.destroyed)
        assertEquals(0, harness.scheduler.pendingCount)
        harness.controller.start("en-US")
        assertEquals(3, harness.factory.recognizers.size)
    }

    @Test
    fun readyAndBeginningCallbacksCancelTheStartWatchdog() {
        val readyHarness = harness()
        readyHarness.controller.start("en-US")
        requireNotNull(readyHarness.factory.latest.activeCallback).onReadyForSpeech()
        readyHarness.scheduler.advanceBy(5_000)
        assertEquals(1, readyHarness.factory.recognizers.size)

        val beginningHarness = harness()
        beginningHarness.controller.start("en-US")
        requireNotNull(beginningHarness.factory.latest.activeCallback).onBeginningOfSpeech()
        beginningHarness.scheduler.advanceBy(5_000)
        assertEquals(1, beginningHarness.factory.recognizers.size)
    }

    @Test
    fun startWatchdogFinalizesVisiblePartialAndRecoversWithBackoff() {
        val harness = harness()
        harness.controller.start("en-US")
        val first = harness.factory.latest
        val firstCallback = requireNotNull(first.activeCallback)
        firstCallback.onPartialResults("watchdog draft")

        harness.scheduler.advanceBy(4_499)
        assertTrue(harness.finals.isEmpty())
        harness.scheduler.advanceBy(1)

        assertEquals(listOf("watchdog draft"), harness.finals)
        assertTrue(first.destroyed)
        assertFalse(first.cancelled)
        firstCallback.onResults("late")

        harness.scheduler.advanceBy(299)
        assertEquals(1, harness.factory.recognizers.size)
        harness.scheduler.advanceBy(1)
        assertEquals(2, harness.factory.recognizers.size)
    }

    @Test
    fun speechEndFallbackFinalizesOnceUnlessARealFinalWinsTheRace() {
        val fallbackHarness = harness()
        fallbackHarness.controller.start("en-US")
        val fallbackCallback =
            requireNotNull(fallbackHarness.factory.latest.activeCallback)
        fallbackCallback.onReadyForSpeech()
        fallbackCallback.onPartialResults("fallback")
        fallbackCallback.onEndOfSpeech()

        fallbackHarness.scheduler.advanceBy(599)
        assertTrue(fallbackHarness.finals.isEmpty())
        fallbackHarness.scheduler.advanceBy(1)
        assertEquals(listOf("fallback"), fallbackHarness.finals)
        fallbackCallback.onResults("late final")
        assertEquals(listOf("fallback"), fallbackHarness.finals)

        val finalHarness = harness()
        finalHarness.controller.start("en-US")
        val finalCallback = requireNotNull(finalHarness.factory.latest.activeCallback)
        finalCallback.onReadyForSpeech()
        finalCallback.onPartialResults("draft")
        finalCallback.onEndOfSpeech()
        finalCallback.onResults("real final")
        finalHarness.scheduler.advanceBy(600)

        assertEquals(listOf("real final"), finalHarness.finals)
    }

    @Test
    fun recoverableErrorFinalizesPartialAndRejectsLateOldSessionCallbacks() {
        val harness = harness()
        harness.controller.start("en-US")
        val firstCallback = requireNotNull(harness.factory.latest.activeCallback)
        firstCallback.onPartialResults("recover me")

        firstCallback.onError(VoiceErrorKind.NO_SPEECH)
        firstCallback.onResults("late")

        assertEquals(listOf("recover me"), harness.finals)
        assertTrue(harness.fatals.isEmpty())
        harness.scheduler.advanceBy(299)
        assertEquals(1, harness.factory.recognizers.size)
        harness.scheduler.advanceBy(1)
        assertEquals(2, harness.factory.recognizers.size)
    }

    @Test
    fun repeatedRecoverableErrorsEventuallyBecomeOneFatalError() {
        val harness = harness()
        harness.controller.start("en-US")
        val delays = listOf(300L, 700L, 1_500L, 2_500L)

        delays.forEach { delay ->
            requireNotNull(harness.factory.latest.activeCallback)
                .onError(VoiceErrorKind.RECOGNIZER_BUSY)
            harness.scheduler.advanceBy(delay)
        }
        val last = harness.factory.latest
        val lastCallback = requireNotNull(last.activeCallback)
        lastCallback.onError(VoiceErrorKind.CLIENT)
        lastCallback.onError(VoiceErrorKind.CLIENT)

        assertEquals(listOf(VoiceErrorKind.TOO_MANY_REQUESTS), harness.fatals)
        assertTrue(last.cancelled)
        assertTrue(last.destroyed)
        assertEquals(0, harness.scheduler.pendingCount)
    }

    @Test
    fun fatalCallbackStopsImmediatelyAndUnavailableStartFailsClosed() {
        val fatalHarness = harness()
        fatalHarness.controller.start("en-US")
        val recognizer = fatalHarness.factory.latest
        val callback = requireNotNull(recognizer.activeCallback)

        callback.onError(VoiceErrorKind.PERMISSION)
        callback.onError(VoiceErrorKind.SERVER)
        fatalHarness.scheduler.advanceBy(10_000)

        assertEquals(listOf(VoiceErrorKind.PERMISSION), fatalHarness.fatals)
        assertTrue(recognizer.cancelled)
        assertTrue(recognizer.destroyed)
        assertEquals(0, fatalHarness.scheduler.pendingCount)

        val unavailableHarness = harness(available = false)
        assertFalse(unavailableHarness.controller.isAvailable)
        unavailableHarness.controller.start("en-US")

        assertEquals(listOf(VoiceErrorKind.UNAVAILABLE), unavailableHarness.fatals)
        assertTrue(unavailableHarness.factory.recognizers.isEmpty())
    }

    @Test
    fun platformErrorCodesMapToReviewedKinds() {
        val cases = mapOf(
            SpeechRecognizer.ERROR_AUDIO to VoiceErrorKind.AUDIO,
            SpeechRecognizer.ERROR_CLIENT to VoiceErrorKind.CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to VoiceErrorKind.PERMISSION,
            SpeechRecognizer.ERROR_NETWORK to VoiceErrorKind.NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT to VoiceErrorKind.NETWORK,
            SpeechRecognizer.ERROR_NO_MATCH to VoiceErrorKind.NO_SPEECH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to VoiceErrorKind.NO_SPEECH,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to VoiceErrorKind.RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER to VoiceErrorKind.SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED to VoiceErrorKind.SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to VoiceErrorKind.TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to VoiceErrorKind.LANGUAGE_UNSUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to VoiceErrorKind.LANGUAGE_UNAVAILABLE
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, voiceErrorKind(error))
        }
        assertEquals(VoiceErrorKind.UNKNOWN, voiceErrorKind(Int.MIN_VALUE))
    }

    private fun harness(available: Boolean = true): Harness {
        val factory = FakeVoiceRecognizerFactory(available)
        val scheduler = FakeVoiceScheduler()
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val fatals = mutableListOf<VoiceErrorKind>()
        return Harness(
            factory = factory,
            scheduler = scheduler,
            partials = partials,
            finals = finals,
            fatals = fatals,
            controller = VoiceInputController(
                recognizerFactory = factory,
                scheduler = scheduler,
                onPartial = partials::add,
                onFinal = finals::add,
                onFatalError = fatals::add
            )
        )
    }

    private data class Harness(
        val factory: FakeVoiceRecognizerFactory,
        val scheduler: FakeVoiceScheduler,
        val partials: MutableList<String>,
        val finals: MutableList<String>,
        val fatals: MutableList<VoiceErrorKind>,
        val controller: VoiceInputController
    )
}

private class FakeVoiceRecognizerFactory(
    var available: Boolean
) : VoiceRecognizerFactory {
    val recognizers = mutableListOf<FakeVoiceRecognizer>()

    val latest: FakeVoiceRecognizer
        get() = recognizers.last()

    override fun isAvailable(): Boolean = available

    override fun create(): VoiceRecognizerHandle =
        FakeVoiceRecognizer().also(recognizers::add)
}

private class FakeVoiceRecognizer : VoiceRecognizerHandle {
    var activeCallback: VoiceRecognizerCallback? = null
    val startedLanguages = mutableListOf<String>()
    var cancelled = false
    var destroyed = false

    override fun setCallback(callback: VoiceRecognizerCallback?) {
        activeCallback = callback
    }

    override fun startListening(languageTag: String) {
        startedLanguages += languageTag
    }

    override fun cancel() {
        cancelled = true
    }

    override fun destroy() {
        destroyed = true
    }
}

private class FakeVoiceScheduler : VoiceScheduler {
    private data class Scheduled(
        val token: Runnable,
        val dueMillis: Long,
        val sequence: Long
    )

    private val scheduled = mutableListOf<Scheduled>()
    private var nowMillis = 0L
    private var nextSequence = 0L

    val pendingCount: Int
        get() = scheduled.size

    override fun postDelayed(token: Runnable, delayMillis: Long) {
        scheduled += Scheduled(
            token = token,
            dueMillis = nowMillis + delayMillis,
            sequence = nextSequence++
        )
    }

    override fun removeCallbacks(token: Runnable) {
        scheduled.removeAll { it.token === token }
    }

    fun advanceBy(deltaMillis: Long) {
        require(deltaMillis >= 0)
        val target = nowMillis + deltaMillis
        while (true) {
            val next = scheduled
                .filter { it.dueMillis <= target }
                .minWithOrNull(
                    compareBy<Scheduled> { it.dueMillis }
                        .thenBy { it.sequence }
                )
                ?: break
            scheduled.remove(next)
            nowMillis = next.dueMillis
            next.token.run()
        }
        nowMillis = target
    }
}
