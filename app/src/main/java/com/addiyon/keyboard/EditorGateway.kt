package com.addiyon.keyboard

import com.addiyon.keyboard.telemetry.NonFatalCategory
import android.os.Build
import android.text.Spanned
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.annotation.RequiresApi

internal data class EditorToken(
    val generation: Long,
    val selectionGeneration: Long,
    val selectionStart: Int,
    val selectionEnd: Int,
    val connection: InputConnection
) {
    fun sameEditorState(other: EditorToken): Boolean =
        generation == other.generation &&
            selectionGeneration == other.selectionGeneration &&
            selectionStart == other.selectionStart &&
            selectionEnd == other.selectionEnd &&
            connection === other.connection
}

internal data class EditorRead<out T>(
    val value: T,
    val token: EditorToken
)

internal data class EditorSurroundingText(
    val text: String,
    val offset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val composingStart: Int? = null,
    val composingEnd: Int? = null
) {
    val absoluteSelectionStart: Int
        get() = offset + selectionStart

    val absoluteSelectionEnd: Int
        get() = offset + selectionEnd

    val textBeforeSelection: String
        get() = text.substring(0, minOf(selectionStart, selectionEnd))

    val selectedText: String
        get() = text.substring(
            minOf(selectionStart, selectionEnd),
            maxOf(selectionStart, selectionEnd)
        )

    val textAfterSelection: String
        get() = text.substring(maxOf(selectionStart, selectionEnd))

    /**
     * True only when the editor reported a composing region that actually
     * DISAGREES with [start]..[end].
     *
     * [composingStart]/[composingEnd] are null whenever the editor didn't
     * surface composing spans at all -- `getSurroundingText` handed back plain
     * (non-[android.text.Spanned]) text, or styled text whose SPAN_COMPOSING
     * markers didn't survive being windowed. That is "unknown", not "wrong":
     * plenty of real editors (WebViews, some Compose text fields, cross-platform
     * toolkits) never echo the region back even though they applied it.
     *
     * So this is the check to use when verifying a region THIS IME set and is
     * therefore already the authority on. Treating null as a mismatch there
     * makes every such editor look permanently desynced -- which silently broke
     * suggestion-chip taps. Use [EditorGateway.composingRegionMatches] instead
     * when you need the editor to positively confirm a region.
     */
    fun composingRegionContradicts(start: Int, end: Int): Boolean =
        (composingStart != null && composingStart != start) ||
            (composingEnd != null && composingEnd != end)
}

internal data class EditorContentIdentity(
    val selectionStart: Int,
    val selectionEnd: Int,
    val textBeforeSelection: String,
    val textAfterSelection: String
) {
    fun matches(surrounding: EditorSurroundingText): Boolean =
        surrounding.absoluteSelectionStart == selectionStart &&
            surrounding.absoluteSelectionEnd == selectionEnd &&
            surrounding.selectedText.isEmpty() &&
            surrounding.textBeforeSelection == textBeforeSelection &&
            surrounding.textAfterSelection == textAfterSelection
}

internal data class EditorReplacementSnapshot(
    val token: EditorToken,
    val surrounding: EditorSurroundingText,
    val replacementStart: Int,
    val replacementEnd: Int
) {
    fun identityAfter(
        replacement: CharSequence,
        beforeChars: Int,
        afterChars: Int
    ): EditorContentIdentity? {
        if (beforeChars < 0 || afterChars < 0) return null
        val localStart = replacementStart - surrounding.offset
        val localEnd = replacementEnd - surrounding.offset
        if (
            localStart !in 0..surrounding.text.length ||
            localEnd !in localStart..surrounding.text.length
        ) {
            return null
        }
        val updated = buildString(
            surrounding.text.length - (localEnd - localStart) + replacement.length
        ) {
            append(surrounding.text, 0, localStart)
            append(replacement)
            append(surrounding.text, localEnd, surrounding.text.length)
        }
        val selection = replacementStart + replacement.length
        val localSelection = localStart + replacement.length
        return EditorContentIdentity(
            selectionStart = selection,
            selectionEnd = selection,
            textBeforeSelection = updated
                .substring(0, localSelection)
                .takeLast(beforeChars),
            textAfterSelection = updated
                .substring(localSelection)
                .take(afterChars)
        )
    }
}

internal class EditorGateway(
    private val clockNanos: () -> Long = System::nanoTime,
    private val slowOptionalReadNanos: Long = 20_000_000L,
    private val connectionProvider: () -> InputConnection?
) {
    private var generation = 0L
    private var selectionGeneration = 0L
    private var selectionStart = UNKNOWN_SELECTION
    private var selectionEnd = UNKNOWN_SELECTION
    private var sessionConnection: InputConnection? = null
    private var optionalReadsEnabled = true
    private var sessionActive = true
    private var directSurroundingTextUsable = true

    val sessionGeneration: Long
        get() = generation

    val allowsOptionalReads: Boolean
        get() = optionalReadsEnabled

    fun beginSession(
        initialSelectionStart: Int = UNKNOWN_SELECTION,
        initialSelectionEnd: Int = UNKNOWN_SELECTION
    ): Long {
        generation += 1
        selectionGeneration = 0L
        selectionStart = initialSelectionStart
        selectionEnd = initialSelectionEnd
        sessionActive = true
        sessionConnection = providedConnection()
        optionalReadsEnabled = true
        directSurroundingTextUsable = true
        return generation
    }

    /**
     * Record where the platform says the selection now is.
     *
     * This bumps [selectionGeneration] on EVERY callback, including ones
     * reporting the position we already hold. That is deliberate: the counter is
     * not "the caret moved", it is "the platform told us something happened",
     * and onUpdateSelection is also how text changes are announced -- an edit
     * that leaves the caret on the same offset is still an edit. Suppressing the
     * bump for same-position callbacks lets tokens taken before such an edit go
     * on looking current, which desyncs the composer's view of the word being
     * typed. Callers that specifically need "is the caret still where it was"
     * should verify it against the editor, as [revalidateSelection] does.
     */
    fun noteSelection(start: Int, end: Int): Long {
        selectionGeneration += 1
        selectionStart = start
        selectionEnd = end
        return selectionGeneration
    }

    fun endSession() {
        invalidateSession()
    }

    private fun invalidateSession() {
        generation += 1
        selectionGeneration += 1
        selectionStart = UNKNOWN_SELECTION
        selectionEnd = UNKNOWN_SELECTION
        sessionConnection = null
        optionalReadsEnabled = false
        sessionActive = false
    }

    fun currentToken(): EditorToken? =
        currentConnection()?.let {
            EditorToken(
                generation = generation,
                selectionGeneration = selectionGeneration,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                connection = it
            )
        }

    fun isCurrent(token: EditorToken): Boolean =
        token.generation == generation &&
            token.selectionGeneration == selectionGeneration &&
            token.selectionStart == selectionStart &&
            token.selectionEnd == selectionEnd &&
            currentConnection() === token.connection

    fun isCurrentSession(token: EditorToken): Boolean =
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
            SafeLog.e(oom, "Editor read OOM", NonFatalCategory.EDITOR)
            null
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor read", NonFatalCategory.EDITOR)
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
    ): Boolean = writeInternal(
        token = token,
        invalidateOnRejectedOperation = true,
        operation = operation
    )

    private fun writeInternal(
        token: EditorToken?,
        invalidateOnRejectedOperation: Boolean,
        operation: (InputConnection) -> Boolean
    ): Boolean {
        val current = currentConnection() ?: return false
        if (token != null && !isCurrent(token)) {
            return false
        }
        val accepted = try {
            operation(current)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "Editor write OOM", NonFatalCategory.EDITOR)
            false
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor write", NonFatalCategory.EDITOR)
            false
        }
        val stillCurrent = providedConnection() === current
        if (!stillCurrent || (!accepted && invalidateOnRejectedOperation)) {
            invalidateSession()
        }
        return accepted && stillCurrent
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

    fun surroundingText(
        beforeChars: Int,
        afterChars: Int,
        optional: Boolean = true
    ): EditorRead<EditorSurroundingText>? {
        val before = beforeChars.coerceIn(0, MAX_READ_CHARS)
        val after = afterChars.coerceIn(0, MAX_READ_CHARS)
        val result = read(optional) { connection ->
            val direct = if (
                directSurroundingTextUsable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                directSurroundingText(connection, before, after)
            } else {
                null
            }
            direct ?: run {
                connection.getExtractedText(
                    ExtractedTextRequest(),
                    InputConnection.GET_TEXT_WITH_STYLES
                )?.toBoundedSurroundingText(before, after)
            }
        } ?: return null
        val token = result.token
        val value = result.value
        if (
            token.selectionStart >= 0 &&
            (
                token.selectionStart != value.absoluteSelectionStart ||
                    token.selectionEnd != value.absoluteSelectionEnd
                )
        ) {
            return null
        }
        return result
    }

    /**
     * Is the caret still exactly where [token] recorded it, in the same session?
     *
     * Answered by ASKING THE EDITOR, not by comparing [selectionGeneration].
     * That counter advances on every onUpdateSelection callback, including the
     * redundant ones the platform emits routinely (after a commit, on focus
     * churn, on its own schedule). Requiring it to match made a token go stale
     * while the caret had not actually moved, which silently rejected suggestion
     * chip taps -- the tapped word simply never replaced what was typed.
     *
     * Reading the real position is strictly better evidence than the counter,
     * and this stays narrow: the counter still guards every other token use, so
     * nothing else gets to treat a stale token as fresh.
     */
    fun revalidateSelection(token: EditorToken): Boolean {
        if (
            token.selectionStart < 0 ||
            token.selectionEnd < 0 ||
            !isCurrentSession(token)
        ) {
            return false
        }
        val result = surroundingText(
            beforeChars = 0,
            afterChars = 0,
            optional = false
        ) ?: return false
        return result.value.absoluteSelectionStart == token.selectionStart &&
            result.value.absoluteSelectionEnd == token.selectionEnd
    }

    fun replacementSnapshot(
        replacementStart: Int,
        replacementEnd: Int,
        beforeChars: Int,
        afterChars: Int
    ): EditorReplacementSnapshot? {
        if (
            replacementStart < 0 ||
            replacementEnd < replacementStart ||
            beforeChars < 0 ||
            afterChars < 0
        ) {
            return null
        }
        val token = currentToken() ?: return null
        val selectionStart = minOf(token.selectionStart, token.selectionEnd)
        val selectionEnd = maxOf(token.selectionStart, token.selectionEnd)
        if (selectionStart < 0 || selectionEnd < 0) return null
        if (replacementStart > selectionStart || replacementEnd < selectionEnd) return null
        val replacedBeforeSelection = selectionStart - replacementStart
        val replacedAfterSelection = replacementEnd - selectionEnd
        if (
            replacedBeforeSelection > MAX_READ_CHARS - beforeChars ||
            replacedAfterSelection > MAX_READ_CHARS - afterChars
        ) {
            return null
        }
        val requiredBefore = beforeChars + replacedBeforeSelection
        val requiredAfter = afterChars + replacedAfterSelection
        val read = surroundingText(
            beforeChars = requiredBefore,
            afterChars = requiredAfter,
            optional = true
        ) ?: return null
        if (!read.token.sameEditorState(token)) return null
        val surrounding = read.value
        if (
            replacementStart < surrounding.offset ||
            replacementEnd > surrounding.offset + surrounding.text.length
        ) {
            return null
        }
        return EditorReplacementSnapshot(
            token = read.token,
            surrounding = surrounding,
            replacementStart = replacementStart,
            replacementEnd = replacementEnd
        )
    }

    fun transitionAfterAcceptedReplacement(
        sourceToken: EditorToken,
        expectedSelection: Int,
        allowedIntermediateSelectionStart: Int? = null,
        allowedIntermediateSelectionEnd: Int? = null
    ): EditorToken? {
        if (expectedSelection < 0 || !isCurrentSession(sourceToken)) return null
        val current = currentToken() ?: return null
        if (
            current.sameEditorState(sourceToken) ||
            (
                allowedIntermediateSelectionStart != null &&
                    allowedIntermediateSelectionEnd != null &&
                    current.selectionStart == allowedIntermediateSelectionStart &&
                    current.selectionEnd == allowedIntermediateSelectionEnd
                )
        ) {
            noteSelection(expectedSelection, expectedSelection)
            return currentToken()
        }
        return current.takeIf {
            it.selectionStart == expectedSelection &&
                it.selectionEnd == expectedSelection
        }
    }

    fun contentIdentityMatches(
        identity: EditorContentIdentity,
        token: EditorToken
    ): Boolean {
        if (!isCurrent(token)) return false
        val read = surroundingText(
            beforeChars = identity.textBeforeSelection.length,
            afterChars = identity.textAfterSelection.length,
            optional = false
        ) ?: return false
        return read.token.sameEditorState(token) && identity.matches(read.value)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun directSurroundingText(
        connection: InputConnection,
        before: Int,
        after: Int
    ): EditorSurroundingText? = try {
        val direct = connection.getSurroundingText(
            before,
            after,
            InputConnection.GET_TEXT_WITH_STYLES
        ) ?: return null
        if (direct.offset < 0) {
            // This editor can't say where its window sits, and it won't start
            // being able to mid-session, so stop paying for the round trip.
            directSurroundingTextUsable = false
        }
        val bounds = (direct.text as? Spanned)?.composingBounds()
        surroundingTextFromDirectRead(
            text = direct.text.toString(),
            offset = direct.offset,
            selectionStart = direct.selectionStart,
            selectionEnd = direct.selectionEnd,
            composingBounds = bounds
        )
    } catch (_: Throwable) {
        null
    }

    /**
     * Confirms the editor reports the expected composing region. Unknown bounds
     * (null) are accepted as a match, because many editors -- Compose
     * TextField, WebViews, cross-platform toolkits -- apply the region but never
     * echo SPAN_COMPOSING back. Bounds that *are* reported must still agree.
     */
    fun composingRegionMatches(
        start: Int,
        end: Int,
        selection: Int,
        token: EditorToken
    ): Boolean {
        if (
            start < 0 ||
            end < start ||
            selection !in start..end ||
            !isCurrent(token)
        ) {
            return false
        }
        val result = surroundingText(
            beforeChars = selection - start,
            afterChars = end - selection,
            optional = false
        ) ?: return false
        val composingStartMatches = result.value.composingStart == null ||
            result.value.composingStart == start
        val composingEndMatches = result.value.composingEnd == null ||
            result.value.composingEnd == end
        return result.token.sameEditorState(token) &&
            result.value.absoluteSelectionStart == selection &&
            result.value.absoluteSelectionEnd == selection &&
            composingStartMatches &&
            composingEndMatches
    }

    fun setComposingText(text: CharSequence, token: EditorToken? = null): Boolean =
        write(token) { it.setComposingText(text, 1) }

    fun setComposingTextAndSelection(
        text: CharSequence,
        selection: Int,
        expectedIntermediateSelection: Int,
        token: EditorToken? = null
    ): Boolean {
        if (selection < 0 || expectedIntermediateSelection < 0) return false
        return write(token) { connection ->
            connection.beginBatchEdit()
            try {
                if (!connection.setComposingText(text, 1)) {
                    false
                } else if (
                    token != null &&
                    !selectionAllowsExpectedTransition(token, expectedIntermediateSelection)
                ) {
                    false
                } else {
                    connection.setSelection(selection, selection)
                }
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
        expectedIntermediateSelection: Int,
        token: EditorToken? = null
    ): Boolean {
        if (selection < 0 || expectedIntermediateSelection < 0) return false
        return write(token) { connection ->
            connection.beginBatchEdit()
            try {
                if (!connection.commitText(text, 1)) {
                    false
                } else if (
                    token != null &&
                    !selectionAllowsExpectedTransition(token, expectedIntermediateSelection)
                ) {
                    false
                } else {
                    connection.setSelection(selection, selection)
                }
            } finally {
                connection.endBatchEdit()
            }
        }
    }

    fun deleteBeforeCursor(chars: Int, token: EditorToken? = null): Boolean =
        write(token) { it.deleteSurroundingText(chars.coerceAtLeast(1), 0) }

    fun setComposingRegion(start: Int, end: Int, token: EditorToken? = null): Boolean {
        if (start < 0 || end < start) return false
        return writeInternal(
            token = token,
            invalidateOnRejectedOperation = false
        ) {
            it.setComposingRegion(start, end)
        }
    }

    fun replaceRange(
        start: Int,
        end: Int,
        text: CharSequence,
        token: EditorToken
    ): Boolean {
        if (start < 0 || end < start) return false
        return write(token) { connection ->
            connection.beginBatchEdit()
            try {
                if (!connection.setSelection(start, end)) {
                    false
                } else if (!selectionAllowsRangeReplacement(token, start, end)) {
                    false
                } else {
                    connection.commitText(text, 1)
                }
            } finally {
                connection.endBatchEdit()
            }
        }
    }

    fun performEditorAction(action: Int): Boolean =
        write { it.performEditorAction(action) }

    fun sendEnter(): Boolean =
        write { connection ->
            val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            down && up
        }

    private fun selectionAllowsRangeReplacement(
        token: EditorToken,
        start: Int,
        end: Int
    ): Boolean {
        val current = currentToken() ?: return false
        if (!isCurrentSession(token)) return false
        return current.sameEditorState(token) ||
            (
                current.selectionStart == start &&
                current.selectionEnd == end
                )
    }

    private fun selectionAllowsExpectedTransition(
        token: EditorToken,
        expectedSelection: Int
    ): Boolean {
        val current = currentToken() ?: return false
        if (!isCurrentSession(token)) return false
        return current.sameEditorState(token) ||
            (
                current.selectionStart == expectedSelection &&
                    current.selectionEnd == expectedSelection
                )
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
            SafeLog.e(oom, "Editor connection OOM", NonFatalCategory.EDITOR)
            null
        } catch (t: Throwable) {
            SafeLog.e(t, "Editor connection", NonFatalCategory.EDITOR)
            null
        }

    private companion object {
        const val MAX_READ_CHARS = 4096
        const val UNKNOWN_SELECTION = -1
    }
}

/**
 * Turns one `getSurroundingText` reply into our view of the field, or null if
 * the reply can't be placed in the document.
 *
 * A negative [offset] means the editor does NOT know where the window it just
 * handed back starts (`SurroundingText.getOffset()` is documented as "-1 if
 * unknown"), and every editor that doesn't override `getSurroundingText` gets
 * exactly that: the framework's default implementation stitches the reply
 * together out of getTextBeforeCursor/getSelectedText/getTextAfterCursor, which
 * carry no absolute positions, and hardcodes -1. That covers every Jetpack
 * Compose text field -- Compose implements InputConnection directly rather than
 * extending BaseInputConnection -- including this app's own test field, plus
 * WebViews and other editors with a "fake" editable.
 *
 * Reading that -1 as a real base made every absolute position we derived from
 * it short by the size of the before-window, so [EditorGateway.surroundingText]'s
 * selection cross-check rejected EVERY read in those editors. Nothing could
 * verify itself against the field: the composer was abandoned on each keystroke
 * (suggestions restarted from the last letter typed -- "inf" offering words
 * starting with "f"), committed words could never be resumed for tap-to-replace,
 * and the n-gram context for next-word predictions never got captured.
 *
 * Returning null instead routes those editors to the getExtractedText path,
 * which does report a real `startOffset`.
 */
internal fun surroundingTextFromDirectRead(
    text: String,
    offset: Int,
    selectionStart: Int,
    selectionEnd: Int,
    composingBounds: Pair<Int, Int>?
): EditorSurroundingText? {
    if (offset < 0) return null
    return EditorSurroundingText(
        text = text,
        offset = offset,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        composingStart = composingBounds?.let { offset + it.first },
        composingEnd = composingBounds?.let { offset + it.second }
    ).takeIf(EditorSurroundingText::hasValidSelection)
}

private fun EditorSurroundingText.hasValidSelection(): Boolean =
    selectionStart in 0..text.length &&
        selectionEnd in 0..text.length

private fun Spanned.composingBounds(): Pair<Int, Int>? {
    var start = Int.MAX_VALUE
    var end = -1
    getSpans(0, length, Any::class.java).forEach { span ->
        if (getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0) {
            start = minOf(start, getSpanStart(span))
            end = maxOf(end, getSpanEnd(span))
        }
    }
    return if (start == Int.MAX_VALUE || end < start) null else start to end
}

private fun ExtractedText.toBoundedSurroundingText(
    beforeChars: Int,
    afterChars: Int
): EditorSurroundingText? {
    val extractedText = text ?: return null
    val extracted = extractedText.toString()
    if (selectionStart !in 0..extracted.length || selectionEnd !in 0..extracted.length) {
        return null
    }
    val composingBounds = (extractedText as? Spanned)?.composingBounds()
    val lowerSelection = minOf(selectionStart, selectionEnd)
    val upperSelection = maxOf(selectionStart, selectionEnd)
    val windowStart = (lowerSelection - beforeChars).coerceAtLeast(0)
    val windowEnd = (upperSelection + afterChars).coerceAtMost(extracted.length)
    return EditorSurroundingText(
        text = extracted.substring(windowStart, windowEnd),
        offset = startOffset + windowStart,
        selectionStart = selectionStart - windowStart,
        selectionEnd = selectionEnd - windowStart,
        composingStart = composingBounds?.let { startOffset + it.first },
        composingEnd = composingBounds?.let { startOffset + it.second }
    )
}
