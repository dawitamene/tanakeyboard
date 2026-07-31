package com.addiyon.keyboard.testing

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates [FakeEditor] against documented `BaseInputConnection` behavior.
 *
 * A fake that models the document incorrectly is worse than no fake at all: it
 * would let the rewrite "pass" while still corrupting real fields. These tests
 * are the fake's own contract, pinned to the AOSP rules the production code
 * relies on.
 */
class FakeEditorTest {

    @Test
    fun commitTextInsertsAtTheCaretAndLeavesItAfterTheText() {
        val editor = FakeEditor("hello ")

        assertTrue(editor.commitText("world", 1))

        assertEquals("hello world", editor.text)
        assertEquals(11, editor.selectionStart)
        assertEquals(11, editor.selectionEnd)
        assertNull(editor.composingText)
    }

    @Test
    fun commitTextReplacesTheSelection() {
        val editor = FakeEditor("hello world")
        editor.setSelection(6, 11)

        editor.commitText("there", 1)

        assertEquals("hello there", editor.text)
        assertEquals(11, editor.selectionStart)
    }

    @Test
    fun setComposingTextOpensARegionAndSubsequentPushesReplaceIt() {
        val editor = FakeEditor("hi ")

        editor.setComposingText("c", 1)
        assertEquals("hi c", editor.text)
        assertEquals(3, editor.composingStart)
        assertEquals(4, editor.composingEnd)

        editor.setComposingText("co", 1)
        assertEquals("hi co", editor.text)
        assertEquals("co", editor.composingText)

        editor.setComposingText("cool", 1)
        assertEquals("hi cool", editor.text)
        assertEquals(3, editor.composingStart)
        assertEquals(7, editor.composingEnd)
        assertEquals(7, editor.selectionStart)
    }

    /**
     * The rule that makes a drifted composing region destructive: an insertion
     * replaces the WHOLE region, not just the caret position. A region that has
     * slid to cover "cool bree" swallows the space when the buffer "coolbree"
     * is pushed into it.
     */
    @Test
    fun aRegionSpanningTwoWordsIsReplacedWholesaleIncludingTheSpace() {
        val editor = FakeEditor("cool bree")
        editor.setComposingRegion(0, 9)

        editor.setComposingText("coolbree", 1)

        assertEquals("coolbree", editor.text)
    }

    @Test
    fun commitTextReplacesTheComposingRegionRatherThanAppending() {
        val editor = FakeEditor("hi ")
        editor.setComposingText("cool", 1)

        editor.commitText("cool ", 1)

        assertEquals("hi cool ", editor.text)
        assertNull(editor.composingText)
    }

    @Test
    fun emptyComposingTextClearsTheRegionAndTheText() {
        val editor = FakeEditor("hi ")
        editor.setComposingText("cool", 1)

        editor.setComposingText("", 1)

        assertEquals("hi ", editor.text)
        assertNull(editor.composingText)
        assertEquals(3, editor.selectionStart)
    }

    @Test
    fun finishComposingTextKeepsTheTextAndDropsOnlyTheRegion() {
        val editor = FakeEditor("hi ")
        editor.setComposingText("cool", 1)

        assertTrue(editor.finishComposingText())

        assertEquals("hi cool", editor.text)
        assertNull(editor.composingText)
        assertEquals(7, editor.selectionStart)
    }

    @Test
    fun deleteSurroundingTextRemovesOnEitherSideOfTheCaret() {
        val editor = FakeEditor("cool breeze")
        editor.setSelection(5, 5)

        editor.deleteSurroundingText(2, 3)

        assertEquals("coo" + "eze", editor.text)
        assertEquals(3, editor.selectionStart)
    }

    @Test
    fun deleteSurroundingTextClampsAtTheDocumentEdges() {
        val editor = FakeEditor("ab")
        editor.setSelection(1, 1)

        assertTrue(editor.deleteSurroundingText(9, 9))

        assertEquals("", editor.text)
        assertEquals(0, editor.selectionStart)
    }

    @Test
    fun deleteThenComposeReEstablishesAWordWithoutAbsoluteOffsets() {
        // The offset-free adoption sequence the rewrite depends on.
        val editor = FakeEditor("cool bree")

        editor.beginBatchEdit()
        editor.deleteSurroundingText(4, 0)
        editor.setComposingText("bree", 1)
        editor.endBatchEdit()

        assertEquals("cool bree", editor.text)
        assertEquals(5, editor.composingStart)
        assertEquals(9, editor.composingEnd)
        assertEquals(9, editor.selectionStart)
    }

    @Test
    fun readsAreServedFromTheDocument() {
        val editor = FakeEditor("cool breeze")
        editor.setSelection(4, 4)

        assertEquals("cool", editor.getTextBeforeCursor(10, 0).toString())
        assertEquals(" bree", editor.getTextAfterCursor(5, 0).toString())
        assertNull(editor.getSelectedText(0))

        editor.setSelection(0, 4)
        assertEquals("cool", editor.getSelectedText(0).toString())
    }

    @Test
    fun extractedTextCarriesRealOffsetsAndTheComposingSpan() {
        val editor = FakeEditor("hi ")
        editor.setComposingText("cool", 1)

        val extracted = editor.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(),
            InputConnection.GET_TEXT_WITH_STYLES
        )!!

        assertEquals("hi cool", extracted.text.toString())
        assertEquals(0, extracted.startOffset)
        assertEquals(7, extracted.selectionStart)
    }

    @Test
    fun partialExtractReportsAWindowWithANonZeroStartOffset() {
        val editor = FakeEditor(
            "x".repeat(60),
            personality = EditorPersonality.PARTIAL_EXTRACT,
            extractWindow = 8
        )
        editor.setSelection(40, 40)

        val extracted = editor.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(),
            0
        )!!

        assertEquals(32, extracted.startOffset)
        assertEquals(16, extracted.text.length)
        // Selection is reported RELATIVE to the window; reading startOffset as
        // anything else is exactly how absolute positions drift.
        assertEquals(8, extracted.selectionStart)
    }

    @Test
    fun editorsThatHideComposingSpansReportNoCandidatesAndNoStyledSpan() {
        val editor = FakeEditor("hi ", personality = EditorPersonality.COMPOSE_TEXT_FIELD)
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.setComposingText("cool", 1)
        editor.flush()

        // The region is genuinely applied...
        assertEquals("hi cool", editor.text)
        assertEquals("cool", editor.composingText)
        // ...but the editor never tells the IME where it is.
        assertEquals(-1, seen.single().candidatesStart)
        assertEquals(-1, seen.single().candidatesEnd)
    }

    @Test
    fun aospEditorReportsCandidatesForTheLiveRegion() {
        val editor = FakeEditor("hi ")
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.setComposingText("cool", 1)
        editor.flush()

        assertEquals(3, seen.single().candidatesStart)
        assertEquals(7, seen.single().candidatesEnd)
        assertEquals(7, seen.single().newSelStart)
    }

    @Test
    fun selectionUpdatesAreQueuedUntilFlushed() {
        val editor = FakeEditor("")
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.setComposingText("a", 1)
        editor.setComposingText("ab", 1)
        assertEquals(0, seen.size)
        assertEquals(2, editor.pendingUpdateCount)

        editor.flush()
        assertEquals(2, seen.size)
    }

    @Test
    fun coalescedDeliveryKeepsOnlyTheLastUpdate() {
        val editor = FakeEditor("", delivery = SelectionUpdateDelivery.COALESCED)
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.setComposingText("a", 1)
        editor.setComposingText("ab", 1)
        editor.setComposingText("abc", 1)
        editor.flush()

        assertEquals(1, seen.size)
        assertEquals(3, seen.single().newSelStart)
    }

    @Test
    fun droppedDeliveryNeverNotifiesTheIme() {
        val editor = FakeEditor("", delivery = SelectionUpdateDelivery.DROPPED)
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.setComposingText("abc", 1)
        editor.flush()

        assertEquals("abc", editor.text)
        assertEquals(0, seen.size)
    }

    @Test
    fun batchEndDeliveryReleasesOnlyWhenTheOutermostBatchCloses() {
        val editor = FakeEditor("", delivery = SelectionUpdateDelivery.AT_BATCH_END)
        val seen = mutableListOf<SelectionUpdate>()
        editor.onSelectionUpdate { seen += it }

        editor.beginBatchEdit()
        editor.beginBatchEdit()
        editor.setComposingText("ab", 1)
        editor.endBatchEdit()
        assertEquals(0, seen.size)

        editor.endBatchEdit()
        assertEquals(1, seen.size)
    }

    @Test
    fun rejectingEditorRefusesContentChangesButStillFinishesComposition() {
        val editor = FakeEditor("hi", personality = EditorPersonality.REJECTS_WRITES)

        assertFalse(editor.commitText("x", 1))
        assertFalse(editor.setComposingText("x", 1))
        assertEquals("hi", editor.text)
        assertTrue(editor.finishComposingText())
    }
}
