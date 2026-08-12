package com.addiyon.keyboard.voice

import android.util.Log

internal inline fun <T> safeRun(default: T, block: () -> T): T = try {
    block()
} catch (oom: OutOfMemoryError) {
    logVoiceFailure(oom, "voice operation OOM")
    default
} catch (failure: Throwable) {
    logVoiceFailure(failure, "voice operation")
    default
}

internal inline fun safeApply(block: () -> Unit) {
    try {
        block()
    } catch (oom: OutOfMemoryError) {
        logVoiceFailure(oom, "voice operation OOM")
    } catch (failure: Throwable) {
        logVoiceFailure(failure, "voice operation")
    }
}

internal fun logVoiceFailure(failure: Throwable, operation: String) {
    try {
        Log.e("AddiyonVoice", operation, failure)
    } catch (_: Throwable) {
    }
}
