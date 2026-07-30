package com.addiyon.keyboard.telemetry

/**
 * Master compile-time switch for the Firebase telemetry backend.
 *
 * Telemetry is currently DISABLED and the whole stack is kept only so it can
 * be switched back on later. Nothing is collected, uploaded, or even linked:
 *
 *  - [ENABLED] is a `const`, so `Telemetry.initialize` folds down to
 *    [NoOpTelemetryBackend] at compile time and R8 drops
 *    [FirebaseTelemetryBackend] (the sole reference to the Firebase SDK, via
 *    reflection) out of the release build entirely.
 *  - `app/build.gradle.kts` mirrors this as `telemetryEnabled`, which gates
 *    both the `google-services`/`crashlytics` Gradle plugins and the Firebase
 *    dependencies, and forces `BuildConfig.TELEMETRY_COLLECTION_ALLOWED` off
 *    so [TelemetryPolicy] refuses every event as a second, independent lock.
 *
 * The consent store ([TelemetryPrefs]) and [TelemetryController] deliberately
 * stay live even while disabled: onboarding gates its "diagnostics choice
 * seen" step on a real persisted write (see `MainActivity`), so nulling the
 * controller out would strand the user on the privacy screen.
 *
 * To re-enable: flip this to `true`, flip `telemetryEnabled` in
 * `app/build.gradle.kts`, drop the `google-services.json` files back in, and
 * flip the `firebase_*_collection_enabled` manifest meta-data as needed.
 */
internal object TelemetryFeature {
    const val ENABLED = false
}
