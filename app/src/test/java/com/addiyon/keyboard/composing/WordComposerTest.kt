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

        fun asInputConnection(): InputConnection =
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

    // No InputConnection is needed to exercise the composer's own state
    // machine -- inputConnection() is only ever used via a safe call
    // (`inputConnection()?.commitText(...)`), so returning null here just
    // skips the (untestable-without-Android) field-write side effect while
    // leaving buffer/raw/onCommit behavior fully exercisable.
    private fun composer(
        commitTransform: (String) -> String = { it },
        discardOnExit: Boolean = false,
        onCommit: (raw: String, display: String) -> Unit = { _, _ -> },
        inputConnection: () -> InputConnection? = { null }
    ) = WordComposer(
        inputConnection = inputConnection,
        commitTransform = commitTransform,
        discardOnExit = discardOnExit,
        onCommit = onCommit
    )

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
    fun finishReportsTheWordViaOnCommitWhenNotDiscarding() {
        var committed: Pair<String, String>? = null
        val c = composer(
            commitTransform = { it.uppercase() },
            onCommit = { raw, display -> committed = raw to display }
        )
        c.onCharacter("h")
        c.onCharacter("i")
        c.finish()
        assertEquals("hi" to "HI", committed)
        assertFalse(c.isComposing)
    }

    @Test
    fun abandonReportsTheWordViaOnCommitWhenNotDiscarding() {
        var committed: Pair<String, String>? = null
        val c = composer(onCommit = { raw, display -> committed = raw to display })
        c.onCharacter("h")
        c.onCharacter("i")
        c.abandon()
        assertEquals("hi" to "hi", committed)
        assertFalse(c.isComposing)
    }

    @Test
    fun abandonFinalizesNonDiscardingCompositionWithoutCommitText() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.onCharacter("h")
        c.onCharacter("i")
        c.abandon()

        assertEquals(listOf("h", "hi"), recording.composingUpdates)
        assertEquals(emptyList<String>(), recording.commits)
        assertEquals(1, recording.finishCount)
        assertFalse(c.isComposing)
    }

    @Test
    fun discardOnExitFinishAndAbandonDoNotReportAFreshWord() {
        var commits = 0
        val c = composer(discardOnExit = true, onCommit = { _, _ -> commits++ })
        c.onCharacter("h")
        c.finish()
        assertFalse(c.isComposing)
        c.onCharacter("h")
        c.abandon()
        assertFalse(c.isComposing)
        // The tentative word is deleted from the field, never committed --
        // so nothing must enter the resume history either.
        assertEquals(0, commits)
    }

    @Test
    fun discardOnExitRestoresAResumedWordThroughTheTransformInsteadOfDeletingIt() {
        var committed: Pair<String, String>? = null
        val c = composer(
            discardOnExit = true,
            commitTransform = { it.uppercase() },
            onCommit = { raw, display -> committed = raw to display }
        )
        // The word already existed as committed field text before adoption,
        // so exiting must re-report it (restored, with the extension, through
        // commitTransform -- never as raw Latin).
        c.resume("sel")
        c.onCharacter("a")
        c.abandon()
        assertEquals("sela" to "SELA", committed)

        committed = null
        c.resume("sel")
        c.finish()
        assertEquals("sel" to "SEL", committed)
    }

    @Test
    fun backspacingAResumedWordToEmptyClearsItsResumedStatus() {
        var commits = 0
        val c = composer(discardOnExit = true, onCommit = { _, _ -> commits++ })
        c.resume("ab")
        assertTrue(c.onBackspace())
        assertTrue(c.onBackspace())
        assertFalse(c.isComposing)
        // A NEW word typed afterwards is fresh, not "resumed": exiting
        // discards it without reporting.
        c.onCharacter("x")
        c.abandon()
        assertEquals(0, commits)
    }

    @Test
    fun commitClearsResumedStatusForTheNextWord() {
        var commits = 0
        val c = composer(discardOnExit = true, onCommit = { _, _ -> commits++ })
        c.resume("ab")
        c.commit()
        assertEquals(1, commits)
        c.onCharacter("x")
        c.finish()
        // Only the explicit commit reported; the fresh "x" was discarded.
        assertEquals(1, commits)
    }

    @Test
    fun inputConnectionLambdaIsReadForEachOperation() {
        val first = RecordingInputConnection()
        val second = RecordingInputConnection()
        var active = first.asInputConnection()
        val c = composer(inputConnection = { active })

        c.onCharacter("a")
        active = second.asInputConnection()
        c.onCharacter("b")
        c.commit()

        assertEquals(listOf("a"), first.composingUpdates)
        assertEquals(listOf("ab"), second.composingUpdates)
        assertEquals(listOf("ab"), second.commits)
        assertEquals(emptyList<String>(), first.commits)
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
