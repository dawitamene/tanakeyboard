package com.addiyon.keyboard.suggestion

import android.os.Build
import android.os.Trace

internal object SuggestionTrace {
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection("Addiyon.$name")
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun beginAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection("Addiyon.$name", cookie)
        }
    }

    fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection("Addiyon.$name", cookie)
        }
    }
}
