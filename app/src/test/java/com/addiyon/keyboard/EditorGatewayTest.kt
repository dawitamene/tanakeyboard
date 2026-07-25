package com.addiyon.keyboard

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGatewayTest {

    @Test
    fun throwingAndRejectedMutationsFailClosed() {
        val throwing = connection { throw RuntimeException("dead editor") }
        val rejected = connection { method -> defaultValue(method.returnType) }

        val throwingGateway = EditorGateway { throwing }
        throwingGateway.beginSession()
        assertFalse(throwingGateway.commitText("a"))

        val rejectedGateway = EditorGateway { rejected }
        rejectedGateway.beginSession()
        assertFalse(rejectedGateway.commitText("a"))
        assertFalse(rejectedGateway.finishComposingText())
        assertFalse(rejectedGateway.deleteBeforeCursor(1))
    }

    @Test
    fun rejectedMutationPoisonsSessionSoLaterWritesCannotRewriteTheField() {
        val attempts = AtomicInteger()
        val input = connection { method ->
            if (method.name == "setComposingText") {
                attempts.incrementAndGet()
                false
            } else {
                defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession()

        assertFalse(gateway.setComposingText("a"))
        assertFalse(gateway.setComposingText("ab"))
        assertEquals(1, attempts.get())
    }

    @Test
    fun nullReadsAndNullConnectionReturnNoData() {
        val nullReads = connection { method -> defaultValue(method.returnType) }
        val gateway = EditorGateway { nullReads }
        gateway.beginSession()

        assertNull(gateway.textBeforeCursor(10))
        assertNull(gateway.textAfterCursor(10))
        assertNull(gateway.extractedText())

        val disconnected = EditorGateway { null }
        disconnected.beginSession()
        assertFalse(disconnected.commitText("a"))
        assertNull(disconnected.textBeforeCursor(10))
    }

    @Test
    fun slowOptionalReadDisablesFurtherOptionalRoundTrips() {
        val reads = AtomicInteger()
        val input = connection { method ->
            when (method.name) {
                "getTextBeforeCursor" -> {
                    reads.incrementAndGet()
                    "context"
                }
                else -> defaultValue(method.returnType)
            }
        }
        val times = ArrayDeque(listOf(0L, 25_000_001L))
        val gateway = EditorGateway(
            connectionProvider = { input },
            clockNanos = { times.removeFirstOrNull() ?: 25_000_001L }
        )
        gateway.beginSession()

        assertEquals("context", gateway.textBeforeCursor(10)?.value)
        assertFalse(gateway.allowsOptionalReads)
        assertNull(gateway.textBeforeCursor(10))
        assertEquals(1, reads.get())
        assertEquals("context", gateway.textBeforeCursor(10, optional = false)?.value)
    }

    @Test
    fun replacedConnectionIsRejectedUntilNextSession() {
        val firstWrites = AtomicInteger()
        val secondWrites = AtomicInteger()
        val first = connection { method ->
            if (method.name == "commitText") {
                firstWrites.incrementAndGet()
                true
            } else {
                defaultValue(method.returnType)
            }
        }
        val second = connection { method ->
            if (method.name == "commitText") {
                secondWrites.incrementAndGet()
                true
            } else {
                defaultValue(method.returnType)
            }
        }
        var current = first
        val gateway = EditorGateway { current }
        gateway.beginSession()

        assertTrue(gateway.commitText("first"))
        current = second
        assertFalse(gateway.commitText("stale"))
        assertEquals(1, firstWrites.get())
        assertEquals(0, secondWrites.get())

        gateway.beginSession()
        assertTrue(gateway.commitText("second"))
        assertEquals(1, secondWrites.get())
    }

    @Test
    fun connectionChangeDuringReadInvalidatesResultAndToken() {
        lateinit var second: InputConnection
        var current: InputConnection? = null
        val first = connection { method ->
            if (method.name == "getTextBeforeCursor") {
                current = second
                "stale"
            } else {
                defaultValue(method.returnType)
            }
        }
        second = connection { method -> defaultValue(method.returnType) }
        current = first
        val gateway = EditorGateway { current }
        gateway.beginSession()
        val token = gateway.currentToken()

        assertNull(gateway.textBeforeCursor(10, optional = false))
        assertFalse(gateway.isCurrent(requireNotNull(token)))
    }

    @Test
    fun endedSessionRejectsLateReadsAndWritesUntilNextSession() {
        val input = connection { method ->
            when (method.name) {
                "commitText" -> true
                "getTextBeforeCursor" -> "field"
                else -> defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession()
        gateway.endSession()

        assertFalse(gateway.commitText("late"))
        assertNull(gateway.textBeforeCursor(10, optional = false))

        gateway.beginSession()
        assertTrue(gateway.commitText("current"))
    }

    @Test
    fun throwingConnectionProviderCannotCrashSessionStart() {
        val gateway = EditorGateway(
            connectionProvider = { throw RuntimeException("binder lookup failed") }
        )

        gateway.beginSession()

        assertFalse(gateway.commitText("a"))
        assertNull(gateway.textBeforeCursor(1, optional = false))
    }

    private fun connection(handler: (java.lang.reflect.Method) -> Any?): InputConnection =
        Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, _ ->
            if (method.name == "closeConnection") Unit else handler(method)
        } as InputConnection

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Void.TYPE -> Unit
        else -> null
    }
}
