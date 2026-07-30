package com.addiyon.keyboard.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryConsentPolicyTest {
    @Test
    fun freshInstallDefaultsBothCollectionsOff() {
        val store = FakeTelemetryStore()
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        assertEquals(DiagnosticsConsent(false, false, false), controller.consent())
        assertEquals(listOf(false), backend.analyticsCollection)
        assertEquals(listOf(false), backend.crashCollection)
        assertEquals(1, backend.deletedReports)
        assertTrue(backend.events.isEmpty())
    }

    @Test
    fun analyticsAndCrashChoicesAreIndependent() {
        val store = FakeTelemetryStore()
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setAnalyticsEnabled(true)
        assertTrue(controller.consent().analyticsEnabled)
        assertFalse(controller.consent().crashReportingEnabled)
        assertEquals(listOf(TelemetryEvent.AnalyticsFirstEnable), backend.events)

        controller.setCrashReportingEnabled(true)
        assertTrue(controller.consent().analyticsEnabled)
        assertTrue(controller.consent().crashReportingEnabled)

        controller.setAnalyticsEnabled(false)
        assertFalse(controller.consent().analyticsEnabled)
        assertTrue(controller.consent().crashReportingEnabled)
        assertEquals(1, backend.analyticsResets)
    }

    @Test
    fun firstEnableEventIsLoggedOnlyOnce() {
        val store = FakeTelemetryStore()
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setAnalyticsEnabled(true)
        controller.setAnalyticsEnabled(false)
        controller.setAnalyticsEnabled(true)

        assertEquals(
            1,
            backend.events.count { it == TelemetryEvent.AnalyticsFirstEnable }
        )
    }

    @Test
    fun markingDiagnosticsChoicePersistsTheDecision() {
        val store = FakeTelemetryStore()
        val controller = TelemetryController(
            store,
            FakeTelemetryBackend(),
            runtimeAllowed = true
        )

        controller.markChoiceSeen()

        assertTrue(controller.consent().choiceSeen)
        assertTrue(store.value.choiceSeen)
    }

    @Test
    fun firstEnableEventRetriesAfterBackendFailure() {
        val store = FakeTelemetryStore()
        val backend = FakeTelemetryBackend().apply { throwOnLog = true }
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setAnalyticsEnabled(true)
        assertFalse(store.value.analyticsFirstEnableLogged)

        backend.throwOnLog = false
        controller.setAnalyticsEnabled(true)

        assertTrue(store.value.analyticsFirstEnableLogged)
        assertEquals(listOf(TelemetryEvent.AnalyticsFirstEnable), backend.events)
    }

    @Test
    fun savedAnalyticsConsentAppliesAndRecordsFirstEnableAtStartup() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(analyticsEnabled = true)
        )
        val backend = FakeTelemetryBackend()

        TelemetryController(store, backend, runtimeAllowed = true)

        assertEquals(listOf(true), backend.analyticsCollection)
        assertEquals(listOf(TelemetryEvent.AnalyticsFirstEnable), backend.events)
        assertTrue(store.value.analyticsFirstEnableLogged)
    }

    @Test
    fun diagnosticsChoiceSeenPersistsWithoutChangingEitherConsent() {
        val store = FakeTelemetryStore()
        val controller = TelemetryController(
            store,
            FakeTelemetryBackend(),
            runtimeAllowed = true
        )

        controller.markChoiceSeen()

        assertEquals(DiagnosticsConsent(false, false, true), controller.consent())
        assertTrue(store.value.choiceSeen)
    }

    @Test
    fun failedConsentPersistenceCannotEnableCollectionOrAdvanceTheChoice() {
        val store = FakeTelemetryStore().apply {
            saveSucceeds = false
        }
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setAnalyticsEnabled(true)
        controller.setCrashReportingEnabled(true)
        controller.markChoiceSeen()

        assertEquals(DiagnosticsConsent(false, false, false), controller.consent())
        assertEquals(false, backend.analyticsCollection.last())
        assertEquals(false, backend.crashCollection.last())
        assertEquals(2, backend.deletedReports)
        assertEquals(1, backend.analyticsResets)
        assertTrue(backend.events.isEmpty())
    }

    @Test
    fun failedRevocationFallsBackToDurableAllOffStateAcrossReinitialization() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                crashReportingEnabled = true
            )
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)
        store.saveSucceeds = false

        assertTrue(controller.setAnalyticsEnabled(false))

        assertFalse(controller.consent().analyticsEnabled)
        assertFalse(controller.consent().crashReportingEnabled)
        assertEquals(false, backend.analyticsCollection.last())
        assertEquals(false, backend.crashCollection.last())
        assertEquals(1, backend.analyticsResets)
        assertEquals(1, backend.deletedReports)
        assertEquals(1, store.clears)

        val restartedBackend = FakeTelemetryBackend()
        val restarted = TelemetryController(store, restartedBackend, runtimeAllowed = true)

        assertEquals(DiagnosticsConsent(false, false, false), restarted.consent())
        assertEquals(listOf(false), restartedBackend.analyticsCollection)
        assertEquals(listOf(false), restartedBackend.crashCollection)
    }

    @Test
    fun totalRevocationStorageFailureIsReportedAndKeepsTheLiveProcessOff() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                crashReportingEnabled = true
            )
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)
        store.saveSucceeds = false
        store.clearSucceeds = false

        assertFalse(controller.setAnalyticsEnabled(false))

        assertEquals(DiagnosticsConsent(false, false, false), controller.consent())
        assertEquals(false, backend.analyticsCollection.last())
        assertEquals(false, backend.crashCollection.last())
        assertEquals(1, backend.analyticsResets)
        assertEquals(1, backend.deletedReports)
        controller.event(TelemetryEvent.OnboardingComplete, privateField = false)
        assertEquals(
            listOf(TelemetryEvent.AnalyticsFirstEnable),
            backend.events
        )
    }

    @Test
    fun revokingCrashConsentDeletesQueuedReports() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(crashReportingEnabled = true)
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setCrashReportingEnabled(false)

        assertEquals(false, backend.crashCollection.last())
        assertEquals(1, backend.deletedReports)
    }

    @Test
    fun disabledRuntimeCannotCollectEvenWithStoredConsent() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                crashReportingEnabled = true
            )
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = false)

        controller.event(
            TelemetryEvent.LayoutOpen(TelemetryLayout.EMOJI),
            privateField = false
        )
        controller.nonFatal(
            NonFatalCategory.APPLICATION_OPERATION,
            IllegalStateException("secret")
        )

        assertEquals(listOf(false), backend.analyticsCollection)
        assertEquals(listOf(false), backend.crashCollection)
        assertTrue(backend.events.isEmpty())
        assertTrue(backend.reports.isEmpty())
        assertEquals(1, backend.deletedReports)
    }

    @Test
    fun disabledRuntimeKeepsLaterEnableRequestsLocallyOffAtTheBackend() {
        val store = FakeTelemetryStore()
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(store, backend, runtimeAllowed = false)

        controller.setAnalyticsEnabled(true)
        controller.setCrashReportingEnabled(true)

        assertEquals(false, backend.analyticsCollection.last())
        assertEquals(false, backend.crashCollection.last())
        assertTrue(backend.events.isEmpty())
        assertEquals(2, backend.deletedReports)
    }

    @Test
    fun unavailableBackendCannotEmitFirstEnableEventsOrReports() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                crashReportingEnabled = true
            )
        )
        val backend = FakeTelemetryBackend(available = false)
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.event(TelemetryEvent.OnboardingComplete, privateField = false)
        controller.nonFatal(NonFatalCategory.REVIEW, IllegalStateException("secret"))

        assertTrue(backend.events.isEmpty())
        assertTrue(backend.reports.isEmpty())
        assertFalse(store.value.analyticsFirstEnableLogged)
    }

    @Test
    fun backendFailuresNeverEscapeConsentEventOrNonFatalPaths() {
        val backend = object : TelemetryBackend {
            override val available = true
            override fun setAnalyticsCollectionEnabled(enabled: Boolean) = error("backend")
            override fun resetAnalyticsData() = error("backend")
            override fun setCrashlyticsCollectionEnabled(enabled: Boolean) = error("backend")
            override fun deleteUnsentReports() = error("backend")
            override fun log(event: TelemetryEvent) = error("backend")
            override fun record(report: SanitizedNonFatal) = error("backend")
        }
        val store = FakeTelemetryStore()
        val controller = TelemetryController(store, backend, runtimeAllowed = true)

        controller.setAnalyticsEnabled(true)
        controller.event(TelemetryEvent.OnboardingComplete, privateField = false)
        controller.setAnalyticsEnabled(false)
        controller.setCrashReportingEnabled(true)
        controller.nonFatal(NonFatalCategory.UPDATE, IllegalStateException("secret"))
        controller.setCrashReportingEnabled(false)

        assertFalse(controller.consent().analyticsEnabled)
        assertFalse(controller.consent().crashReportingEnabled)
        assertFalse(store.value.analyticsFirstEnableLogged)
    }

    @Test
    fun optedInCrashReportingSanitizesAndRateLimitsCaughtFailures() {
        var now = 1_000L
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(crashReportingEnabled = true)
        )
        val backend = FakeTelemetryBackend()
        val controller = TelemetryController(
            store = store,
            backend = backend,
            runtimeAllowed = true,
            rateLimiter = NonFatalRateLimiter(intervalMillis = 100),
            nowMillis = { now }
        )
        val failure = IllegalStateException("typed secret").apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "com.addiyon.keyboard.EditorGateway",
                    "commit",
                    "EditorGateway.kt",
                    10
                )
            )
        }

        controller.nonFatal(NonFatalCategory.EDITOR, failure)
        controller.nonFatal(NonFatalCategory.EDITOR, failure)
        now = 1_100L
        controller.nonFatal(NonFatalCategory.EDITOR, failure)

        assertEquals(2, backend.reports.size)
        backend.reports.forEach {
            assertEquals(NonFatalCategory.EDITOR, it.category)
            assertEquals(CoarseThrowableClass.ILLEGAL_STATE, it.throwableClass)
        }
    }

    @Test
    fun malformedPreferenceValuesResolveToOff() {
        assertFalse(TelemetryPrefs.booleanValue(null))
        assertFalse(TelemetryPrefs.booleanValue("true"))
        assertFalse(TelemetryPrefs.booleanValue(1))
        assertTrue(TelemetryPrefs.booleanValue(true))
    }
}
