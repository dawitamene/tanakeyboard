package com.addiyon.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeOpsTest {

    @Test
    fun safeRunReturnsBlockResultOnSuccess() {
        val result = safeRun(default = "fallback") { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun safeRunReturnsDefaultOnThrowable() {
        val result = safeRun(default = "fallback") {
            throw RuntimeException("boom")
        }
        assertEquals("fallback", result)
    }

    @Test
    fun safeRunReturnsDefaultOnOutOfMemoryError() {
        val result = safeRun(default = "fallback") {
            throw OutOfMemoryError("oom")
        }
        assertEquals("fallback", result)
    }

    @Test
    fun safeRunReturnsDefaultOnError() {
        val result = safeRun(default = "fallback") {
            throw Error("generic")
        }
        assertEquals("fallback", result)
    }

    @Test
    fun safeRunReturnsNullDefaultWhenAsked() {
        val result: String? = safeRun<String?>(default = null) {
            throw IllegalStateException("nope")
        }
        assertNull(result)
    }

    @Test
    fun safeApplyRunsBlockOnSuccess() {
        var ran = false
        safeApply { ran = true }
        assertTrue(ran)
    }

    @Test
    fun safeApplySwallowsThrowable() {
        val ran = booleanArrayOf(false)
        safeApply {
            ran[0] = true
            throw RuntimeException("boom")
        }
        assertTrue(ran[0])
    }

    @Test
    fun safeApplySwallowsOutOfMemoryError() {
        val ran = booleanArrayOf(false)
        safeApply {
            ran[0] = true
            throw OutOfMemoryError("oom")
        }
        assertTrue(ran[0])
    }
}
