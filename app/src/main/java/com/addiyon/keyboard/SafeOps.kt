package com.addiyon.keyboard

import android.os.Handler

internal inline fun <T> safeRun(default: T, block: () -> T): T = try {
    block()
} catch (oom: OutOfMemoryError) {
    SafeLog.e(oom, "safeRun OOM")
    default
} catch (t: Throwable) {
    SafeLog.e(t, "safeRun")
    default
}

internal inline fun safeApply(block: () -> Unit) {
    try {
        block()
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "safeApply OOM")
    } catch (t: Throwable) {
        SafeLog.e(t, "safeApply")
    }
}

internal fun safeOnMain(handler: Handler, block: () -> Unit) {
    try {
        if (!handler.post(Runnable { safeApply(block) })) {
            safeApply(block)
        }
    } catch (oom: OutOfMemoryError) {
        SafeLog.e(oom, "safeOnMain OOM")
    } catch (t: Throwable) {
        SafeLog.e(t, "safeOnMain")
    }
}

internal inline fun AddiyonKeyboardService.safeIc(
    crossinline block: (android.view.inputmethod.InputConnection) -> Boolean
): Boolean = editorGateway.write { block(it) }
