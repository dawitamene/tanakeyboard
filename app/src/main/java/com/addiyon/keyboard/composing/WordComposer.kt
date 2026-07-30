package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import com.addiyon.keyboard.EditorGateway
import com.addiyon.keyboard.EditorToken
import java.util.ArrayDeque

/**
 * Owns the "currently-being-typed" word: a raw key buffer. One instance per
 * language, both composing the SAME thing inline -- the raw buffer itself,
 * underlined in the field's composing region as the user types (Gboard-style
 * pinyin IME: the romanized text is what's visible; a converted reading only
 * lands in the field on commit). They differ in [commitTransform]:
 *
 *   - English: [commitTransform] is the identity (nothing to convert).
 *   - Amharic: [commitTransform] turns the raw SERA Latin into the fidel
 *     word to commit (the service wires this to the ranked top
 *     transliteration candidate -- see
 *     [com.addiyon.keyboard.AddiyonKeyboardService]).
 *     Fidel suggestion readings live only in the suggestion strip while
 *     typing. Backspace removes one Latin character at a time (default
 *     [lastUnitStart]) and the region re-renders.
 *
 * An involuntary end to composition, such as a cursor move or the keyboard
 * closing, only finalizes the text already visible in the editor. It never
 * clears or replaces that text. Removing text is reserved for [onBackspace],
 * which is called only from the keyboard's explicit delete action.
 *
 * WHY A COMPOSER AT ALL
 *
 * The composing region is Android's first-class primitive for "this word may
 * still be replaced" -- text set with setComposingText is rendered underlined
 * and is atomically swapped out by the next setComposingText / commitText
 * call. That swap is exactly how a tapped suggestion replaces the half-typed
 * word ([commitSuggestion]), so words are composed rather than committed
 * keypress-by-keypress. This is the same approach AOSP's LatinIME takes.
 *
 * DESIGN
 *
 * - The raw buffer is the source of truth and IS the composing text --
 *   every keystroke mutates it and the whole buffer is re-pushed into the
 *   composing region, verbatim (no per-language rendering step while
 *   typing). [commitTransform] only runs at the moment text is about to
 *   become permanent field content, and is recomputed from the CURRENT
 *   buffer every time (never cached) so the committed word can't go stale
 *   relative to what's actually in the buffer -- the "stateless whole-buffer"
 *   strategy documented on Transliterator, extended to commit time.
 *
 * - The composer is fed an InputConnection *lambda*, not an InputConnection
 *   reference. The system swaps InputConnection instances between input
 *   sessions, and AddiyonKeyboardService.currentInputConnection reflects the
 *   live one. Capturing the current value once would go stale exactly the
 *   way the KeyboardScreen comment warns about. The lambda re-reads it on
 *   every call.
 *
 * WHAT COMMITS THE COMPOSING REGION
 *
 * Anything that means "this word is done":
 *   - space, enter, punctuation -> [commit]
 *   - language toggle           -> [commit] (we don't want a half-typed
 *                                   word stranded in the other pipeline)
 *   - a new input session       -> [reset]  (onStartInputView)
 *   - the cursor moving outside
 *     our composing region      -> [abandon] (user tapped elsewhere)
 *
 * The service owns those triggers; this class just exposes the operations.
 */
internal class WordComposer(
    inputConnection: () -> InputConnection?,
    /**
     * Buffer -> the form committed into the field: identity for English,
     * the ranked top transliteration reading for Amharic. Invoked fresh at
     * every commit site (never cached) so it always reflects the current
     * buffer -- see the class doc's "stateless whole-buffer" note.
     */
    private val commitTransform: (String) -> String = { it },
    private val lastUnitStart: (String) -> Int = { it.length - 1 },
    /**
     * Invoked with the raw buffer and its committed ([commitTransform])
     * form, right before the buffer clears, whenever committed text lands in
     * the field from [commit]. Amharic uses this to remember fidel -> raw Latin for
     * words committed this session, so the caret can walk back to one and
     * resume typing it (reverse-transliterating fidel in general isn't
     * reliable -- see WordTrie's class doc -- but a word we just composed
     * ourselves, we already have the raw Latin for for free). English has no
     * use for it (the field already holds the raw text).
     */
    private val onCommit: (raw: String, display: String) -> Unit = { _, _ -> },
    private val editor: EditorGateway = EditorGateway(connectionProvider = inputConnection)
) {

    private val buffer = StringBuilder()
    private var _rawCache: String = ""
    private var rawDirty = true
    private var cursorOffset = 0
    private var composingStart: Int? = null
    private var composingEnd: Int? = null
    private var lastPushedLength = 0
    private val replacedComposingRegions = ArrayDeque<Pair<Int, Int>>()
    private var pendingCursorOffset: Int? = null

    /** True while there's an active composing region we're responsible for. */
    val isComposing: Boolean
        get() = buffer.isNotEmpty()

    /**
     * The raw, unrendered key buffer -- what's actually shown in the field's
     * composing region while typing (underlined Latin for both languages).
     * The service uses it as the transliteration/dictionary lookup key.
     */
    val raw: String
        get() {
            if (rawDirty) {
                _rawCache = buffer.toString()
                rawDirty = false
            }
            return _rawCache
        }

    /**
     * A character key was pressed. Inserts at the current composing cursor
     * and pushes the updated whole buffer into the composing region verbatim.
     *
     * [char] is what the key produces AFTER shift/case has been applied by
     * the caller -- this class doesn't know about shift state. In Amharic,
     * feeding "H" vs "h" is how you reach ሐ vs ሀ once committed.
     */
    fun onCharacter(char: String, token: EditorToken? = null) {
        if (buffer.isEmpty()) {
            val selectionStart = token?.selectionStart ?: -1
            val selectionEnd = token?.selectionEnd ?: -1
            if (selectionStart >= 0 && selectionEnd >= 0) {
                composingStart = minOf(selectionStart, selectionEnd)
                composingEnd = composingStart
            }
        }
        buffer.insert(cursorOffset, char)
        cursorOffset += char.length
        rawDirty = true
        if (!pushComposing(token)) clearBuffer()
    }

    /**
     * Backspace pressed. Returns true if we absorbed the backspace (the
     * buffer had something to delete), false if the caller should apply
     * its own delete-from-text-field fallback.
     *
     * Deletes back to [lastUnitStart] -- one Latin character for both
     * languages by default, so the user clears the composed word letter by
     * letter.
     */
    fun onBackspace(token: EditorToken? = null): Boolean {
        if (buffer.isEmpty() || cursorOffset == 0) return false
        val unitStart = lastUnitStart(buffer.substring(0, cursorOffset))
            .coerceIn(0, cursorOffset)
        buffer.delete(unitStart, cursorOffset)
        cursorOffset = unitStart
        rawDirty = true
        if (buffer.isEmpty()) {
            // The user explicitly deleted the whole word, so the region
            // must end up EMPTY in the field (setComposingText("")), not
            // finalized; finishComposingText then closes the empty region's
            // bookkeeping cleanly.
            editor.setComposingText("", token)
            editor.finishComposingText(token)
            clearBuffer()
        } else {
            if (!pushComposing(token)) clearBuffer()
        }
        return true
    }

    /**
     * The word is done -- replace whatever is currently in the composing
     * region with [commitTransform] of the raw buffer and lock it into the
     * field as normal committed text, then clear the buffer. No-op if
     * there's nothing being composed. For English that's just the typed
     * word; for Amharic it swaps the underlined Latin for the ranked top
     * fidel reading.
     */
    fun commit(token: EditorToken? = null): Boolean {
        if (buffer.isEmpty()) return false
        val committedRaw = raw
        val committedDisplay = commitTransform(committedRaw)
        val committed = editor.commitText(committedDisplay, token)
        if (committed) {
            onCommit(committedRaw, committedDisplay)
        }
        clearBuffer()
        return committed
    }

    /**
     * Finalize the in-progress word exactly as it is currently visible,
     * without resolving it through [commitTransform].
     *
     * `finishComposingText` finalizes the existing span without replacing it.
     * `commitText` here would duplicate the word as an input session ends,
     * while an empty composing update would erase text without a delete
     * action.
     */
    fun finish() {
        if (buffer.isEmpty()) return
        editor.finishComposingText()
        clearBuffer()
    }

    /**
     * Adopt an already-typed word [prefix] that contains or ends at the caret,
     * so continuing to type edits THAT word instead of starting a fresh one.
     *
     * The caller is responsible for turning the existing field text into the
     * composing region first (deleting it and re-inserting it, or
     * setComposingRegion); here we just seed the buffer and re-push it so the
     * composing text and buffer stay in lockstep. Only meaningful when the
     * buffer is empty (we're not already composing) -- callers guard on
     * [isComposing].
     */
    fun resume(
        prefix: String,
        cursorOffset: Int = prefix.length,
        composingStart: Int? = null
    ) {
        if (prefix.isEmpty()) return
        buffer.setLength(0)
        buffer.append(prefix)
        this.cursorOffset = cursorOffset.coerceIn(0, prefix.length)
        this.composingStart = composingStart
        composingEnd = composingStart?.plus(prefix.length)
        rawDirty = true
        if (!pushComposing()) clearBuffer()
    }

    fun adopt(
        word: String,
        cursorOffset: Int,
        composingStart: Int,
        composingEnd: Int
    ): Boolean {
        if (
            buffer.isNotEmpty() ||
            word.isEmpty() ||
            cursorOffset !in 0..word.length ||
            composingStart < 0 ||
            composingEnd - composingStart != word.length
        ) {
            return false
        }
        buffer.append(word)
        this.cursorOffset = cursorOffset
        this.composingStart = composingStart
        this.composingEnd = composingEnd
        lastPushedLength = word.length
        rawDirty = true
        return true
    }

    fun moveCursor(
        cursorOffset: Int,
        composingStart: Int,
        composingEnd: Int
    ): Boolean {
        if (
            buffer.isEmpty() ||
            cursorOffset !in 0..buffer.length ||
            composingStart < 0 ||
            composingEnd - composingStart != buffer.length
        ) {
            return false
        }
        val ownedStart = this.composingStart ?: return false
        val ownedEnd = this.composingEnd ?: return false
        if (ownedStart != composingStart || ownedEnd != composingEnd) {
            return false
        }
        val pending = pendingCursorOffset
        if (pending != null && this.composingStart == composingStart) {
            if (cursorOffset == pending) {
                pendingCursorOffset = null
            } else if (cursorOffset == buffer.length) {
                return true
            } else {
                pendingCursorOffset = null
            }
        }
        this.cursorOffset = cursorOffset
        return true
    }

    fun isStaleComposingUpdate(composingStart: Int, composingEnd: Int): Boolean =
        composingStart to composingEnd in replacedComposingRegions

    fun ownedComposingRegion(): Pair<Int, Int>? {
        val start = composingStart ?: return null
        val end = composingEnd ?: return null
        return start to end
    }

    fun textBeforeCursor(): String =
        buffer.substring(0, cursorOffset)

    fun textAfterCursor(): String =
        buffer.substring(cursorOffset)

    fun commitAtCursor(token: EditorToken? = null): Boolean {
        if (buffer.isEmpty() || cursorOffset == buffer.length) return false
        val regionStart = composingStart ?: return false
        val leftRaw = buffer.substring(0, cursorOffset)
        val rightRaw = buffer.substring(cursorOffset)
        val leftDisplay = if (leftRaw.isEmpty()) "" else commitTransform(leftRaw)
        val rightDisplay = if (rightRaw.isEmpty()) "" else commitTransform(rightRaw)
        if (
            editor.commitTextAndSelection(
                text = leftDisplay + rightDisplay,
                selection = regionStart + leftDisplay.length,
                expectedIntermediateSelection =
                    regionStart + leftDisplay.length + rightDisplay.length,
                token = token
            )
        ) {
            if (leftRaw.isNotEmpty()) onCommit(leftRaw, leftDisplay)
            if (rightRaw.isNotEmpty()) onCommit(rightRaw, rightDisplay)
        }
        clearBuffer()
        return true
    }

    /**
     * A suggestion chip was tapped: swap whatever's currently composing for
     * [word] plus a trailing space, and clear the buffer.
     *
     * Unlike [commit], this doesn't just lock in the CURRENT composing text
     * -- [word] is a different, complete word than whatever partial text is
     * showing. `commitText` on Android replaces the active composing span
     * with the given text (rather than appending after it), so no separate
     * finishComposingText() call is needed first; clearing the buffer means
     * the next keystroke starts a fresh word.
     */
    fun commitSuggestion(word: String, token: EditorToken? = null): Boolean {
        val regionStart = composingStart ?: 0
        val committed = editor.commitText("$word ", token)
        if (committed) {
            editor.noteSelection(regionStart + word.length + 1, regionStart + word.length + 1)
        }
        clearBuffer()
        return committed
    }

    /**
     * Abandon the buffer WITHOUT committing. Used when a new input session
     * starts -- the InputConnection we were composing into may not even be
     * the same one anymore, so we can't meaningfully finish that composition,
     * and we definitely don't want to carry a half-typed word into the
     * next field.
     */
    fun reset() {
        clearBuffer()
    }

    /**
     * The user moved the cursor out from under the composing region (they
     * tapped elsewhere in the text). Freeze whatever is currently underlined
     * into the field as-is so cursor movement can never remove or replace
     * editor content.
     *
     * Distinct from [reset], which drops the buffer without touching the
     * field, and from [commit], which is called at natural word boundaries
     * (space/enter/language toggle). This is the "involuntary" exit
     * triggered by external cursor movement.
     */
    fun abandon() {
        if (buffer.isEmpty()) return
        editor.finishComposingText()
        clearBuffer()
    }

    private fun clearBuffer() {
        buffer.clear()
        cursorOffset = 0
        composingStart = null
        composingEnd = null
        lastPushedLength = 0
        replacedComposingRegions.clear()
        pendingCursorOffset = null
        rawDirty = true
    }

    private fun pushComposing(token: EditorToken? = null): Boolean {
        val desiredCursor = cursorOffset
        val regionStart = composingStart
        if (lastPushedLength > 0 && lastPushedLength != buffer.length) {
            if (regionStart != null) {
                replacedComposingRegions.addLast(
                    regionStart to (regionStart + lastPushedLength)
                )
            }
            while (replacedComposingRegions.size > 4) {
                replacedComposingRegions.removeFirst()
            }
        }
        lastPushedLength = buffer.length
        if (regionStart != null) {
            composingEnd = regionStart + buffer.length
        }
        if (regionStart != null && desiredCursor < buffer.length) {
            pendingCursorOffset = desiredCursor
            val selection = regionStart + desiredCursor
            val accepted = editor.setComposingTextAndSelection(
                text = raw,
                selection = selection,
                expectedIntermediateSelection = regionStart + raw.length,
                token = token
            )
            if (accepted) {
                editor.noteSelection(selection, selection)
            } else {
                pendingCursorOffset = null
            }
            return accepted
        }
        pendingCursorOffset = null
        if (regionStart == null) {
            return editor.setComposingText(raw, token)
        }
        val selection = regionStart + raw.length
        val accepted = editor.setComposingText(raw, token)
        if (accepted) {
            editor.noteSelection(selection, selection)
        }
        return accepted
    }
}
