package com.addiyon.keyboard

import android.app.Application
import com.addiyon.keyboard.telemetry.Telemetry

class AddiyonApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Telemetry.initialize(
            this,
            BuildConfig.TELEMETRY_COLLECTION_ALLOWED && !isInstrumentationRuntime()
        )
    }

    private fun isInstrumentationRuntime(): Boolean = try {
        System.getProperty(INSTRUMENTATION_RUNTIME_PROPERTY) == "true"
    } catch (_: Throwable) {
        false
    }

    private companion object {
        const val INSTRUMENTATION_RUNTIME_PROPERTY =
            "com.addiyon.keyboard.instrumentation_runtime"
    }
}
