package com.addiyon.keyboard

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.addiyon.keyboard.telemetry.Telemetry
import com.addiyon.keyboard.telemetry.TelemetryPrefs

class TelemetryTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader,
        className: String,
        context: Context
    ): Application {
        System.setProperty(
            "com.addiyon.keyboard.instrumentation_runtime",
            "true"
        )
        context.getSharedPreferences(TelemetryPrefs.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Telemetry.initialize(context, runtimeAllowed = false)
        return super.newApplication(classLoader, className, context)
    }
}
