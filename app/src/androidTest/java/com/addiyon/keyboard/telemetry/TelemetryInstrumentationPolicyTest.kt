package com.addiyon.keyboard.telemetry

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryInstrumentationPolicyTest {
    @Test
    fun instrumentationRuntimeForcesCollectionOff() {
        assertFalse(Telemetry.isRuntimeCollectionAllowed())
    }

    @Test
    fun realPreferencesPersistIndependentChoicesAcrossControllerReinitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(TelemetryPrefs.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val firstBackend = RecordingBackend()
            val first = TelemetryController(
                store = TelemetryPrefs(context),
                backend = firstBackend,
                runtimeAllowed = true
            )
            first.setAnalyticsEnabled(true)
            first.setCrashReportingEnabled(false)
            first.markChoiceSeen()

            val restoredBackend = RecordingBackend()
            val restored = TelemetryController(
                store = TelemetryPrefs(context),
                backend = restoredBackend,
                runtimeAllowed = true
            )

            assertEquals(
                DiagnosticsConsent(
                    analyticsEnabled = true,
                    crashReportingEnabled = false,
                    choiceSeen = true
                ),
                restored.consent()
            )
            assertEquals(listOf(true), restoredBackend.analyticsCollection)
            assertEquals(listOf(false), restoredBackend.crashCollection)
            assertTrue(restoredBackend.events.isEmpty())

            restored.setAnalyticsEnabled(false)
            restored.setCrashReportingEnabled(true)
            val secondRestoredBackend = RecordingBackend()
            val secondRestored = TelemetryController(
                store = TelemetryPrefs(context),
                backend = secondRestoredBackend,
                runtimeAllowed = true
            )

            assertEquals(
                DiagnosticsConsent(
                    analyticsEnabled = false,
                    crashReportingEnabled = true,
                    choiceSeen = true
                ),
                secondRestored.consent()
            )
            assertEquals(listOf(false), secondRestoredBackend.analyticsCollection)
            assertEquals(listOf(true), secondRestoredBackend.crashCollection)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun clearingDedicatedConsentStoreIsDurablyAllOffAcrossReinitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(TelemetryPrefs.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = TelemetryPrefs(context)
            assertTrue(
                store.save(
                    StoredTelemetryConsent(
                        analyticsEnabled = true,
                        crashReportingEnabled = true,
                        choiceSeen = true
                    )
                )
            )

            assertTrue(store.clear())

            assertEquals(
                StoredTelemetryConsent(),
                TelemetryPrefs(context).load()
            )
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun malformedPersistedValuesAreRepairedToOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(TelemetryPrefs.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putString(TelemetryPrefs.KEY_ANALYTICS, "true")
            .putInt(TelemetryPrefs.KEY_CRASH_REPORTING, 1)
            .putString(TelemetryPrefs.KEY_CHOICE_SEEN, "yes")
            .putString(TelemetryPrefs.KEY_FIRST_ENABLE_LOGGED, "false")
            .commit()
        try {
            val restored = TelemetryPrefs(context).load()

            assertFalse(restored.analyticsEnabled)
            assertFalse(restored.crashReportingEnabled)
            assertFalse(restored.choiceSeen)
            assertTrue(prefs.all[TelemetryPrefs.KEY_ANALYTICS] is Boolean)
            assertTrue(prefs.all[TelemetryPrefs.KEY_CRASH_REPORTING] is Boolean)
            assertTrue(prefs.all[TelemetryPrefs.KEY_CHOICE_SEEN] is Boolean)
            assertTrue(prefs.all[TelemetryPrefs.KEY_FIRST_ENABLE_LOGGED] is Boolean)
            assertEquals(restored, TelemetryPrefs(context).load())
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private class RecordingBackend : TelemetryBackend {
        override val available = true
        val analyticsCollection = mutableListOf<Boolean>()
        val crashCollection = mutableListOf<Boolean>()
        val events = mutableListOf<TelemetryEvent>()

        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
            analyticsCollection += enabled
        }

        override fun resetAnalyticsData() = Unit

        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
            crashCollection += enabled
        }

        override fun deleteUnsentReports() = Unit

        override fun log(event: TelemetryEvent) {
            events += event
        }

        override fun record(report: SanitizedNonFatal) = Unit
    }
}
