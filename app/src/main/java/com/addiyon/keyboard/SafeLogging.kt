package com.addiyon.keyboard

internal object SafeLog {
    private const val TAG = "AddiyonKb"

    fun e(t: Throwable, msg: String) {
        try {
            android.util.Log.e(TAG, msg, t)
        } catch (_: Throwable) {
        }
    }

    fun w(msg: String) {
        try {
            android.util.Log.w(TAG, msg)
        } catch (_: Throwable) {
        }
    }
}
