package com.addiyon.keyboard.telemetry

import android.content.Context
import android.content.SharedPreferences

internal class TelemetryPrefs(context: Context) : TelemetryConsentStore {
    private val runtimeContext = context.applicationContext ?: context
    private val prefs = runtimeContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): StoredTelemetryConsent {
        val consent = StoredTelemetryConsent(
            analyticsEnabled = boolean(KEY_ANALYTICS),
            crashReportingEnabled = boolean(KEY_CRASH_REPORTING),
            choiceSeen = boolean(KEY_CHOICE_SEEN),
            analyticsFirstEnableLogged = boolean(KEY_FIRST_ENABLE_LOGGED)
        )
        repair(consent)
        return consent
    }

    override fun save(consent: StoredTelemetryConsent): Boolean =
        try {
            prefs.edit()
                .putBoolean(KEY_ANALYTICS, consent.analyticsEnabled)
                .putBoolean(KEY_CRASH_REPORTING, consent.crashReportingEnabled)
                .putBoolean(KEY_CHOICE_SEEN, consent.choiceSeen)
                .putBoolean(KEY_FIRST_ENABLE_LOGGED, consent.analyticsFirstEnableLogged)
                .commit()
        } catch (_: Throwable) {
            false
        }

    override fun clear(): Boolean {
        try {
            if (prefs.edit().clear().commit()) return true
        } catch (_: Throwable) {
        }
        return try {
            runtimeContext.deleteSharedPreferences(FILE_NAME)
        } catch (_: Throwable) {
            false
        }
    }

    private fun boolean(key: String): Boolean = try {
        booleanValue(prefs.all[key])
    } catch (_: Throwable) {
        false
    }

    private fun repair(consent: StoredTelemetryConsent) {
        val malformed = try {
            listOf(
                KEY_ANALYTICS,
                KEY_CRASH_REPORTING,
                KEY_CHOICE_SEEN,
                KEY_FIRST_ENABLE_LOGGED
            ).any { key -> prefs.all[key]?.let { it !is Boolean } == true }
        } catch (_: Throwable) {
            false
        }
        if (malformed) {
            save(consent)
        }
    }

    internal companion object {
        const val FILE_NAME = "addiyon_telemetry_prefs"
        const val KEY_ANALYTICS = "analytics_enabled"
        const val KEY_CRASH_REPORTING = "crash_reporting_enabled"
        const val KEY_CHOICE_SEEN = "diagnostics_choice_seen"
        const val KEY_FIRST_ENABLE_LOGGED = "analytics_first_enable_logged"

        fun booleanValue(raw: Any?): Boolean = raw as? Boolean ?: false
    }
}
