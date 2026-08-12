package com.addiyon.keyboard

object SafeLog {
    private const val TAG = "AddiyonKb"

    @Volatile
    private var debug = false

    fun configure(isDebug: Boolean) {
        debug = isDebug
    }

    fun e(t: Throwable, msg: String) {
        try {
            if (debug) {
                android.util.Log.e(TAG, msg, t)
            } else {
                android.util.Log.e(TAG, "operation_failed")
            }
        } catch (_: Throwable) {
        }
    }

    fun w(msg: String) {
        try {
            android.util.Log.w(TAG, if (debug) msg else "warning")
        } catch (_: Throwable) {
        }
    }
}
