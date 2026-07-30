package com.addiyon.keyboard

import com.addiyon.keyboard.telemetry.NonFatalCategory
import com.addiyon.keyboard.telemetry.Telemetry

internal object SafeLog {
    private const val TAG = "AddiyonKb"

    fun e(
        t: Throwable,
        msg: String,
        category: NonFatalCategory = NonFatalCategory.APPLICATION_OPERATION
    ) {
        try {
            if (BuildConfig.DEBUG) {
                android.util.Log.e(TAG, msg, t)
            } else {
                android.util.Log.e(TAG, category.name)
            }
        } catch (_: Throwable) {
        }
        Telemetry.recordNonFatal(category, t)
    }

    fun w(msg: String) {
        try {
            android.util.Log.w(TAG, if (BuildConfig.DEBUG) msg else "warning")
        } catch (_: Throwable) {
        }
    }
}
