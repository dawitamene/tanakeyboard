package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import com.addiyon.keyboard.EditorGateway
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordComposerTest {

    private class RecordingInputConnection {
        val commits = mutableListOf<String>()
        val composingUpdates = mutableListOf<String>()
        val selections = mutableListOf<Pair<Int, Int>>()
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
                    "setSelection" -> {
                        selections +=
                            (args?.getOrNull(0) as? Int ?: -1) to
                                (args?.getOrNull(1) as? Int ?: -1)
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
    fun characterInMiddleUpdatesWholeBufferAndRestoresCaret() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume(prefix = "inform", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 6))

        c.onCharacter("h")

        assertEquals("inhform", c.raw)
        assertEquals("inhform", recording.composingUpdates.last())
        assertEquals(3 to 3, recording.selections.last())
    }

    @Test
    fun consecutiveCharactersInMiddleKeepUsingTheWholeBuffer() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume(prefix = "inform", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 6))

        c.onCharacter("h")
        assertTrue(c.moveCursor(cursorOffset = 3, composingStart = 0, composingEnd = 7))
        c.onCharacter("x")

        assertEquals("inhxform", c.raw)
        assertEquals("inhxform", recording.composingUpdates.last())
        assertEquals(4 to 4, recording.selections.last())
    }

    @Test
    fun temporaryEndCallbackDoesNotMovePendingInternalCaret() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume(prefix = "inform", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 6))
        c.onCharacter("h")

        assertTrue(c.moveCursor(cursorOffset = 7, composingStart = 0, composingEnd = 7))
        c.onCharacter("x")

        assertEquals("inhxform", c.raw)
    }

    @Test
    fun backspaceInMiddleUpdatesWholeBufferAndRestoresCaret() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume(prefix = "inform", composingStart = 4)
        assertTrue(c.moveCursor(cursorOffset = 3, composingStart = 4, composingEnd = 10))

        assertTrue(c.onBackspace())

        assertEquals("inorm", c.raw)
        assertEquals("inorm", recording.composingUpdates.last())
        assertEquals(6 to 6, recording.selections.last())
    }

    @Test
    fun commitAtCursorTransformsBothSidesAndRestoresCaretBetweenThem() {
        val recording = RecordingInputConnection()
        val committed = mutableListOf<Pair<String, String>>()
        val c = composer(
            inputConnection = { recording.asInputConnection() },
            commitTransform = String::uppercase,
            onCommit = { raw, display -> committed += raw to display }
        )
        c.resume(prefix = "inform", composingStart = 4)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 4, composingEnd = 10))

        assertTrue(c.commitAtCursor())

        assertEquals(listOf("INFORM"), recording.commits)
        assertEquals(6 to 6, recording.selections.last())
        assertEquals(listOf("in" to "IN", "form" to "FORM"), committed)
        assertFalse(c.isComposing)
    }

    @Test
    fun commitAtCursorDoesNothingWhenCaretIsAlreadyAtEnd() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume("inform")

        assertFalse(c.commitAtCursor())
        assertTrue(c.isComposing)
        assertTrue(recording.commits.isEmpty())
    }

    @Test
    fun resumeWithEmptyPrefixIsANoOp() {
        val c = composer()
        c.resume("")
        assertFalse(c.isComposing)
    }

    @Test
    fun adoptionSeedsAnExistingRegionWithoutWritingUntilTheFirstEdit() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })

        assertTrue(
            c.adopt(
                word = "inform",
                cursorOffset = 2,
                composingStart = 4,
                composingEnd = 10
            )
        )
        assertTrue(recording.composingUpdates.isEmpty())

        c.onCharacter("f")

        assertEquals("infform", recording.composingUpdates.single())
        assertEquals(7 to 7, recording.selections.single())
    }

    @Test
    fun ownedRegionCannotRebindToAnotherEqualLengthSpan() {
        val c = composer()
        assertTrue(
            c.adopt(
                word = "same",
                cursorOffset = 2,
                composingStart = 1,
                composingEnd = 5
            )
        )

        assertFalse(
            c.moveCursor(
                cursorOffset = 2,
                composingStart = 8,
                composingEnd = 12
            )
        )
        assertEquals("sa", c.textBeforeCursor())
    }

    @Test
    fun firstCharacterOwnsItsRegionBeforeTheFirstCursorCallback() {
        val recording = RecordingInputConnection()
        val input = recording.asInputConnection()
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 8, initialSelectionEnd = 8)
        val c = WordComposer(
            inputConnection = { input },
            editor = gateway
        )

        c.onCharacter("x", requireNotNull(gateway.currentToken()))

        assertEquals(8 to 9, c.ownedComposingRegion())
        assertFalse(c.moveCursor(cursorOffset = 1, composingStart = 1, composingEnd = 2))
        assertEquals(8 to 9, c.ownedComposingRegion())
        assertTrue(c.moveCursor(cursorOffset = 1, composingStart = 8, composingEnd = 9))
    }

    @Test
    fun pushComposingAdvancesGatewaySelectionWithoutFrameworkCallback() {
        val recording = RecordingInputConnection()
        val input = recording.asInputConnection()
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 0, initialSelectionEnd = 0)
        val c = WordComposer(
            inputConnection = { input },
            editor = gateway
        )

        c.onCharacter("i", requireNotNull(gateway.currentToken()))

        assertEquals(1, gateway.currentToken()?.selectionStart)
        assertEquals(1, gateway.currentToken()?.selectionEnd)

        c.onCharacter("n", requireNotNull(gateway.currentToken()))

        assertEquals(2, gateway.currentToken()?.selectionStart)
        assertEquals(2, gateway.currentToken()?.selectionEnd)
        assertEquals("in", c.raw)
    }

    @Test
    fun pushComposingAdvancesMidWordSelectionWithoutFrameworkCallback() {
        val recording = RecordingInputConnection()
        val input = recording.asInputConnection()
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 0, initialSelectionEnd = 0)
        val c = WordComposer(
            inputConnection = { input },
            editor = gateway
        )

        c.resume(prefix = "inorm", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 5))
        c.onCharacter("f", requireNotNull(gateway.currentToken()))

        assertEquals(3, gateway.currentToken()?.selectionStart)
        assertEquals(3, gateway.currentToken()?.selectionEnd)
        assertEquals("inform", c.raw)
    }

    @Test
    fun firstCharacterUsesTheLowerBoundOfASelectedRange() {
        val recording = RecordingInputConnection()
        val input = recording.asInputConnection()
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 10, initialSelectionEnd = 4)
        val c = WordComposer(
            inputConnection = { input },
            editor = gateway
        )

        c.onCharacter("x", requireNotNull(gateway.currentToken()))

        assertEquals(4 to 5, c.ownedComposingRegion())
    }

    @Test
    fun cursorCallbackCannotAssignAnUnknownRegion() {
        val c = composer()
        c.resume("word")

        assertNull(c.ownedComposingRegion())
        assertFalse(c.moveCursor(cursorOffset = 2, composingStart = 8, composingEnd = 12))
        assertNull(c.ownedComposingRegion())
    }

    @Test
    fun adoptRejectsInvalidStateAndRegionGeometry() {
        val active = composer()
        active.onCharacter("x")
        assertFalse(
            active.adopt(
                word = "word",
                cursorOffset = 2,
                composingStart = 0,
                composingEnd = 4
            )
        )
        assertFalse(
            composer().adopt(
                word = "",
                cursorOffset = 0,
                composingStart = 0,
                composingEnd = 0
            )
        )
        assertFalse(
            composer().adopt(
                word = "word",
                cursorOffset = -1,
                composingStart = 0,
                composingEnd = 4
            )
        )
        assertFalse(
            composer().adopt(
                word = "word",
                cursorOffset = 5,
                composingStart = 0,
                composingEnd = 4
            )
        )
        assertFalse(
            composer().adopt(
                word = "word",
                cursorOffset = 2,
                composingStart = -1,
                composingEnd = 3
            )
        )
        assertFalse(
            composer().adopt(
                word = "word",
                cursorOffset = 2,
                composingStart = 0,
                composingEnd = 3
            )
        )
    }

    @Test
    fun moveCursorRejectsInvalidStateAndRegionGeometry() {
        assertFalse(
            composer().moveCursor(
                cursorOffset = 0,
                composingStart = 0,
                composingEnd = 0
            )
        )

        val c = composer()
        c.resume(prefix = "word", composingStart = 0)
        assertFalse(c.moveCursor(cursorOffset = -1, composingStart = 0, composingEnd = 4))
        assertFalse(c.moveCursor(cursorOffset = 5, composingStart = 0, composingEnd = 4))
        assertFalse(c.moveCursor(cursorOffset = 2, composingStart = -1, composingEnd = 3))
        assertFalse(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 3))
    }

    @Test
    fun backspaceAtBeginningOfCompositionFallsThrough() {
        val c = composer()
        c.resume(prefix = "word", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 0, composingStart = 0, composingEnd = 4))

        assertFalse(c.onBackspace())
        assertEquals("word", c.raw)
    }

    @Test
    fun failedBackspaceAndCommitWritesClearWithoutReporting() {
        val backspaceConnection = RecordingInputConnection()
        var activeBackspaceConnection: InputConnection? = backspaceConnection.asInputConnection()
        val backspaceComposer = composer(inputConnection = { activeBackspaceConnection })
        backspaceComposer.onCharacter("a")
        backspaceComposer.onCharacter("b")
        activeBackspaceConnection = null

        assertTrue(backspaceComposer.onBackspace())
        assertFalse(backspaceComposer.isComposing)

        val commitConnection = RecordingInputConnection()
        var activeCommitConnection: InputConnection? = commitConnection.asInputConnection()
        val committed = mutableListOf<Pair<String, String>>()
        val commitComposer = composer(
            inputConnection = { activeCommitConnection },
            onCommit = { raw, display -> committed += raw to display }
        )
        commitComposer.onCharacter("a")
        activeCommitConnection = null

        assertFalse(commitComposer.commit())
        assertTrue(committed.isEmpty())
        assertFalse(commitComposer.isComposing)
    }

    @Test
    fun resumeWithKnownRegionOwnsItAndPushesAtEnd() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        assertNull(c.ownedComposingRegion())

        c.resume(
            prefix = "word",
            cursorOffset = 4,
            composingStart = 7
        )

        assertEquals(7 to 11, c.ownedComposingRegion())
        assertEquals(listOf("word"), recording.composingUpdates)
        assertTrue(recording.selections.isEmpty())
    }

    @Test
    fun pendingCaretMismatchBecomesTheAuthoritativeCursor() {
        val recording = RecordingInputConnection()
        val c = composer(inputConnection = { recording.asInputConnection() })
        c.resume(prefix = "inform", composingStart = 0)
        assertTrue(c.moveCursor(cursorOffset = 2, composingStart = 0, composingEnd = 6))
        c.onCharacter("h")

        assertTrue(c.moveCursor(cursorOffset = 4, composingStart = 0, composingEnd = 7))
        c.onCharacter("x")

        assertEquals("inhfxorm", c.raw)
    }

    @Test
    fun commitAtCursorRequiresAWordAndKnownRegion() {
        val empty = composer()
        assertFalse(empty.commitAtCursor())

        val unknownRegion = composer()
        unknownRegion.resume(prefix = "word", cursorOffset = 2)
        assertFalse(unknownRegion.commitAtCursor())
        assertTrue(unknownRegion.isComposing)
    }

    @Test
    fun commitAtBeginningTransformsAndReportsOnlyTheRightSide() {
        val recording = RecordingInputConnection()
        val committed = mutableListOf<Pair<String, String>>()
        val c = composer(
            inputConnection = { recording.asInputConnection() },
            commitTransform = String::uppercase,
            onCommit = { raw, display -> committed += raw to display }
        )
        assertTrue(
            c.adopt(
                word = "word",
                cursorOffset = 0,
                composingStart = 5,
                composingEnd = 9
            )
        )

        assertTrue(c.commitAtCursor())

        assertEquals(listOf("WORD"), recording.commits)
        assertEquals(listOf("word" to "WORD"), committed)
        assertEquals(5 to 5, recording.selections.single())
        assertFalse(c.isComposing)
    }

    @Test
    fun failedCommitAtCursorClearsWithoutReportingSegments() {
        val recording = RecordingInputConnection()
        var activeConnection: InputConnection? = recording.asInputConnection()
        val committed = mutableListOf<Pair<String, String>>()
        val c = composer(
            inputConnection = { activeConnection },
            onCommit = { raw, display -> committed += raw to display }
        )
        assertTrue(
            c.adopt(
                word = "word",
                cursorOffset = 2,
                composingStart = 5,
                composingEnd = 9
            )
        )
        activeConnection = null

        assertTrue(c.commitAtCursor())

        assertTrue(recording.commits.isEmpty())
        assertTrue(committed.isEmpty())
        assertFalse(c.isComposing)
    }

    @Test
    fun staleComposingRegionHistoryRetainsOnlyFourMostRecentRegions() {
        val c = composer()
        assertTrue(
            c.adopt(
                word = "word",
                cursorOffset = 2,
                composingStart = 10,
                composingEnd = 14
            )
        )

        listOf("a", "b", "c", "d", "e").forEach(c::onCharacter)

        assertFalse(c.isStaleComposingUpdate(10, 14))
        assertTrue(c.isStaleComposingUpdate(10, 15))
        assertTrue(c.isStaleComposingUpdate(10, 16))
        assertTrue(c.isStaleComposingUpdate(10, 17))
        assertTrue(c.isStaleComposingUpdate(10, 18))
    }

    @Test
    fun commitSuggestionAdvancesSelectionAndClearsBuffer() {
        val recording = RecordingInputConnection()
        val input = recording.asInputConnection()
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 4, initialSelectionEnd = 4)
        val c = WordComposer(
            inputConnection = { input },
            editor = gateway
        )
        c.resume(prefix = "infor", composingStart = 4)

        assertTrue(c.commitSuggestion("information", requireNotNull(gateway.currentToken())))

        assertEquals(listOf("information "), recording.commits)
        assertEquals(16, gateway.currentToken()?.selectionStart)
        assertEquals(16, gateway.currentToken()?.selectionEnd)
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
