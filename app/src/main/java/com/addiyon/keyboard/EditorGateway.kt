package com.addiyon.keyboard

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

internal data class EditorToken(
    val generation: Long,
    val connection: InputConnection
)

internal data class EditorRead<out T>(
    val value: T,
    val token: EditorToken
)

internal class EditorGateway(
    private val clockNanos: () -> Long = System::nanoTime,
    private val slowOptionalReadNanos: Long = 20_000_000L,
    private val connectionProvider: () -> InputConnection?
) {
    private var generation = 0L
    private var sessionConnection: InputConnection? = null
    private var optionalReadsEnabled = true
    private var sessionActive = true

    val sessionGeneration: Long
        get() = generation

    val allowsOptionalReads: Boolean
        get() = optionalReadsEnabled

    fun beginSession(): Long {
        generation += 1
        sessionActive = true
        sessionConnection = providedConnection()
        optionalReadsEnabled = true
        return generation
    }

    fun endSession() {
        invalidateSession()
    }

    private fun invalidateSession() {
        generation += 1
        sessionConnection = null
        optionalReadsEnabled = false
        sessionActive = false
    }

    fun currentToken(): EditorToken? =
        currentConnection()?.let { EditorToken(generation, it) }

    fun isCurrent(token: EditorToken): Boolean =
        token.generation == generation && currentConnection() === token.connection

    fun <T : Any> read(
        optional: Boolean = true,
        operation: (InputConnection) -> T?
    ): EditorRead<T>? {
        if (optional && !optionalReadsEnabled) return null
        val token = currentToken() ?: return null
        val started = clockNanos()
        val value = try {
            operation(token.connection)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "Editor read OOM")
            null
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor read")
            null
        }
        val elapsed = (clockNanos() - started).coerceAtLeast(0L)
        if (optional && elapsed > slowOptionalReadNanos) {
            optionalReadsEnabled = false
        }
        if (!isCurrent(token) || value == null) return null
        return EditorRead(value, token)
    }

    fun write(
        token: EditorToken? = null,
        operation: (InputConnection) -> Boolean
    ): Boolean {
        val current = currentConnection() ?: return false
        if (token != null && (token.generation != generation || token.connection !== current)) {
            return false
        }
        val accepted = try {
            operation(current)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "Editor write OOM")
            false
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor write")
            false
        }
        val stillCurrent = providedConnection() === current
        if (!accepted || !stillCurrent) {
            invalidateSession()
            return false
        }
        return true
    }

    fun textBeforeCursor(maxChars: Int, optional: Boolean = true): EditorRead<String>? =
        read(optional) { connection ->
            connection.getTextBeforeCursor(maxChars.coerceIn(1, MAX_READ_CHARS), 0)?.toString()
        }

    fun textAfterCursor(maxChars: Int, optional: Boolean = true): EditorRead<String>? =
        read(optional) { connection ->
            connection.getTextAfterCursor(maxChars.coerceIn(1, MAX_READ_CHARS), 0)?.toString()
        }

    fun selectedText(optional: Boolean = false): EditorRead<String>? =
        read(optional) { connection -> connection.getSelectedText(0)?.toString() }

    fun extractedText(optional: Boolean = true): EditorRead<ExtractedText>? =
        read(optional) { connection ->
            connection.getExtractedText(ExtractedTextRequest(), 0)
        }

    fun setComposingText(text: CharSequence, token: EditorToken? = null): Boolean =
        write(token) { it.setComposingText(text, 1) }

    fun setComposingTextAndSelection(
        text: CharSequence,
        selection: Int,
        token: EditorToken? = null
    ): Boolean {
        if (selection < 0) return false
        return write(token) { connection ->
            connection.beginBatchEdit()
            try {
                connection.setComposingText(text, 1) &&
                    connection.setSelection(selection, selection)
            } finally {
                connection.endBatchEdit()
            }
        }
    }

    fun finishComposingText(token: EditorToken? = null): Boolean =
        write(token) { it.finishComposingText() }

    fun commitText(text: CharSequence, token: EditorToken? = null): Boolean =
        write(token) { it.commitText(text, 1) }

    fun commitTextAndSelection(
        text: CharSequence,
        selection: Int,
        token: EditorToken? = null
    ): Boolean {
        if (selection < 0) return false
        return write(token) { connection ->
            connection.beginBatchEdit()
            try {
                connection.commitText(text, 1) &&
                    connection.setSelection(selection, selection)
            } finally {
                connection.endBatchEdit()
            }
        }
    }

    fun deleteBeforeCursor(chars: Int, token: EditorToken? = null): Boolean =
        write(token) { it.deleteSurroundingText(chars.coerceAtLeast(1), 0) }

    fun setComposingRegion(start: Int, end: Int, token: EditorToken? = null): Boolean {
        if (start < 0 || end < start) return false
        return write(token) { it.setComposingRegion(start, end) }
    }

    fun performEditorAction(action: Int): Boolean =
        write { it.performEditorAction(action) }

    fun sendEnter(): Boolean =
        write { connection ->
            val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            down && up
        }

    private fun currentConnection(): InputConnection? {
        if (!sessionActive) return null
        val current = providedConnection() ?: return null
        val expected = sessionConnection
        if (expected == null) {
            sessionConnection = current
            return current
        }
        return current.takeIf { it === expected }
    }

    private fun providedConnection(): InputConnection? =
        try {
            connectionProvider()
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "Editor connection OOM")
            null
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor connection")
            null
        }

    private companion object {
        const val MAX_READ_CHARS = 4096
    }
}
