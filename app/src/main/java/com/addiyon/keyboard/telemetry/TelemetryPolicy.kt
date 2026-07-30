package com.addiyon.keyboard.telemetry

data class DiagnosticsConsent(
    val analyticsEnabled: Boolean,
    val crashReportingEnabled: Boolean,
    val choiceSeen: Boolean
)

internal data class StoredTelemetryConsent(
    val analyticsEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = false,
    val choiceSeen: Boolean = false,
    val analyticsFirstEnableLogged: Boolean = false
)

internal interface TelemetryConsentStore {
    fun load(): StoredTelemetryConsent
    fun save(consent: StoredTelemetryConsent): Boolean
    fun clear(): Boolean
}

internal object TelemetryPolicy {
    fun analyticsAllowed(
        runtimeAllowed: Boolean,
        backendAvailable: Boolean,
        consent: StoredTelemetryConsent,
        privateField: Boolean
    ): Boolean = runtimeAllowed &&
        backendAvailable &&
        consent.analyticsEnabled &&
        !privateField

    fun crashReportingAllowed(
        runtimeAllowed: Boolean,
        backendAvailable: Boolean,
        consent: StoredTelemetryConsent
    ): Boolean = runtimeAllowed &&
        backendAvailable &&
        consent.crashReportingEnabled
}
