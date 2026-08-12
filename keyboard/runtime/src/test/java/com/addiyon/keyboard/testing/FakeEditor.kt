package com.addiyon.keyboard.testing

import android.os.Bundle
import android.text.Spanned
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo

/**
 * A real text document behind an [InputConnection], for JVM tests.
 *
 * WHY THIS EXISTS
 *
 * The pre-existing JVM fakes (`WordComposerTest.RecordingInputConnection`,
 * `WordComposerCrashTest.RecordingInputConnection`) are `java.lang.reflect.Proxy`
 * CALL RECORDERS: every write returns true, every read returns null, and the
 * arguments are appended to lists. Tests could therefore only assert *which
 * InputConnection calls were made* -- never *what text the field ended up
 * holding*. That made an entire bug class structurally invisible on the JVM:
 * a composing region that has drifted away from where the editor actually put
 * it looks identical, in a call trace, to one that is perfectly in sync. Both
 * emit `setComposingText("coolbree")`. Only one of them eats the user's space.
 *
 * This class closes that gap. It maintains [text], [selectionStart]/[selectionEnd]
 * and [composingStart]/[composingEnd] and applies AOSP `BaseInputConnection`
 * semantics to every mutation, so a test can assert the thing that actually
 * matters:
 *
 *     assertEquals("cool breeze", editor.text)
 *
 * EDITOR PERSONALITIES
 *
 * Real editors differ in ways that change IME correctness, which is why the
 * reported bugs reproduce in some apps and not others. [personality] makes that
 * divergence a test parameter rather than a device-only surprise. See
 * [EditorPersonality].
 *
 * SELECTION CALLBACKS
 *
 * The platform delivers `onUpdateSelection` asynchronously, after the IME's key
 * handler has already returned. Emitting it synchronously inside a mutating call
 * would model a re-entrancy the real framework does not have and would hide
 * ordering bugs. So updates are QUEUED by default and released by [flush];
 * [SelectionUpdateDelivery] covers the hostile variants.
 */
class FakeEditor(
    initialText: String = "",
    initialSelection: Int = initialText.length,
    val personality: EditorPersonality = EditorPersonality.AOSP_EDIT_TEXT,
    val delivery: SelectionUpdateDelivery = SelectionUpdateDelivery.DEFERRED,
    /** Window size for [EditorPersonality.PARTIAL_EXTRACT]; ignored otherwise. */
    private val extractWindow: Int = 16
) : InputConnection {

    private val buffer = StringBuilder(initialText)
    private var selStart = initialSelection.coerceIn(0, initialText.length)
    private var selEnd = selStart
    private var compStart = NO_SPAN
    private var compEnd = NO_SPAN
    private var batchDepth = 0

    private val pending = ArrayDeque<SelectionUpdate>()
    private val listeners = mutableListOf<(SelectionUpdate) -> Unit>()

    /** Every call made, for the rare test that genuinely cares about the trace. */
    val calls = mutableListOf<String>()

    val text: String get() = buffer.toString()
    val selectionStart: Int get() = selStart
    val selectionEnd: Int get() = selEnd
    val composingStart: Int get() = compStart
    val composingEnd: Int get() = compEnd
    val composingText: String?
        get() = if (compStart < 0 || compEnd < compStart) null else buffer.substring(compStart, compEnd)

    /** Uncommitted `onUpdateSelection` callbacks waiting for [flush]. */
    val pendingUpdateCount: Int get() = pending.size

    fun onSelectionUpdate(listener: (SelectionUpdate) -> Unit) {
        listeners += listener
    }

    /**
     * Release queued `onUpdateSelection` callbacks to the listeners, the way the
     * platform's looper would once the IME's key handler returns.
     */
    fun flush() {
        if (delivery == SelectionUpdateDelivery.DROPPED) {
            pending.clear()
            return
        }
        val toDeliver = if (delivery == SelectionUpdateDelivery.COALESCED && pending.size > 1) {
            listOf(pending.last())
        } else {
            pending.toList()
        }
        pending.clear()
        toDeliver.forEach { update -> listeners.forEach { it(update) } }
    }

    // ---------------------------------------------------------------- writes

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        calls += "setComposingText(${text})"
        if (personality == EditorPersonality.REJECTS_WRITES) return false
        val incoming = text?.toString() ?: return false
        return mutate {
            val start = replaceTarget()
            replace(start.first, start.second, incoming)
            if (incoming.isEmpty()) {
                clearComposing()
            } else {
                compStart = start.first
                compEnd = start.first + incoming.length
            }
            placeCursor(start.first, incoming.length, newCursorPosition)
        }
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        calls += "commitText(${text})"
        if (personality == EditorPersonality.REJECTS_WRITES) return false
        val incoming = text?.toString() ?: return false
        return mutate {
            val start = replaceTarget()
            replace(start.first, start.second, incoming)
            clearComposing()
            placeCursor(start.first, incoming.length, newCursorPosition)
        }
    }

    override fun finishComposingText(): Boolean {
        calls += "finishComposingText()"
        // AOSP finishes the span even on editors that reject content mutations:
        // it changes no text. REJECTS_WRITES therefore does not veto it.
        return mutate { clearComposing() }
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        calls += "setComposingRegion($start,$end)"
        if (personality == EditorPersonality.REJECTS_WRITES) return false
        return mutate {
            val lo = minOf(start, end).coerceIn(0, buffer.length)
            val hi = maxOf(start, end).coerceIn(0, buffer.length)
            if (lo == hi) clearComposing() else { compStart = lo; compEnd = hi }
        }
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        calls += "deleteSurroundingText($beforeLength,$afterLength)"
        if (personality == EditorPersonality.REJECTS_WRITES) return false
        if (beforeLength < 0 || afterLength < 0) return false
        return mutate {
            // After-range first: deleting it cannot move the before-range.
            val afterStart = selEnd
            val afterStop = (selEnd + afterLength).coerceAtMost(buffer.length)
            if (afterStop > afterStart) deleteRange(afterStart, afterStop)
            val beforeStop = selStart
            val beforeStart = (selStart - beforeLength).coerceAtLeast(0)
            if (beforeStop > beforeStart) deleteRange(beforeStart, beforeStop)
        }
    }

    override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean =
        deleteSurroundingText(before, after)

    override fun setSelection(start: Int, end: Int): Boolean {
        calls += "setSelection($start,$end)"
        if (start !in 0..buffer.length || end !in 0..buffer.length) return false
        return mutate { selStart = start; selEnd = end }
    }

    override fun beginBatchEdit(): Boolean {
        calls += "beginBatchEdit()"
        batchDepth++
        return true
    }

    override fun endBatchEdit(): Boolean {
        calls += "endBatchEdit()"
        if (batchDepth == 0) return false
        batchDepth--
        if (batchDepth == 0 && delivery == SelectionUpdateDelivery.AT_BATCH_END) flush()
        return batchDepth > 0
    }

    // ----------------------------------------------------------------- reads

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        calls += "getTextBeforeCursor($n)"
        if (n < 0) return null
        val lo = minOf(selStart, selEnd)
        return buffer.substring((lo - n).coerceAtLeast(0), lo)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        calls += "getTextAfterCursor($n)"
        if (n < 0) return null
        val hi = maxOf(selStart, selEnd)
        return buffer.substring(hi, (hi + n).coerceAtMost(buffer.length))
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        calls += "getSelectedText()"
        val lo = minOf(selStart, selEnd)
        val hi = maxOf(selStart, selEnd)
        return if (lo == hi) null else buffer.substring(lo, hi)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        calls += "getExtractedText()"
        val windowStart: Int
        val windowEnd: Int
        if (
            personality == EditorPersonality.PARTIAL_EXTRACT ||
            personality == EditorPersonality.LYING_EXTRACT_OFFSET
        ) {
            // A real windowing editor hands back a slice around the caret and
            // reports where that slice starts. Getting this offset wrong by the
            // size of the before-window is precisely how absolute positions drift.
            windowStart = (minOf(selStart, selEnd) - extractWindow).coerceAtLeast(0)
            windowEnd = (maxOf(selStart, selEnd) + extractWindow).coerceAtMost(buffer.length)
        } else {
            windowStart = 0
            windowEnd = buffer.length
        }
        val slice = buffer.substring(windowStart, windowEnd)
        val withStyles = flags and InputConnection.GET_TEXT_WITH_STYLES != 0
        val body: CharSequence = if (withStyles && echoesComposingSpans() && compStart >= 0) {
            val localStart = (compStart - windowStart).coerceIn(0, slice.length)
            val localEnd = (compEnd - windowStart).coerceIn(localStart, slice.length)
            FakeSpanned(slice, localStart, localEnd)
        } else {
            slice
        }
        return allocateExtractedText().apply {
            this.text = body
            // A dishonest editor hands back a window but claims it starts at 0.
            // `SurroundingText.getOffset()` documents -1 for "unknown", but
            // getExtractedText has no such marker, so naive implementations just
            // report 0 -- and every absolute position the IME derives from it is
            // then short by the size of the before-window.
            this.startOffset =
                if (personality == EditorPersonality.LYING_EXTRACT_OFFSET) 0 else windowStart
            this.selectionStart = selStart - windowStart
            this.selectionEnd = selEnd - windowStart
            this.partialStartOffset = -1
            this.partialEndOffset = -1
            this.flags = 0
        }
    }

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    // ------------------------------------------------------- inert remainder

    override fun commitCompletion(completion: CompletionInfo?): Boolean = false
    override fun commitCorrection(correction: CorrectionInfo?): Boolean = false
    override fun performEditorAction(editorAction: Int): Boolean = true
    override fun performContextMenuAction(id: Int): Boolean = true
    override fun sendKeyEvent(event: KeyEvent?): Boolean = true
    override fun clearMetaKeyStates(states: Int): Boolean = true
    override fun reportFullscreenMode(enabled: Boolean): Boolean = true
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun commitContent(
        inputContentInfo: InputContentInfo,
        flags: Int,
        opts: Bundle?
    ): Boolean = false

    override fun getHandler() = null
    override fun closeConnection() = Unit

    // -------------------------------------------------------------- internals

    /**
     * Where the next insertion lands: the composing region if one is open,
     * otherwise the current selection. This is the AOSP rule, and it is what
     * makes a drifted composing region destructive rather than merely wrong --
     * `setComposingText` REPLACES this range wholesale.
     */
    private fun replaceTarget(): Pair<Int, Int> =
        if (compStart >= 0 && compEnd >= compStart) {
            compStart to compEnd
        } else {
            minOf(selStart, selEnd) to maxOf(selStart, selEnd)
        }

    private fun replace(start: Int, end: Int, incoming: String) {
        buffer.replace(start, end, incoming)
        val delta = incoming.length - (end - start)
        selStart = shift(selStart, start, end, delta)
        selEnd = shift(selEnd, start, end, delta)
        if (compStart >= 0) {
            compStart = shift(compStart, start, end, delta)
            compEnd = shift(compEnd, start, end, delta)
        }
    }

    private fun deleteRange(start: Int, end: Int) {
        buffer.delete(start, end)
        val delta = -(end - start)
        selStart = shift(selStart, start, end, delta)
        selEnd = shift(selEnd, start, end, delta)
        if (compStart >= 0) {
            compStart = shift(compStart, start, end, delta)
            compEnd = shift(compEnd, start, end, delta)
            if (compEnd <= compStart) clearComposing()
        }
    }

    private fun shift(position: Int, start: Int, end: Int, delta: Int): Int = when {
        position <= start -> position
        position >= end -> position + delta
        else -> start
    }

    private fun placeCursor(start: Int, insertedLength: Int, newCursorPosition: Int) {
        // InputConnection contract: > 0 counts from the END of the inserted text,
        // <= 0 counts from its START. 1 == "just after what I inserted".
        val target = if (newCursorPosition > 0) {
            start + insertedLength + (newCursorPosition - 1)
        } else {
            start + newCursorPosition
        }
        val clamped = target.coerceIn(0, buffer.length)
        selStart = clamped
        selEnd = clamped
    }

    private fun clearComposing() {
        compStart = NO_SPAN
        compEnd = NO_SPAN
    }

    private fun echoesComposingSpans(): Boolean = when (personality) {
        // Compose text fields, WebViews and cross-platform toolkits apply the
        // region but never echo SPAN_COMPOSING back to the IME.
        EditorPersonality.COMPOSE_TEXT_FIELD, EditorPersonality.WEBVIEW -> false
        else -> true
    }

    private fun reportsCandidates(): Boolean = echoesComposingSpans()

    private inline fun mutate(block: () -> Unit): Boolean {
        val oldStart = selStart
        val oldEnd = selEnd
        block()
        val update = SelectionUpdate(
            oldSelStart = oldStart,
            oldSelEnd = oldEnd,
            newSelStart = selStart,
            newSelEnd = selEnd,
            candidatesStart = if (reportsCandidates()) compStart else NO_SPAN,
            candidatesEnd = if (reportsCandidates()) compEnd else NO_SPAN
        )
        when (delivery) {
            SelectionUpdateDelivery.SYNCHRONOUS -> listeners.forEach { it(update) }
            SelectionUpdateDelivery.DROPPED -> Unit
            else -> pending.addLast(update)
        }
        return true
    }

    private companion object {
        const val NO_SPAN = -1

        /**
         * [ExtractedText] has no JVM-usable constructor in the mockable android.jar;
         * `Unsafe.allocateInstance` is the technique already used by EditorGatewayTest.
         */
        fun allocateExtractedText(): ExtractedText {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe")
                .apply { isAccessible = true }
                .get(null)
            return unsafeClass
                .getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, ExtractedText::class.java) as ExtractedText
        }
    }
}

/** One `onUpdateSelection` callback, with the framework's exact argument list. */
data class SelectionUpdate(
    val oldSelStart: Int,
    val oldSelEnd: Int,
    val newSelStart: Int,
    val newSelEnd: Int,
    val candidatesStart: Int,
    val candidatesEnd: Int
)

/**
 * The editor-capability axes that actually change IME correctness. Each reported
 * bug reproduces in some of these and not others; parameterising tests over them
 * is how per-app divergence stops being a device-only surprise.
 */
enum class EditorPersonality {
    /** Full support: real offsets, composing spans echoed, `candidates*` reported. */
    AOSP_EDIT_TEXT,

    /** Compose implements InputConnection directly: no span echo, `candidates* == -1`. */
    COMPOSE_TEXT_FIELD,

    /** Fake editable: no span echo, and callbacks arrive coalesced at endBatchEdit. */
    WEBVIEW,

    /** `getExtractedText` returns a window around the caret with an honest startOffset. */
    PARTIAL_EXTRACT,

    /**
     * `getExtractedText` returns a window but reports `startOffset = 0`. Common in
     * editors that implement extraction naively (line/paragraph scoped, or capped
     * for large documents). Any IME that trusts the reported offset as an absolute
     * document position writes to the wrong range here.
     */
    LYING_EXTRACT_OFFSET,

    /** Content mutations are refused; `finishComposingText` still works. */
    REJECTS_WRITES
}

/** When queued `onUpdateSelection` callbacks reach the IME. */
enum class SelectionUpdateDelivery {
    /** Queued; released by `flush()`, as the platform's looper would. The realistic default. */
    DEFERRED,

    /** Delivered inside the mutating call -- re-entrancy some local editors do produce. */
    SYNCHRONOUS,

    /** Queued, then released when the outermost batch edit ends. */
    AT_BATCH_END,

    /** Only the final update of a burst is ever delivered. */
    COALESCED,

    /** Never delivered: the IME must stay correct without any callback at all. */
    DROPPED
}

/**
 * Minimal [Spanned] carrying exactly one composing span.
 * `SpannableStringBuilder` is not mocked in the unit-test android.jar (its methods
 * throw), so styled reads need a hand-rolled implementation.
 */
private class FakeSpanned(
    private val body: String,
    private val spanStart: Int,
    private val spanEnd: Int
) : Spanned, CharSequence by body {

    private val tag = Any()

    override fun toString(): String = body

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
        val overlaps = spanEnd > start && spanStart < end
        val assignable = type == null || type.isAssignableFrom(tag.javaClass) ||
            type == Any::class.java
        return if (overlaps && assignable) {
            arrayOf(tag) as Array<T>
        } else {
            java.lang.reflect.Array.newInstance(type ?: Any::class.java, 0) as Array<T>
        }
    }

    override fun getSpanStart(what: Any?): Int = if (what === tag) spanStart else -1

    override fun getSpanEnd(what: Any?): Int = if (what === tag) spanEnd else -1

    override fun getSpanFlags(what: Any?): Int =
        if (what === tag) Spanned.SPAN_COMPOSING or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE else 0

    override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = when {
        start < spanStart && spanStart < limit -> spanStart
        start < spanEnd && spanEnd < limit -> spanEnd
        else -> limit
    }
}
