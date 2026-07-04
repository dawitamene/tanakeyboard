package com.addiyon.tanakeyboard.transliteration

import android.view.inputmethod.InputConnection

/**
 * Owns the "currently-being-typed" Amharic word: the romanized Latin
 * buffer, and the mirror of that buffer in fidel that's shown to the user
 * as underlined composing text.
 *
 * WHY A COMPOSER AT ALL
 *
 * In Amharic mode, a keypress is ambiguous until the syllable ends. Typing
 * "s" might become "sh" (ሽ) or "se" (ሰ) or stay "s" (ስ). If we committed
 * each keypress with commitText, we'd have to keep deleting and re-typing
 * to fix it up -- flickery, and it fights with any downstream text field
 * that treats each commit as a discrete edit.
 *
 * Android's InputConnection has a first-class primitive for exactly this
 * situation: the *composing region*. Text set with setComposingText is
 * rendered underlined in the target field and is atomically replaced by
 * the next setComposingText / finishComposingText / commitText call. That's
 * the same mechanism autocomplete uses to swap a half-typed word for a
 * suggestion, which is why building on it now sets up step 7 for free.
 *
 * DESIGN
 *
 * - The Latin buffer is the source of truth. Every keystroke mutates it,
 *   then re-runs [Transliterator.transliterate] over the whole buffer and
 *   pushes the result into the composing region. This is exactly the
 *   "stateless whole-buffer" strategy documented on Transliterator itself.
 *
 * - Backspace shrinks the Latin buffer by one char (not one fidel glyph).
 *   So "she"->ሸ, backspace -> ሽ (buffer now "sh"), backspace -> ስ (buffer
 *   now "s"), backspace -> empty. This matches how Gboard's Amharic layout
 *   behaves and is what users expect once they've internalized the scheme.
 *
 * - The composer is fed an InputConnection *lambda*, not an InputConnection
 *   reference. The system swaps InputConnection instances between input
 *   sessions, and TanaKeyboardService.currentInputConnection reflects the
 *   live one. Capturing the current value once would go stale exactly the
 *   way the KeyboardScreen comment warns about. The lambda re-reads it on
 *   every call.
 *
 * WHAT COMMITS THE COMPOSING REGION
 *
 * Anything that means "this word is done":
 *   - space, enter, punctuation -> [commit]
 *   - language toggle           -> [commit] (we don't want a half-Amharic
 *                                   syllable stranded in the English pipeline)
 *   - a new input session       -> [reset]  (onStartInputView)
 *   - the cursor moving outside
 *     our composing region      -> [commit] (user tapped elsewhere)
 *
 * The service owns those triggers; this class just exposes the operations.
 */
internal class AmharicComposer(
    private val inputConnection: () -> InputConnection?
) {

    private val latin = StringBuilder()

    /** True while there's an active composing region we're responsible for. */
    val isComposing: Boolean
        get() = latin.isNotEmpty()

    /** The current romanized buffer -- what autocomplete will key off later. */
    val currentLatin: String
        get() = latin.toString()

    /**
     * A character key was pressed. Appends to the Latin buffer and pushes
     * the re-transliterated fidel into the composing region.
     *
     * [char] is what the key produces AFTER shift/case has been applied by
     * the caller -- this class doesn't know about shift state. Feeding "H"
     * vs "h" is how you reach ሐ vs ሀ.
     */
    fun onCharacter(char: String) {
        latin.append(char)
        pushComposing()
    }

    /**
     * Backspace pressed. Returns true if we absorbed the backspace (the
     * buffer had something to delete), false if the caller should apply
     * its own delete-from-text-field fallback.
     *
     * Deletes one Latin character, not one fidel glyph -- see the class
     * doc for why.
     */
    fun onBackspace(): Boolean {
        if (latin.isEmpty()) return false
        latin.deleteCharAt(latin.length - 1)
        if (latin.isEmpty()) {
            // Empty setComposingText leaves an empty composing region hanging
            // around in some IMEs' bookkeeping. finishComposingText resolves
            // it cleanly to nothing.
            inputConnection()?.finishComposingText()
        } else {
            pushComposing()
        }
        return true
    }

    /**
     * The word is done -- lock whatever is currently in the composing
     * region into the field as normal committed text and clear the buffer.
     * No-op if there's nothing being composed.
     */
    fun commit() {
        if (latin.isEmpty()) return
        inputConnection()?.finishComposingText()
        latin.clear()
    }

    /**
     * Abandon the buffer WITHOUT committing. Used when a new input session
     * starts -- the InputConnection we were composing into may not even be
     * the same one anymore, so we can't meaningfully finish that composition,
     * and we definitely don't want to carry a half-typed syllable into the
     * next field.
     */
    fun reset() {
        latin.clear()
    }

    /**
     * The user moved the cursor out from under the composing region (they
     * tapped elsewhere in the text). Freeze whatever fidel is currently
     * underlined into the field as-is and forget the Latin buffer -- we
     * can't keep transliterating a word the user has visibly walked away
     * from.
     *
     * Distinct from [reset], which drops the buffer without touching the
     * field, and from [commit], which is called at natural word boundaries
     * (space/enter/language toggle). This is the "involuntary" commit
     * triggered by external cursor movement.
     */
    fun abandon() {
        if (latin.isEmpty()) return
        inputConnection()?.finishComposingText()
        latin.clear()
    }

    private fun pushComposing() {
        val fidel = Transliterator.transliterate(latin.toString())
        // newCursorPosition=1 means: place caret at the end of the composed
        // text (offset 1 past its last char), which is the natural "keep
        // typing" position.
        inputConnection()?.setComposingText(fidel, 1)
    }
}