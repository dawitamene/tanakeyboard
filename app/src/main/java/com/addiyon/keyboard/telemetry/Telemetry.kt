package com.addiyon.keyboard.telemetry

import android.content.Context

object Telemetry {
    @Volatile
    private var controller: TelemetryController? = null
    @Volatile
    private var runtimeCollectionAllowed = false

    internal fun initialize(context: Context, runtimeAllowed: Boolean) {
        runtimeCollectionAllowed = runtimeAllowed
        val runtimeContext = context.applicationContext ?: context
        // TelemetryFeature.ENABLED is a compile-time const: while it is false
        // this folds to the no-op backend and R8 strips FirebaseTelemetryBackend
        // (and with it every reference to the Firebase SDK) from the build.
        val backend = if (TelemetryFeature.ENABLED) {
            FirebaseTelemetryBackend.create(runtimeContext)
        } else {
            NoOpTelemetryBackend
        }
        controller = TelemetryController(
            store = TelemetryPrefs(runtimeContext),
            backend = backend,
            runtimeAllowed = runtimeAllowed
        )
    }

    internal fun isRuntimeCollectionAllowed(): Boolean = runtimeCollectionAllowed

    internal fun installForTesting(
        store: TelemetryConsentStore,
        backend: TelemetryBackend,
        runtimeAllowed: Boolean
    ) {
        check(System.getProperty("com.addiyon.keyboard.instrumentation_runtime") == "true")
        runtimeCollectionAllowed = runtimeAllowed
        controller = TelemetryController(
            store = store,
            backend = backend,
            runtimeAllowed = runtimeAllowed
        )
    }

    fun consent(): DiagnosticsConsent =
        controller?.consent() ?: DiagnosticsConsent(
            analyticsEnabled = false,
            crashReportingEnabled = false,
            choiceSeen = false
        )

    fun setAnalyticsEnabled(enabled: Boolean): Boolean =
        controller?.setAnalyticsEnabled(enabled) ?: false

    fun setCrashReportingEnabled(enabled: Boolean): Boolean =
        controller?.setCrashReportingEnabled(enabled) ?: false

    fun markDiagnosticsChoiceSeen(): Boolean =
        controller?.markChoiceSeen() ?: false

    fun imeSessionStarted(language: TelemetryLanguage, privateField: Boolean) {
        controller?.event(TelemetryEvent.ImeSessionStart(language), privateField)
    }

    fun languageSwitched(destination: TelemetryLanguage, privateField: Boolean) {
        controller?.event(TelemetryEvent.LanguageSwitch(destination), privateField)
    }

    fun layoutOpened(layout: TelemetryLayout, privateField: Boolean) {
        controller?.event(TelemetryEvent.LayoutOpen(layout), privateField)
    }

    fun suggestionAccepted(kind: TelemetrySuggestionKind, privateField: Boolean) {
        controller?.event(TelemetryEvent.SuggestionAccept(kind), privateField)
    }

    fun voiceStarted(language: TelemetryLanguage, privateField: Boolean) {
        controller?.event(TelemetryEvent.VoiceStart(language), privateField)
    }

    fun voiceFinished(
        result: TelemetryVoiceResult,
        error: TelemetryVoiceError,
        privateField: Boolean
    ) {
        controller?.event(TelemetryEvent.VoiceFinish(result, error), privateField)
    }

    fun voiceFinished(result: TelemetryVoiceResult, privateField: Boolean) {
        controller?.event(TelemetryEvent.VoiceFinish(result, null), privateField)
    }

    fun onboardingCompleted() {
        controller?.event(TelemetryEvent.OnboardingComplete, privateField = false)
    }

    fun settingChanged(setting: TelemetrySetting, enabled: Boolean) {
        controller?.event(TelemetryEvent.SettingChange(setting, enabled), privateField = false)
    }

    internal fun recordNonFatal(category: NonFatalCategory, throwable: Throwable) {
        controller?.nonFatal(category, throwable)
    }
}

internal class TelemetryController(
    private val store: TelemetryConsentStore,
    private val backend: TelemetryBackend,
    private val runtimeAllowed: Boolean,
    private val rateLimiter: NonFatalRateLimiter = NonFatalRateLimiter(),
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val lock = Any()
    private var state = try {
        store.load()
    } catch (_: Throwable) {
        StoredTelemetryConsent()
    }

    init {
        applyStoredConsent()
    }

    fun consent(): DiagnosticsConsent = synchronized(lock) {
        DiagnosticsConsent(
            analyticsEnabled = state.analyticsEnabled,
            crashReportingEnabled = state.crashReportingEnabled,
            choiceSeen = state.choiceSeen
        )
    }

    fun setAnalyticsEnabled(enabled: Boolean): Boolean =
        synchronized(lock) {
            val previous = state
            val desired = state.copy(analyticsEnabled = enabled)
            val persisted = if (saveConsent(desired)) {
                state = desired
                true
            } else if (!enabled && clearConsent()) {
                state = StoredTelemetryConsent()
                if (saveConsent(desired)) {
                    state = desired
                }
                true
            } else {
                state = if (enabled) {
                    state.copy(analyticsEnabled = false)
                } else {
                    StoredTelemetryConsent()
                }
                false
            }
            val collectionEnabled = runtimeAllowed && state.analyticsEnabled
            safely { backend.setAnalyticsCollectionEnabled(collectionEnabled) }
            if (!collectionEnabled) {
                safely { backend.resetAnalyticsData() }
            } else {
                logFirstEnableIfNeeded()
            }
            if (previous.crashReportingEnabled && !state.crashReportingEnabled) {
                safely { backend.setCrashlyticsCollectionEnabled(false) }
                safely { backend.deleteUnsentReports() }
            }
            persisted
        }

    fun setCrashReportingEnabled(enabled: Boolean): Boolean =
        synchronized(lock) {
            val previous = state
            val desired = state.copy(crashReportingEnabled = enabled)
            val persisted = if (saveConsent(desired)) {
                state = desired
                true
            } else if (!enabled && clearConsent()) {
                state = StoredTelemetryConsent()
                if (saveConsent(desired)) {
                    state = desired
                }
                true
            } else {
                state = if (enabled) {
                    state.copy(crashReportingEnabled = false)
                } else {
                    StoredTelemetryConsent()
                }
                false
            }
            val collectionEnabled = runtimeAllowed && state.crashReportingEnabled
            safely { backend.setCrashlyticsCollectionEnabled(collectionEnabled) }
            if (!collectionEnabled) {
                safely { backend.deleteUnsentReports() }
            }
            if (previous.analyticsEnabled && !state.analyticsEnabled) {
                safely { backend.setAnalyticsCollectionEnabled(false) }
                safely { backend.resetAnalyticsData() }
            }
            persisted
        }

    fun markChoiceSeen(): Boolean =
        synchronized(lock) {
            val desired = state.copy(choiceSeen = true)
            if (saveConsent(desired)) {
                state = desired
                true
            } else {
                false
            }
        }

    fun event(event: TelemetryEvent, privateField: Boolean) {
        synchronized(lock) {
            if (
                !TelemetryPolicy.analyticsAllowed(
                    runtimeAllowed,
                    backend.available,
                    state,
                    privateField
                )
            ) {
                return
            }
            safely { backend.log(event) }
        }
    }

    fun nonFatal(category: NonFatalCategory, throwable: Throwable) {
        synchronized(lock) {
            if (
                !TelemetryPolicy.crashReportingAllowed(
                    runtimeAllowed,
                    backend.available,
                    state
                )
            ) {
                return
            }
            val report = NonFatalSanitizer.sanitize(category, throwable) ?: return
            val now = nowMillis()
            if (!rateLimiter.shouldReport(category, now)) return
            safely { backend.record(report) }
        }
    }

    private fun applyStoredConsent() {
        synchronized(lock) {
            val analyticsEnabled = runtimeAllowed && state.analyticsEnabled
            val crashReportingEnabled = runtimeAllowed && state.crashReportingEnabled
            safely { backend.setAnalyticsCollectionEnabled(analyticsEnabled) }
            safely { backend.setCrashlyticsCollectionEnabled(crashReportingEnabled) }
            if (!crashReportingEnabled) {
                safely { backend.deleteUnsentReports() }
            }
            if (analyticsEnabled) {
                logFirstEnableIfNeeded()
            }
        }
    }

    private fun logFirstEnableIfNeeded() {
        if (!runtimeAllowed || !backend.available || state.analyticsFirstEnableLogged) return
        if (!safely { backend.log(TelemetryEvent.AnalyticsFirstEnable) }) return
        state = state.copy(analyticsFirstEnableLogged = true)
        saveConsent(state)
    }

    private fun saveConsent(consent: StoredTelemetryConsent): Boolean =
        try {
            store.save(consent)
        } catch (_: Throwable) {
            false
        }

    private fun clearConsent(): Boolean =
        try {
            store.clear()
        } catch (_: Throwable) {
            false
        }

    private inline fun safely(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: Throwable) {
            false
        }
}
