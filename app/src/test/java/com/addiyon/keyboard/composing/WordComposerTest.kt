package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordComposerTest {

    private class RecordingInputConnection {
        val commits = mutableListOf<String>()
        val composingUpdates = mutableListOf<String>()
        var finishCount = 0

        private val connection: InputConnection by lazy {
            Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "setComposingText" -> {
                        composingUpdates += args?.getOrNull(0)?.toString().orEmpty()
                        true
                    }
                    "finishComposingText" -> {
                        finishCount++
                        true
                    }
                    "commitText" -> {
                        commits += args?.getOrNull(0)?.toString().orEmpty()
                        true
                    }
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> true
                        java.lang.Integer.TYPE -> 0
                        java.lang.Void.TYPE -> null
                        else -> null
                    }
                }
            } as InputConnection
        }

        fun asInputConnection(): InputConnection = connection
    }

    // No InputConnection is needed to exercise the composer's own state
    // machine -- inputConnection() is only ever used via a safe call
    // (`inputConnection()?.commitText(...)`), so returning null here just
    // skips the (untestable-without-Android) field-write side effect while
    // leaving buffer/raw/onCommit behavior fully exercisable.
    private fun composer(
        commitTransform: (String) -> String = { it },
        onCommit: (raw: String, display: String) -> Unit = { _, _ -> },
        inputConnection: (() -> InputConnection?)? = null
    ): WordComposer {
        val recording = RecordingInputConnection()
        return WordComposer(
            inputConnection = inputConnection ?: { recording.asInputConnection() },
            commitTransform = commitTransform,
            onCommit = onCommit
        )
    }

    @Test
    fun onCharacterAppendsToBufferAndIsComposing() {
        val c = composer()
        assertFalse(c.isComposing)
        c.onCharacter("i")
        c.onCharacter("n")
        c.onCharacter("f")
        assertTrue(c.isComposing)
        assertEquals("inf", c.raw)
    }

    @Test
    fun resumeSeedsBufferAndSubsequentCharactersExtendIt() {
        val c = composer()
        c.resume("info")
        assertTrue(c.isComposing)
        assertEquals("info", c.raw)

        c.onCharacter("r")
        c.onCharacter("m")
        c.onCharacter("a")
        c.onCharacter("t")
        c.onCharacter("i")
        c.onCharacter("o")
        c.onCharacter("n")
        assertEquals("information", c.raw)
    }

    @Test
    fun resumeWithEmptyPrefixIsANoOp() {
        val c = composer()
        c.resume("")
        assertFalse(c.isComposing)
    }

    @Test
    fun commitEmitsTransformedFormAndClearsBuffer() {
        var committedRaw: String? = null
        var committedDisplay: String? = null
        val c = composer(
            commitTransform = { it.uppercase() },
            onCommit = { raw, display -> committedRaw = raw; committedDisplay = display }
        )
        c.onCharacter("h")
        c.onCharacter("i")
        c.commit()

        assertFalse(c.isComposing)
        assertEquals("hi", committedRaw)
        assertEquals("HI", committedDisplay)
    }

    @Test
    fun commitRecomputesTransformFromTheCurrentBufferNotACachedValue() {
        // commitTransform must be invoked at commit time, off the buffer as
        // it stands then -- never a value cached from an earlier keystroke
        // (e.g. what the suggestion strip last showed).
        var calls = 0
        val c = composer(commitTransform = { raw -> calls++; raw.uppercase() })
        c.onCharacter("h")
        assertEquals(0, calls)
        c.onCharacter("i")
        c.commit()
        assertEquals(1, calls)
    }

    @Test
    fun commitOnEmptyBufferDoesNotInvokeOnCommit() {
        var invoked = false
        val c = composer(onCommit = { _, _ -> invoked = true })
        c.commit()
        assertFalse(invoked)
    }

    @Test
    fun resumeThenCommitRoundTripsTheFullWord() {
        var committedRaw: String? = null
        val c = composer(
            commitTransform = { it.uppercase() },
            onCommit = { raw, _ -> committedRaw = raw }
        )
        c.resume("info")
        c.onCharacter("r")
        c.onCharacter("m")
        c.onCharacter("a")
        c.onCharacter("t")
        c.onCharacter("i")
        c.onCharacter("o")
        c.onCharacter("n")
        c.commit()

        assertEquals("information", committedRaw)
        assertFalse(c.isComposing)
    }

    @Test
    fun onBackspaceRemovesOneUnitAtATime() {
        val c = composer()
        c.onCharacter("a")
        c.onCharacter("b")
        c.onCharacter("c")
        assertTrue(c.onBackspace())
        assertEquals("ab", c.raw)
        assertTrue(c.onBackspace())
        assertTrue(c.onBackspace())
        assertFalse(c.isComposing)
        // Buffer now empty -- caller's fallback should kick in.
        assertFalse(c.onBackspace())
    }
    @Test
    fun finishFinalizesVisibleTextWithoutClearingReplacingOrReportingIt() {
        val recording = RecordingInputConnection()
        var commits = 0
        val c = composer(
            commitTransform = { it.uppercase() },
            onCommit = { _, _ -> commits++ },
            inputConnection = { recording.asInputConnection() }
        )
        c.onCharacter("h")
        c.onCharacter("i")
        c.finish()

        assertEquals(listOf("h", "hi"), recording.composingUpdates)
        assertEquals(emptyList<String>(), recording.commits)
        assertEquals(1, recording.finishCount)
        assertEquals(0, commits)
        assertFalse(c.isComposing)
    }

    @Test
    fun abandonFinalizesVisibleTextWithoutClearingReplacingOrReportingIt() {
        val recording = RecordingInputConnection()
        var commits = 0
        val c = composer(
            commitTransform = { it.uppercase() },
            onCommit = { _, _ -> commits++ },
            inputConnection = { recording.asInputConnection() }
        )
        c.onCharacter("h")
        c.onCharacter("i")
        c.abandon()

        assertEquals(listOf("h", "hi"), recording.composingUpdates)
        assertEquals(emptyList<String>(), recording.commits)
        assertEquals(1, recording.finishCount)
        assertEquals(0, commits)
        assertFalse(c.isComposing)
    }

    @Test
    fun onlyExplicitCommitReportsCommittedText() {
        var commits = 0
        val c = composer(onCommit = { _, _ -> commits++ })
        c.onCharacter("a")
        c.commit()
        assertEquals(1, commits)
        c.onCharacter("b")
        c.finish()
        c.onCharacter("c")
        c.abandon()
        assertEquals(1, commits)
    }

    @Test
    fun replacedInputConnectionAbandonsCompositionUntilNextSession() {
        val first = RecordingInputConnection()
        val second = RecordingInputConnection()
        var active = first.asInputConnection()
        val c = composer(inputConnection = { active })

        c.onCharacter("a")
        active = second.asInputConnection()
        c.onCharacter("b")
        c.commit()

        assertEquals(listOf("a"), first.composingUpdates)
        assertEquals(emptyList<String>(), second.composingUpdates)
        assertEquals(emptyList<String>(), second.commits)
        assertEquals(emptyList<String>(), first.commits)
        assertFalse(c.isComposing)
    }

    @Test
    fun backspacingTheLastCharacterClearsAndFinishesTheComposingRegion() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })

        c.onCharacter("x")
        assertTrue(c.onBackspace())

        assertEquals(listOf("x", ""), recording.composingUpdates)
        assertEquals(1, recording.finishCount)
        assertFalse(c.isComposing)
    }
}
