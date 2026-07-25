package com.addiyon.keyboard.util

import android.os.Debug
import android.util.Log

object MemoryProbe {
    private const val TAG = "MemoryProbe"
    private val enabled: Boolean = try {
        val cls = Class.forName("com.addiyon.keyboard.BuildConfig")
        val f = cls.getField("DEBUG")
        f.getBoolean(null)
    } catch (_: Throwable) {
        false
    }

    fun snapshot(label: String) {
        if (!enabled) return
        val rt = Runtime.getRuntime()
        val javaUsed = rt.totalMemory() - rt.freeMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        val pss = try {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            mi.totalPss * 1024L
        } catch (_: Throwable) {
            -1L
        }
        Log.i(
            TAG,
            "[$label] javaUsed=${javaUsed / 1024}KB nativeHeap=${nativeHeap / 1024}KB pss=${pss / 1024}KB",
        )
    }

    fun isEnabled(): Boolean = enabled
}
