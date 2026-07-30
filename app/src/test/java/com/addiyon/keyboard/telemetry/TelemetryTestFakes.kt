package com.addiyon.keyboard.telemetry

internal class FakeTelemetryStore(
    initial: StoredTelemetryConsent = StoredTelemetryConsent()
) : TelemetryConsentStore {
    var value = initial
    var saves = 0
    var saveSucceeds = true
    var clears = 0
    var clearSucceeds = true

    override fun load(): StoredTelemetryConsent = value

    override fun save(consent: StoredTelemetryConsent): Boolean {
        if (!saveSucceeds) return false
        value = consent
        saves += 1
        return true
    }

    override fun clear(): Boolean {
        if (!clearSucceeds) return false
        value = StoredTelemetryConsent()
        clears += 1
        return true
    }
}

internal class FakeTelemetryBackend(
    override val available: Boolean = true
) : TelemetryBackend {
    val analyticsCollection = mutableListOf<Boolean>()
    val crashCollection = mutableListOf<Boolean>()
    val events = mutableListOf<TelemetryEvent>()
    val reports = mutableListOf<SanitizedNonFatal>()
    var analyticsResets = 0
    var deletedReports = 0
    var throwOnLog = false

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analyticsCollection += enabled
    }

    override fun resetAnalyticsData() {
        analyticsResets += 1
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashCollection += enabled
    }

    override fun deleteUnsentReports() {
        deletedReports += 1
    }

    override fun log(event: TelemetryEvent) {
        if (throwOnLog) throw IllegalStateException()
        events += event
    }

    override fun record(report: SanitizedNonFatal) {
        reports += report
    }
}
