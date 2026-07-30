package com.addiyon.keyboard.telemetry

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telemetry is deliberately switched off while the stack is kept in the tree
 * (see [TelemetryFeature]). These tests pin the two independent locks that
 * make that true so neither can be lost by accident:
 *
 *  1. the Kotlin `const` that folds the Firebase backend out of the build, and
 *  2. the Gradle switch that stops the SDK being linked at all and forces
 *     `TELEMETRY_COLLECTION_ALLOWED` off for every variant.
 *
 * When telemetry is intentionally re-enabled, flip [TelemetryFeature.ENABLED]
 * and `telemetryEnabled`, then delete this class.
 */
class TelemetryDisabledContractTest {
    @Test
    fun featureSwitchIsOff() {
        assertFalse(TelemetryFeature.ENABLED)
    }

    @Test
    fun initializeSelectsTheBackendThroughTheFeatureSwitch() {
        val telemetry = moduleFile(
            "src/main/java/com/addiyon/keyboard/telemetry/Telemetry.kt"
        ).readText()

        // The Firebase backend must be reachable only behind the const switch,
        // otherwise R8 cannot prove it dead and strip it.
        assertTrue(telemetry.contains("if (TelemetryFeature.ENABLED)"))
        assertTrue(telemetry.contains("NoOpTelemetryBackend"))
        val firebaseReference = "FirebaseTelemetryBackend.create(runtimeContext)"
        assertTrue(telemetry.contains(firebaseReference))
        assertTrue(
            "FirebaseTelemetryBackend must be referenced exactly once, inside the switch",
            telemetry.windowed(firebaseReference.length).count { it == firebaseReference } == 1
        )
    }

    @Test
    fun gradleSwitchIsOffAndGatesFirebaseWiring() {
        val build = moduleFile("build.gradle.kts").readText()

        assertTrue(build.contains("val telemetryEnabled = false"))
        // Firebase plugins and dependencies hang off hasAnyFirebaseConfig, so
        // gating that one value keeps the SDK unlinked even if a
        // google-services.json is dropped back in.
        assertTrue(
            build.contains(
                "val hasAnyFirebaseConfig = telemetryEnabled &&"
            )
        )
        // Every variant compiles with runtime collection refused, which is the
        // second lock: TelemetryPolicy rejects events even if a live backend
        // somehow appeared.
        assertFalse(
            build.contains(
                "buildConfigField(\"boolean\", \"TELEMETRY_COLLECTION_ALLOWED\", \"true\")"
            )
        )
        assertTrue(build.contains("telemetryEnabled.toString()"))
    }

    @Test
    fun noOpBackendRefusesEveryEventEvenWithFullConsent() {
        val store = FakeTelemetryStore(
            StoredTelemetryConsent(
                analyticsEnabled = true,
                crashReportingEnabled = true,
                choiceSeen = true
            )
        )
        val controller = TelemetryController(
            store = store,
            backend = NoOpTelemetryBackend,
            runtimeAllowed = false
        )

        controller.event(TelemetryEvent.OnboardingComplete, privateField = false)
        controller.nonFatal(NonFatalCategory.EDITOR, IllegalStateException("boom"))

        assertFalse(
            TelemetryPolicy.analyticsAllowed(
                runtimeAllowed = false,
                backendAvailable = NoOpTelemetryBackend.available,
                consent = store.value,
                privateField = false
            )
        )
        assertFalse(
            TelemetryPolicy.crashReportingAllowed(
                runtimeAllowed = false,
                backendAvailable = NoOpTelemetryBackend.available,
                consent = store.value
            )
        )
    }

    @Test
    fun consentStoreStaysUsableSoOnboardingCanStillComplete() {
        // MainActivity only leaves the privacy screen when a real write lands,
        // so disabling telemetry must not disable the consent store.
        val store = FakeTelemetryStore()
        val controller = TelemetryController(
            store = store,
            backend = NoOpTelemetryBackend,
            runtimeAllowed = false
        )

        assertTrue(controller.markChoiceSeen())
        assertTrue(controller.consent().choiceSeen)
    }

    private fun moduleFile(relativePath: String): File =
        listOf(
            File(relativePath),
            File("app/$relativePath")
        ).first(File::exists)
}
