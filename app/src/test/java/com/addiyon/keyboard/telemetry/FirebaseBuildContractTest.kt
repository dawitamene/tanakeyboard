package com.addiyon.keyboard.telemetry

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseBuildContractTest {
    @Test
    fun benchmarkAndInstrumentationRuntimeCollectionAreForcedOff() {
        val build = moduleFile("build.gradle.kts").readText()
        val app = moduleFile(
            "src/main/java/com/addiyon/keyboard/AddiyonApp.kt"
        ).readText()
        val runner = moduleFile(
            "src/androidTest/java/com/addiyon/keyboard/TelemetryTestRunner.kt"
        ).readText()

        listOf(
            "name == \"benchmark\"",
            "name == \"benchmarkRelease\"",
            "name == \"nonMinifiedRelease\"",
            "buildConfigField(\"boolean\", \"TELEMETRY_COLLECTION_ALLOWED\", \"false\")",
            "requireNotNull(variant.buildConfigFields).put(",
            "BuildConfigField(\"boolean\", false, null)",
            "verifyBenchmarkTelemetryBuildConfig"
        ).forEach { assertTrue(it, build.contains(it)) }
        assertTrue(app.contains("!isInstrumentationRuntime()"))
        assertTrue(app.contains("com.addiyon.keyboard.instrumentation_runtime"))
        assertTrue(build.contains("com.addiyon.keyboard.TelemetryTestRunner"))
        assertTrue(runner.contains("TelemetryPrefs.FILE_NAME"))
        assertTrue(runner.contains("Telemetry.initialize(context, runtimeAllowed = false)"))
    }

    @Test
    fun productionVerificationRequiresFirebaseAndMappingUploadWiring() {
        val build = moduleFile("build.gradle.kts").readText()
        val verifier = projectFile("plans/verify-release-artifact.sh").readText()

        listOf(
            "verifyProductionFirebaseConfig",
            "verifyReleaseCrashlyticsWiring",
            "injectCrashlyticsMappingFileIdRelease",
            "uploadCrashlyticsMappingFileRelease"
        ).forEach { assertTrue(it, build.contains(it)) }
        listOf(
            "build/crashlytics/release/mappingFileId.txt",
            "firebaseProductionAppId",
            "firebaseProductionProjectId",
            "<uses-permission[^>]*android:name",
            "com\\.addiyon\\.keyboard\\.(benchmarkhost|debug)",
            "telemetry_fatal",
            "telemetry_non_fatal",
            "ImeTestCommandReceiver"
        ).forEach { assertTrue(it, verifier.contains(it)) }
    }

    @Test
    fun reflectionBackendHasNarrowReleaseKeepRules() {
        val rules = moduleFile("proguard-rules.pro").readText()

        listOf(
            "class com.google.firebase.FirebaseApp",
            "class com.google.firebase.analytics.FirebaseAnalytics",
            "FirebaseAnalytics\$ConsentType",
            "FirebaseAnalytics\$ConsentStatus",
            "class com.google.firebase.crashlytics.FirebaseCrashlytics",
            "setConsent(java.util.Map)",
            "recordException(java.lang.Throwable)"
        ).forEach { assertTrue(it, rules.contains(it)) }
        assertFalse(rules.contains("com.google.firebase.**"))
    }

    private fun moduleFile(relativePath: String): File =
        listOf(
            File(relativePath),
            File("app/$relativePath")
        ).first(File::exists)

    private fun projectFile(relativePath: String): File =
        listOf(
            File(relativePath),
            File("../$relativePath")
        ).first(File::exists)
}
