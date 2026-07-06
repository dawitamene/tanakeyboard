package com.addiyon.tanakeyboard.composing

import android.view.inputmethod.InputConnection

/**
 * Owns the "currently-being-typed" word: a raw key buffer, and the rendered
 * mirror of that buffer that's shown to the user as underlined composing
 * text. One instance per language, differing only in the two lambdas:
 *
 *   - Amharic: [render] = Transliterator.transliterate (the buffer is
 *     romanized SERA Latin, the display is fidel), [lastUnitStart] =
 *     Transliterator.lastUnitStart (backspace removes the whole Latin span
 *     behind the last rendered fidel character, so "she" -> ሸ dies in one
 *     press instead of stepping back through intermediate consonants).
 *   - English: both defaults (the buffer IS the display, backspace removes
 *     one character).
 *
 * WHY A COMPOSER AT ALL
 *
 * In Amharic mode, a keypress is ambiguous until the syllable ends. Typing
 * "s" might become "sh" (ሽ) or "se" (ሰ) or stay "s" (ስ). If we committed
 * each keypress with commitText, we'd have to keep deleting and re-typing
 * to fix it up -- flickery, and it fights with any downstream text field
 * that treats each commit as a discrete edit.
 *
 * English has no such ambiguity, but it composes through this class for a
 * different reason: suggestions. The composing region is Android's
 * first-class primitive for "this word may still be replaced" -- text set
 * with setComposingText is rendered underlined and is atomically swapped
 * out by the next setComposingText / finishComposingText / commitText call.
 * That swap is exactly how a tapped suggestion replaces the half-typed word
 * ([commitSuggestion]), so English words are composed rather than committed
 * keypress-by-keypress. This is the same approach AOSP's LatinIME takes.
 *
 * DESIGN
 *
 * - The raw buffer is the source of truth. Every keystroke mutates it, then
 *   re-runs [render] over the whole buffer and pushes the result into the
 *   composing region -- the "stateless whole-buffer" strategy documented on
 *   Transliterator itself.
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
 *   - language toggle           -> [commit] (we don't want a half-typed
 *                                   word stranded in the other pipeline)
 *   - a new input session       -> [reset]  (onStartInputView)
 *   - the cursor moving outside
 *     our composing region      -> [abandon] (user tapped elsewhere)
 *
 * The service owns those triggers; this class just exposes the operations.
 */
internal class WordComposer(
    private val inputConnection: () -> InputConnection?,
    private val render: (String) -> String = { it },
    private val lastUnitStart: (String) -> Int = { it.length - 1 }
) {

    private val buffer = StringBuilder()

    /** True while there's an active composing region we're responsible for. */
    val isComposing: Boolean
        get() = buffer.isNotEmpty()

    /**
     * What the composing region currently shows -- the rendered buffer.
     * This is the string suggestions key off: for Amharic that's the live
     * fidel (looking words up by the raw Latin would require reverse
     * transliteration, which is ambiguous -- see WordTrie's class doc), and
     * for English it's simply the word typed so far.
     */
    val display: String
        get() = render(buffer.toString())

    /**
     * A character key was pressed. Appends to the buffer and pushes the
     * re-rendered word into the composing region.
     *
     * [char] is what the key produces AFTER shift/case has been applied by
     * the caller -- this class doesn't know about shift state. In Amharic,
     * feeding "H" vs "h" is how you reach ሐ vs ሀ.
     */
    fun onCharacter(char: String) {
        buffer.append(char)
        pushComposing()
    }

    /**
     * Backspace pressed. Returns true if we absorbed the backspace (the
     * buffer had something to delete), false if the caller should apply
     * its own delete-from-text-field fallback.
     *
     * Deletes back to [lastUnitStart] -- the whole span behind the last
     * rendered character, which for Amharic can be several Latin chars.
     */
    fun onBackspace(): Boolean {
        if (buffer.isEmpty()) return false
        buffer.setLength(lastUnitStart(buffer.toString()))
        if (buffer.isEmpty()) {
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
        if (buffer.isEmpty()) return
        inputConnection()?.finishComposingText()
        buffer.clear()
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
    fun commitSuggestion(word: String) {
        inputConnection()?.commitText("$word ", 1)
        buffer.clear()
    }

    /**
     * Abandon the buffer WITHOUT committing. Used when a new input session
     * starts -- the InputConnection we were composing into may not even be
     * the same one anymore, so we can't meaningfully finish that composition,
     * and we definitely don't want to carry a half-typed word into the
     * next field.
     */
    fun reset() {
        buffer.clear()
    }

    /**
     * The user moved the cursor out from under the composing region (they
     * tapped elsewhere in the text). Freeze whatever is currently underlined
     * into the field as-is and forget the buffer -- we can't keep rewriting
     * a word the user has visibly walked away from.
     *
     * Distinct from [reset], which drops the buffer without touching the
     * field, and from [commit], which is called at natural word boundaries
     * (space/enter/language toggle). This is the "involuntary" commit
     * triggered by external cursor movement.
     */
    fun abandon() {
        if (buffer.isEmpty()) return
        inputConnection()?.finishComposingText()
        buffer.clear()
    }

    private fun pushComposing() {
        // newCursorPosition=1 means: place caret at the end of the composed
        // text (offset 1 past its last char), which is the natural "keep
        // typing" position.
        inputConnection()?.setComposingText(render(buffer.toString()), 1)
    }
}
