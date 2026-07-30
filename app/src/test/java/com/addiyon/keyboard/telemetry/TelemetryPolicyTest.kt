package com.addiyon.keyboard.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPolicyTest {
    @Test
    fun privateFieldsSuppressEveryCustomAnalyticsEvent() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(analyticsEnabled = true)
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)
        backend.events.clear()

        controller.event(
            TelemetryEvent.ImeSessionStart(TelemetryLanguage.ENGLISH),
            privateField = true
        )
        controller.event(
            TelemetryEvent.LanguageSwitch(TelemetryLanguage.AMHARIC),
            privateField = true
        )

        assertTrue(backend.events.isEmpty())
    }

    @Test
    fun nonPrivateEventRequiresConsentAndAvailableBackend() {
        val denied = StoredTelemetryConsent(analyticsEnabled = false)
        val allowed = denied.copy(analyticsEnabled = true)

        assertFalse(TelemetryPolicy.analyticsAllowed(true, true, denied, false))
        assertFalse(TelemetryPolicy.analyticsAllowed(true, false, allowed, false))
        assertFalse(TelemetryPolicy.analyticsAllowed(false, true, allowed, false))
        assertFalse(TelemetryPolicy.analyticsAllowed(true, true, allowed, true))
        assertTrue(TelemetryPolicy.analyticsAllowed(true, true, allowed, false))
    }

    @Test
    fun crashReportingRequiresRuntimeBackendAndConsent() {
        val denied = StoredTelemetryConsent(crashReportingEnabled = false)
        val allowed = denied.copy(crashReportingEnabled = true)

        assertFalse(TelemetryPolicy.crashReportingAllowed(false, true, allowed))
        assertFalse(TelemetryPolicy.crashReportingAllowed(true, false, allowed))
        assertFalse(TelemetryPolicy.crashReportingAllowed(true, true, denied))
        assertTrue(TelemetryPolicy.crashReportingAllowed(true, true, allowed))
    }

    @Test
    fun caughtFailureRequiresRuntimeBackendAndCrashConsent() {
        val denied = StoredTelemetryConsent(crashReportingEnabled = false)
        val allowed = denied.copy(crashReportingEnabled = true)

        assertFalse(TelemetryPolicy.crashReportingAllowed(true, true, denied))
        assertFalse(TelemetryPolicy.crashReportingAllowed(false, true, allowed))
        assertFalse(TelemetryPolicy.crashReportingAllowed(true, false, allowed))
        assertTrue(TelemetryPolicy.crashReportingAllowed(true, true, allowed))
    }

    @Test
    fun optedInNonPrivateEventIsEmittedWithEnumsOnly() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                analyticsFirstEnableLogged = true
            )
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.event(
            TelemetryEvent.SuggestionAccept(TelemetrySuggestionKind.PREDICTION),
            privateField = false
        )

        assertEquals(
            listOf(TelemetryEvent.SuggestionAccept(TelemetrySuggestionKind.PREDICTION)),
            backend.events
        )
    }

    @Test
    fun optedInNonFatalsAreSanitizedRateLimitedAndSkipOutOfMemory() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(crashReportingEnabled = true)
        )
        val backend = FakeTelemetryBackend()
        val times = ArrayDeque(listOf(1_000L, 1_050L, 1_051L, 1_100L))
        val controller = TelemetryController(
            store = store,
            backend = backend,
            runtimeAllowed = true,
            rateLimiter = NonFatalRateLimiter(intervalMillis = 100),
            nowMillis = { times.removeFirst() }
        )

        controller.nonFatal(NonFatalCategory.DATABASE, IllegalStateException("secret"))
        controller.nonFatal(NonFatalCategory.DATABASE, IllegalStateException("secret"))
        controller.nonFatal(NonFatalCategory.VOICE, IllegalStateException("secret"))
        controller.nonFatal(NonFatalCategory.DATABASE, IllegalStateException("secret"))
        controller.nonFatal(NonFatalCategory.DATABASE, OutOfMemoryError("secret"))

        assertEquals(3, backend.reports.size)
        assertEquals(
            listOf(
                NonFatalCategory.DATABASE,
                NonFatalCategory.VOICE,
                NonFatalCategory.DATABASE
            ),
            backend.reports.map { it.category }
        )
    }
}
