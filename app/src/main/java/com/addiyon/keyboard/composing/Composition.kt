package com.addiyon.keyboard.composing

import com.addiyon.keyboard.EditorGateway

/**
 * The word currently being typed, held as a raw key buffer that IS the editor's
 * composing region.
 *
 * REPLACES [WordComposer]. The difference is not the feature set, it is what the
 * class is allowed to know.
 *
 * THE RULE
 *
 * This class never computes, stores, or passes an absolute document offset.
 * Every editor operation it issues is cursor-relative:
 *
 *     setComposingText · commitText · finishComposingText
 *
 * All three act on "the current composing region, or the caret if there is
 * none" -- positions the EDITOR owns and the IME cannot get wrong. Contrast
 * [WordComposer], which tracked `composingStart`/`composingEnd` as absolute
 * offsets seeded from a selection read and thereafter maintained by arithmetic,
 * then fed them back as ranges to overwrite (`setComposingRegion`,
 * `commitTextAndSelection`, `replaceRange`). Those numbers are only correct when
 * the editor reports honest absolute positions, which Compose text fields,
 * WebViews and naive `getExtractedText` implementations do not. When they drifted
 * the IME wrote to the wrong range; when the drift was *detected* the feature
 * silently did nothing instead (see `LegacyServiceReproTest`, where chip taps
 * no-op in every editor whose reads cannot be cross-checked).
 *
 * Removing the offsets removes both failure modes, and with them the machinery
 * that existed only to second-guess them: `pendingCursorOffset`,
 * `replacedComposingRegions`, `moveCursor`, `commitAtCursor` and the
 * `EditorToken` threaded through every call.
 *
 * THE CARET IS ALWAYS AT THE END OF THE BUFFER
 *
 * A composition exists only while the caret sits at its end. The moment the user
 * moves the caret anywhere else, the controller finalizes the composition in
 * place and this object goes inactive (see `TypingController.onSelectionChanged`).
 * That is the Gboard/AOSP LatinIME rule, and it is what makes "the caret is at
 * `buffer.length`" true by construction rather than by bookkeeping -- which in
 * turn means each keystroke issues exactly ONE write and therefore produces
 * exactly one selection callback, with nothing to predict or de-duplicate.
 *
 * WHAT PUTS TEXT IN THE FIELD PERMANENTLY
 *
 * Only an explicit accept gesture: space, enter, punctuation ([commit]) or a
 * tapped suggestion ([replaceWith]). An involuntary exit -- caret move, keyboard
 * hiding, input session ending -- goes through [finalizeInPlace], which freezes
 * exactly what is already visible and can never add, remove or replace text.
 */
internal class Composition(
    private val editor: EditorGateway,
    /**
     * Buffer -> the text that lands in the field on an explicit commit. Identity
     * for English; the ranked top fidel reading for Amharic. Invoked fresh at
     * every commit site so it always reflects the current buffer.
     */
    private val commitTransform: (String) -> String = { it },
    /**
     * Where backspace should cut back to, given the buffer. Default removes one
     * character, so a composed word clears letter by letter.
     */
    private val lastUnitStart: (String) -> Int = { it.length - 1 },
    /**
     * Called with the raw buffer and its committed form whenever text lands in
     * the field via [commit] or [replaceWith]. Amharic uses it to remember
     * fidel -> raw Latin for words committed this session, so the caret can walk
     * back to one and resume editing it in Latin (see [ResumedWordMemory]).
     */
    private val onCommit: (raw: String, display: String) -> Unit = { _, _ -> }
) {

    private val buffer = StringBuilder()
    private var cachedRaw: String = ""
    private var rawDirty = false

    /** True while a word is being composed and we own the editor's composing region. */
    val isActive: Boolean
        get() = buffer.isNotEmpty()

    /** The raw key buffer -- exactly what is underlined in the field right now. */
    val raw: String
        get() {
            if (rawDirty) {
                cachedRaw = buffer.toString()
                rawDirty = false
            }
            return cachedRaw
        }

    /** What [commit] would put in the field, computed from the current buffer. */
    fun previewCommit(): String = commitTransform(raw)

    /**
     * A character key was pressed. [text] is post-shift: this class knows nothing
     * about shift state, and in Amharic feeding "H" rather than "h" is how the
     * user reaches ሐ rather than ሀ.
     */
    fun append(text: String): Boolean {
        if (text.isEmpty()) return false
        buffer.append(text)
        rawDirty = true
        return push()
    }

    /**
     * Backspace. Returns false if there was nothing composed to delete, in which
     * case the caller applies its own field-level delete.
     */
    fun backspace(): Boolean {
        if (buffer.isEmpty()) return false
        val cut = lastUnitStart(raw).coerceIn(0, buffer.length - 1)
        buffer.setLength(cut)
        rawDirty = true
        if (buffer.isEmpty()) {
            // The user deleted the whole word, so the region must end up EMPTY in
            // the field -- setComposingText("") removes the text, and
            // finishComposingText then closes the empty region's bookkeeping
            // cleanly (some editors leave a stale empty region otherwise).
            clear()
            editor.setComposingText("")
            editor.finishComposingText()
        } else {
            push()
        }
        return true
    }

    /**
     * The word is done: replace the composing region with [commitTransform] of the
     * buffer and lock it in. `commitText` swaps the whole region atomically, so no
     * separate finish is needed.
     *
     * Returns whether the editor accepted the write. Callers MUST act on this --
     * [WordComposer.commitAtCursor] returned true unconditionally, so a rejected
     * write looked like a successful one and the word was dropped from the buffer
     * without ever reaching the field.
     */
    fun commit(): Boolean {
        if (buffer.isEmpty()) return false
        val committedRaw = raw
        val display = commitTransform(committedRaw)
        // Clear BEFORE writing. An editor that notifies synchronously from inside
        // its own mutation would otherwise observe a composition that has been
        // committed but still looks active, conclude the caret has left it, and
        // finalize it — recursively. Reaching the final state before yielding
        // control makes that unrepresentable rather than merely guarded against.
        clear()
        val accepted = editor.commitText(display)
        if (accepted) onCommit(committedRaw, display)
        return accepted
    }

    /**
     * A suggestion chip was tapped: swap the composing region for [word] and a
     * trailing space. `commitText` replaces the active region rather than
     * appending after it, so this needs no offsets and no prior finish.
     *
     * Also valid with nothing composed -- a next-word prediction inserts at the
     * caret -- which is why there is no empty-buffer guard.
     */
    fun replaceWith(word: String, trailingSpace: Boolean = true): Boolean {
        val display = if (trailingSpace) "$word " else word
        val committedRaw = raw
        clear()
        val accepted = editor.commitText(display)
        if (accepted && committedRaw.isNotEmpty()) onCommit(committedRaw, word)
        return accepted
    }

    /**
     * Freeze what is currently underlined, exactly as it appears, and stop owning
     * it. Used for every involuntary end to composition: the caret moved, the
     * keyboard closed, the input session ended.
     *
     * `finishComposingText` finalizes the span without touching its content.
     * Committing here would duplicate the word; pushing an empty region would
     * erase text the user never asked to delete. Cursor movement must never be
     * able to change field content -- that is the whole contract.
     */
    fun finalizeInPlace() {
        if (buffer.isEmpty()) return
        clear()
        editor.finishComposingText()
    }

    /**
     * Drop our bookkeeping without touching the field. For a new input session,
     * where the connection we were composing into may not even exist any more.
     */
    fun discard() {
        clear()
    }

    /**
     * Seed the buffer for a word the caller has just turned into the editor's
     * composing region (see [WordAdoption]). Bookkeeping only -- writes nothing,
     * because the caller's delete+compose already put the region in place.
     */
    fun adopt(word: String): Boolean {
        if (buffer.isNotEmpty() || word.isEmpty()) return false
        buffer.append(word)
        rawDirty = true
        return true
    }

    private fun push(): Boolean {
        val accepted = editor.setComposingText(raw)
        // A refused write means the editor is gone or unwilling; keeping a buffer
        // that no longer matches any region would make the next keystroke push a
        // word the field never showed.
        if (!accepted) clear()
        return accepted
    }

    private fun clear() {
        buffer.setLength(0)
        cachedRaw = ""
        rawDirty = false
    }
}
