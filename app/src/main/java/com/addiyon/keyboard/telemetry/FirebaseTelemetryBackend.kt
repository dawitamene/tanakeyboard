package com.addiyon.keyboard.telemetry

import android.content.Context
import android.os.Bundle

internal class FirebaseTelemetryBackend private constructor(
    private val analytics: Any,
    private val crashlytics: Any
) : TelemetryBackend {
    override val available = true

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        val granted = if (enabled) "GRANTED" else "DENIED"
        val consent = linkedMapOf(
            enumConstant(ANALYTICS_CONSENT_TYPE, "ANALYTICS_STORAGE") to
                enumConstant(ANALYTICS_CONSENT_STATUS, granted),
            enumConstant(ANALYTICS_CONSENT_TYPE, "AD_STORAGE") to
                enumConstant(ANALYTICS_CONSENT_STATUS, "DENIED"),
            enumConstant(ANALYTICS_CONSENT_TYPE, "AD_USER_DATA") to
                enumConstant(ANALYTICS_CONSENT_STATUS, "DENIED"),
            enumConstant(ANALYTICS_CONSENT_TYPE, "AD_PERSONALIZATION") to
                enumConstant(ANALYTICS_CONSENT_STATUS, "DENIED")
        )
        analytics.javaClass
            .getMethod("setConsent", Map::class.java)
            .invoke(analytics, consent)
        analytics.javaClass
            .getMethod("setAnalyticsCollectionEnabled", java.lang.Boolean.TYPE)
            .invoke(analytics, enabled)
    }

    override fun resetAnalyticsData() {
        analytics.javaClass.getMethod("resetAnalyticsData").invoke(analytics)
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.javaClass
            .getMethod("setCrashlyticsCollectionEnabled", java.lang.Boolean.TYPE)
            .invoke(crashlytics, enabled)
    }

    override fun deleteUnsentReports() {
        crashlytics.javaClass.getMethod("deleteUnsentReports").invoke(crashlytics)
    }

    override fun log(event: TelemetryEvent) {
        when (event) {
            TelemetryEvent.AnalyticsFirstEnable ->
                logEvent(TelemetrySchema.ANALYTICS_FIRST_ENABLE, null)
            is TelemetryEvent.ImeSessionStart ->
                logEvent(
                    TelemetrySchema.IME_SESSION_START,
                    bundle(TelemetrySchema.LANGUAGE, event.language.wireName)
                )
            is TelemetryEvent.LanguageSwitch ->
                logEvent(
                    TelemetrySchema.LANGUAGE_SWITCH,
                    bundle(TelemetrySchema.DESTINATION, event.destination.wireName)
                )
            is TelemetryEvent.LayoutOpen ->
                logEvent(
                    TelemetrySchema.LAYOUT_OPEN,
                    bundle(TelemetrySchema.LAYOUT, event.layout.wireName)
                )
            is TelemetryEvent.SuggestionAccept ->
                logEvent(
                    TelemetrySchema.SUGGESTION_ACCEPT,
                    bundle(TelemetrySchema.KIND, event.kind.wireName)
                )
            is TelemetryEvent.VoiceStart ->
                logEvent(
                    TelemetrySchema.VOICE_START,
                    bundle(TelemetrySchema.LANGUAGE, event.language.wireName)
                )
            is TelemetryEvent.VoiceFinish -> {
                val parameters = bundle(TelemetrySchema.RESULT, event.result.wireName)
                event.error?.let {
                    parameters.putString(TelemetrySchema.ERROR_CATEGORY, it.wireName)
                }
                logEvent(TelemetrySchema.VOICE_FINISH, parameters)
            }
            TelemetryEvent.OnboardingComplete ->
                logEvent(TelemetrySchema.ONBOARDING_COMPLETE, null)
            is TelemetryEvent.SettingChange -> {
                val parameters = bundle(TelemetrySchema.SETTING, event.setting.wireName)
                parameters.putLong(TelemetrySchema.ENABLED, if (event.enabled) 1L else 0L)
                logEvent(TelemetrySchema.SETTING_CHANGE, parameters)
            }
        }
    }

    override fun record(report: SanitizedNonFatal) {
        crashlytics.javaClass
            .getMethod("setCustomKey", String::class.java, String::class.java)
            .invoke(
                crashlytics,
                FAILURE_CATEGORY_KEY,
                report.category.wireName
            )
        crashlytics.javaClass
            .getMethod("setCustomKey", String::class.java, String::class.java)
            .invoke(
                crashlytics,
                THROWABLE_CLASS_KEY,
                report.throwableClass.wireName
            )
        crashlytics.javaClass
            .getMethod("recordException", Throwable::class.java)
            .invoke(crashlytics, report.exception())
    }

    private fun logEvent(name: String, parameters: Bundle?) {
        analytics.javaClass
            .getMethod("logEvent", String::class.java, Bundle::class.java)
            .invoke(analytics, name, parameters)
    }

    private fun bundle(key: String, value: String): Bundle =
        Bundle().apply { putString(key, value) }

    internal companion object {
        private const val FIREBASE_APP = "com.google.firebase.FirebaseApp"
        private const val FIREBASE_ANALYTICS =
            "com.google.firebase.analytics.FirebaseAnalytics"
        private const val FIREBASE_CRASHLYTICS =
            "com.google.firebase.crashlytics.FirebaseCrashlytics"
        private const val ANALYTICS_CONSENT_TYPE =
            "com.google.firebase.analytics.FirebaseAnalytics\$ConsentType"
        private const val ANALYTICS_CONSENT_STATUS =
            "com.google.firebase.analytics.FirebaseAnalytics\$ConsentStatus"
        private const val FAILURE_CATEGORY_KEY = "failure_category"
        private const val THROWABLE_CLASS_KEY = "coarse_throwable"

        fun create(context: Context): TelemetryBackend {
            return try {
                val appClass = Class.forName(FIREBASE_APP)
                val apps = appClass
                    .getMethod("getApps", Context::class.java)
                    .invoke(null, context) as? List<*>
                val app = apps?.firstOrNull()
                    ?: appClass
                        .getMethod("initializeApp", Context::class.java)
                        .invoke(null, context)
                    ?: return NoOpTelemetryBackend
                val defaultName = appClass.getField("DEFAULT_APP_NAME").get(null)
                val appName = appClass.getMethod("getName").invoke(app)
                if (appName != defaultName) return NoOpTelemetryBackend
                val analyticsClass = Class.forName(FIREBASE_ANALYTICS)
                val crashlyticsClass = Class.forName(FIREBASE_CRASHLYTICS)
                FirebaseTelemetryBackend(
                    analytics = analyticsClass
                        .getMethod("getInstance", Context::class.java)
                        .invoke(null, context),
                    crashlytics = crashlyticsClass
                        .getMethod("getInstance")
                        .invoke(null)
                )
            } catch (_: Throwable) {
                NoOpTelemetryBackend
            }
        }

        private fun enumConstant(className: String, name: String): Any {
            val constants = Class.forName(className).enumConstants
                ?: throw IllegalStateException()
            return constants.first { (it as Enum<*>).name == name }
        }
    }
}
