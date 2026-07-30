package com.addiyon.keyboard

import android.view.inputmethod.ExtractedText
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
    fun rejectedComposingRegionKeepsCurrentSessionAvailableForFallbackWrite() {
        val commits = AtomicInteger()
        val input = connection { method ->
            when (method.name) {
                "setComposingRegion" -> false
                "commitText" -> {
                    commits.incrementAndGet()
                    true
                }
                else -> defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 4, initialSelectionEnd = 4)
        val token = requireNotNull(gateway.currentToken())

        assertFalse(gateway.setComposingRegion(0, 4, token))
        assertTrue(gateway.isCurrent(token))
        assertTrue(gateway.commitText("x", token))
        assertEquals(1, commits.get())
    }

    @Test
    fun throwingComposingRegionKeepsCurrentSessionAvailableForFallbackDelete() {
        val deletes = AtomicInteger()
        val input = connection { method ->
            when (method.name) {
                "setComposingRegion" -> throw RuntimeException("region rejected")
                "deleteSurroundingText" -> {
                    deletes.incrementAndGet()
                    true
                }
                else -> defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 4, initialSelectionEnd = 4)
        val token = requireNotNull(gateway.currentToken())

        assertFalse(gateway.setComposingRegion(0, 4, token))
        assertTrue(gateway.isCurrent(token))
        assertTrue(gateway.deleteBeforeCursor(1, token))
        assertEquals(1, deletes.get())
    }

    @Test
    fun composingRegionFailureStillInvalidatesASwappedConnection() {
        lateinit var second: InputConnection
        var current: InputConnection? = null
        val first = connection { method ->
            if (method.name == "setComposingRegion") {
                current = second
                false
            } else {
                defaultValue(method.returnType)
            }
        }
        second = connection { method ->
            if (method.name == "commitText") true else defaultValue(method.returnType)
        }
        current = first
        val gateway = EditorGateway { current }
        gateway.beginSession(initialSelectionStart = 4, initialSelectionEnd = 4)

        assertFalse(
            gateway.setComposingRegion(
                start = 0,
                end = 4,
                token = requireNotNull(gateway.currentToken())
            )
        )
        assertNull(gateway.currentToken())
        assertFalse(gateway.commitText("x"))
    }

    @Test
    fun selectionRevalidationReadsTheEditorsActualSelection() {
        var actualSelection = 2
        val reads = AtomicInteger()
        val input = connection { method ->
            if (method.name == "getExtractedText") {
                reads.incrementAndGet()
                extractedText(
                    text = "field",
                    selectionStart = actualSelection,
                    selectionEnd = actualSelection
                )
            } else {
                defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 2, initialSelectionEnd = 2)
        val token = requireNotNull(gateway.currentToken())

        assertTrue(gateway.revalidateSelection(token))
        actualSelection = 4
        assertFalse(gateway.revalidateSelection(token))
        assertEquals(2, reads.get())
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

    @Test
    fun selectionChangeRejectsAWriteScopedToThePriorSelection() {
        val writes = AtomicInteger()
        val input = connection { method ->
            if (method.name == "commitText") {
                writes.incrementAndGet()
                true
            } else {
                defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 2, initialSelectionEnd = 2)
        val stale = requireNotNull(gateway.currentToken())

        gateway.noteSelection(5, 5)

        assertFalse(gateway.commitText("stale", stale))
        assertEquals(0, writes.get())
        assertTrue(gateway.commitText("current", gateway.currentToken()))
        assertEquals(1, writes.get())
    }

    @Test
    fun rangeReplacementAcceptsItsExpectedSynchronousSelectionCallback() {
        lateinit var gateway: EditorGateway
        val commits = AtomicInteger()
        val batchEnds = AtomicInteger()
        val input = connection { method ->
            when (method.name) {
                "beginBatchEdit" -> true
                "setSelection" -> {
                    gateway.noteSelection(1, 4)
                    true
                }
                "commitText" -> {
                    commits.incrementAndGet()
                    true
                }
                "endBatchEdit" -> {
                    batchEnds.incrementAndGet()
                    true
                }
                else -> defaultValue(method.returnType)
            }
        }
        gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 2, initialSelectionEnd = 2)

        assertTrue(
            gateway.replaceRange(
                start = 1,
                end = 4,
                text = "word",
                token = requireNotNull(gateway.currentToken())
            )
        )
        assertEquals(1, commits.get())
        assertEquals(1, batchEnds.get())
    }

    @Test
    fun rangeReplacementAbortsAfterAnUnexpectedSynchronousSelectionCallback() {
        lateinit var gateway: EditorGateway
        val commits = AtomicInteger()
        val batchEnds = AtomicInteger()
        val input = connection { method ->
            when (method.name) {
                "beginBatchEdit" -> true
                "setSelection" -> {
                    gateway.noteSelection(8, 8)
                    true
                }
                "commitText" -> {
                    commits.incrementAndGet()
                    true
                }
                "endBatchEdit" -> {
                    batchEnds.incrementAndGet()
                    true
                }
                else -> defaultValue(method.returnType)
            }
        }
        gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 2, initialSelectionEnd = 2)

        assertFalse(
            gateway.replaceRange(
                start = 1,
                end = 4,
                text = "word",
                token = requireNotNull(gateway.currentToken())
            )
        )
        assertEquals(0, commits.get())
        assertEquals(1, batchEnds.get())
    }

    @Test
    fun acceptedReplacementAdvancesSelectionWithoutAFrameworkCallback() {
        val input = connection { method -> defaultValue(method.returnType) }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 5, initialSelectionEnd = 5)
        val source = requireNotNull(gateway.currentToken())

        val post = requireNotNull(
            gateway.transitionAfterAcceptedReplacement(
                sourceToken = source,
                expectedSelection = 6
            )
        )

        assertEquals(6, post.selectionStart)
        assertEquals(6, post.selectionEnd)
        assertEquals(source.selectionGeneration + 1, post.selectionGeneration)
        assertFalse(gateway.isCurrent(source))
        assertTrue(gateway.isCurrent(post))
    }

    @Test
    fun acceptedRangeReplacementConsumesItsIntermediateSelection() {
        val input = connection { method -> defaultValue(method.returnType) }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 7, initialSelectionEnd = 7)
        val source = requireNotNull(gateway.currentToken())
        gateway.noteSelection(4, 10)

        val post = requireNotNull(
            gateway.transitionAfterAcceptedReplacement(
                sourceToken = source,
                expectedSelection = 16,
                allowedIntermediateSelectionStart = 4,
                allowedIntermediateSelectionEnd = 10
            )
        )

        assertEquals(16, post.selectionStart)
        assertEquals(16, post.selectionEnd)
        assertTrue(gateway.isCurrent(post))
    }

    @Test
    fun replacementIdentityRejectsASilentSameCaretHostRewrite() {
        var document = "helloworld"
        var actualSelection = 5
        val input = connection { method ->
            if (method.name == "getExtractedText") {
                extractedText(
                    text = document,
                    selectionStart = actualSelection,
                    selectionEnd = actualSelection
                )
            } else {
                defaultValue(method.returnType)
            }
        }
        val gateway = EditorGateway { input }
        gateway.beginSession(initialSelectionStart = 5, initialSelectionEnd = 5)
        val snapshot = requireNotNull(
            gateway.replacementSnapshot(
                replacementStart = 5,
                replacementEnd = 5,
                beforeChars = 256,
                afterChars = 128
            )
        )
        val identity = requireNotNull(
            snapshot.identityAfter(
                replacement = " ",
                beforeChars = 256,
                afterChars = 128
            )
        )
        val post = requireNotNull(
            gateway.transitionAfterAcceptedReplacement(
                sourceToken = snapshot.token,
                expectedSelection = 6
            )
        )

        document = "hello world"
        actualSelection = 6
        assertTrue(gateway.contentIdentityMatches(identity, post))

        document = "abide world"
        assertTrue(gateway.isCurrent(post))
        assertFalse(gateway.contentIdentityMatches(identity, post))
    }

    private fun connection(handler: (java.lang.reflect.Method) -> Any?): InputConnection =
        Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, _ ->
            if (method.name == "closeConnection") Unit else handler(method)
        } as InputConnection

    /**
     * Regression: an editor that never echoes its composing spans back reported
     * null bounds, and the composer's own verification read that as "the field
     * disagrees with me" -- so every suggestion-chip tap was rejected and
     * silently did nothing. Unknown bounds must not count as a contradiction.
     */
    @Test
    fun absentComposingBoundsDoNotContradictTheRegionWeSet() {
        val noBounds = surroundingText(
            text = "info",
            offset = 6,
            selectionStart = 4,
            selectionEnd = 4,
            composingStart = null,
            composingEnd = null
        )

        assertFalse(noBounds.composingRegionContradicts(start = 6, end = 10))
        // A half-reported region is still only half-known: the end alone
        // agreeing is enough not to veto.
        assertFalse(
            surroundingText(
                text = "info",
                offset = 6,
                selectionStart = 4,
                selectionEnd = 4,
                composingStart = null,
                composingEnd = 10
            ).composingRegionContradicts(start = 6, end = 10)
        )
    }

    /**
     * Regression: an editor whose getSurroundingText reply carries the
     * documented "offset unknown" marker (-1) -- which is what the framework's
     * DEFAULT implementation always reports, so every Compose text field does
     * -- must not have that marker read back as a real document position. Doing
     * so made every derived absolute position short by the before-window, the
     * gateway rejected each read against the tracked caret, and the composer
     * gave up on the word mid-typing: "inf" started suggesting words beginning
     * with "f". Null here means "ask getExtractedText instead", which reports a
     * real startOffset.
     */
    @Test
    fun unknownWindowOffsetIsNotReadAsADocumentPosition() {
        assertNull(
            surroundingTextFromDirectRead(
                text = "inf",
                offset = -1,
                selectionStart = 3,
                selectionEnd = 3,
                composingBounds = 0 to 3
            )
        )

        val placed = surroundingTextFromDirectRead(
            text = "inf",
            offset = 6,
            selectionStart = 3,
            selectionEnd = 3,
            composingBounds = 0 to 3
        )
        assertEquals(9, placed?.absoluteSelectionStart)
        assertEquals(9, placed?.absoluteSelectionEnd)
        assertEquals(6, placed?.composingStart)
        assertEquals(9, placed?.composingEnd)
        assertEquals("inf", placed?.textBeforeSelection)
    }

    @Test
    fun outOfRangeSelectionInADirectReadIsRejected() {
        assertNull(
            surroundingTextFromDirectRead(
                text = "inf",
                offset = 6,
                selectionStart = 4,
                selectionEnd = 4,
                composingBounds = null
            )
        )
    }

    @Test
    fun reportedComposingBoundsThatDisagreeStillContradict() {
        val shifted = surroundingText(
            text = "info",
            offset = 6,
            selectionStart = 4,
            selectionEnd = 4,
            composingStart = 5,
            composingEnd = 10
        )
        val shortEnd = surroundingText(
            text = "info",
            offset = 6,
            selectionStart = 4,
            selectionEnd = 4,
            composingStart = 6,
            composingEnd = 9
        )
        val agreeing = surroundingText(
            text = "info",
            offset = 6,
            selectionStart = 4,
            selectionEnd = 4,
            composingStart = 6,
            composingEnd = 10
        )

        assertTrue(shifted.composingRegionContradicts(start = 6, end = 10))
        assertTrue(shortEnd.composingRegionContradicts(start = 6, end = 10))
        assertFalse(agreeing.composingRegionContradicts(start = 6, end = 10))
    }

    private fun surroundingText(
        text: String,
        offset: Int,
        selectionStart: Int,
        selectionEnd: Int,
        composingStart: Int?,
        composingEnd: Int?
    ) = EditorSurroundingText(
        text = text,
        offset = offset,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        composingStart = composingStart,
        composingEnd = composingEnd
    )

    private fun extractedText(
        text: String,
        selectionStart: Int,
        selectionEnd: Int
    ): ExtractedText {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeField.get(null)
        val extracted = unsafeClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ExtractedText::class.java) as ExtractedText
        extracted.text = text
        extracted.startOffset = 0
        extracted.selectionStart = selectionStart
        extracted.selectionEnd = selectionEnd
        return extracted
    }

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
