package com.addiyon.tanakeyboard.composing

import android.view.inputmethod.InputConnection

/**
 * Owns the "currently-being-typed" word: a raw key buffer, mirrored into the
 * field's composing region (underlined, replaceable) as you type. One
 * instance per language, differing in the injected lambdas:
 *
 *   - Amharic: [composingText] = identity, so the field shows the LITERAL
 *     Latin the user is typing ("sh"), while [render] =
 *     Transliterator.transliterate produces the fidel ("ሽ") used for the
 *     suggestion strip and for [commit]. Backspace removes one Latin
 *     character at a time (default [lastUnitStart]), so the user can clear
 *     the raw letters one by one.
 *   - English: all defaults -- the buffer IS both the composing text and the
 *     display, backspace removes one character.
 *
 * WHY THE LATIN IS SHOWN INLINE (AMHARIC)
 *
 * Earlier the Amharic word lived ONLY in the suggestion strip and nothing was
 * written to the field until commit. That confused users -- typing produced
 * no visible text in the field. Now the raw Latin is composed inline
 * (underlined) so there's always visible feedback and each letter can be
 * cleared, while the ambiguous fidel readings (ስህ, ሽ, …) are offered in the
 * strip. Picking a reading (tap or space) atomically replaces the Latin span
 * with the chosen fidel.
 *
 * WHY A COMPOSER AT ALL
 *
 * The composing region is Android's first-class primitive for "this word may
 * still be replaced" -- text set with setComposingText is rendered underlined
 * and is atomically swapped out by the next setComposingText / commitText
 * call. That swap is exactly how a tapped suggestion replaces the half-typed
 * word ([commitSuggestion]) and how Amharic's Latin becomes fidel on commit,
 * so words are composed rather than committed keypress-by-keypress. This is
 * the same approach AOSP's LatinIME takes.
 *
 * DESIGN
 *
 * - The raw buffer is the source of truth. Every keystroke mutates it, then
 *   re-runs [composingText] over the whole buffer and pushes the result into
 *   the composing region, while [render] derives the committed/looked-up form
 *   -- the "stateless whole-buffer" strategy documented on Transliterator.
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
    /**
     * Buffer -> the form committed into the field and used for dictionary
     * lookups (fidel for Amharic, identity for English).
     */
    private val render: (String) -> String = { it },
    /**
     * Buffer -> the text shown in the underlined composing region as the user
     * types. Defaults to [render]; Amharic overrides it to identity so the
     * literal Latin ("sh") is shown inline while [render] still produces the
     * fidel used for suggestions and [commit].
     */
    private val composingText: (String) -> String = render,
    private val lastUnitStart: (String) -> Int = { it.length - 1 }
) {

    private val buffer = StringBuilder()

    /** True while there's an active composing region we're responsible for. */
    val isComposing: Boolean
        get() = buffer.isNotEmpty()

    /**
     * The rendered buffer -- the form committed into the field and that
     * suggestions key off. For Amharic that's the live fidel (looking words
     * up by the raw Latin would require reverse transliteration, which is
     * ambiguous -- see WordTrie's class doc), and for English it's simply the
     * word typed so far. Distinct from what the composing region *shows*,
     * which for Amharic is the raw Latin ([composingText]).
     */
    val display: String
        get() = render(buffer.toString())

    /**
     * The raw, unrendered key buffer -- for Amharic the romanized SERA Latin
     * behind the fidel [display]. The service uses it to offer the alternate
     * "separated" reading of an ambiguous digraph (Transliterator.transliterateSplit),
     * which can't be recovered from [display] because forward transliteration
     * isn't reliably invertible.
     */
    val raw: String
        get() = buffer.toString()

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
     * Deletes back to [lastUnitStart] -- one Latin character for both
     * languages by default, so the user clears the composed word letter by
     * letter.
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
     * The word is done -- replace whatever is currently in the composing
     * region with the rendered [display] and lock it into the field as normal
     * committed text, then clear the buffer. No-op if there's nothing being
     * composed. For English [display] equals the composing text, so this just
     * finalizes the word; for Amharic it swaps the underlined Latin for the
     * greedy fidel reading.
     */
    fun commit() {
        if (buffer.isEmpty()) return
        inputConnection()?.commitText(display, 1)
        buffer.clear()
    }

    /**
     * Finalize the composing word IN PLACE, without inserting new text.
     *
     * Used when the input view is going away (the field is losing us). Unlike
     * [commit], which calls `commitText`, this never inserts text: it resolves
     * the composing region to its rendered [display] (fidel for Amharic) with
     * `setComposingText` -- which REPLACES the current composing span rather
     * than appending after it -- then `finishComposingText` locks it in place.
     *
     * `commitText` on the way out duplicated the word: as an input session
     * ends, the framework finalizes the still-active composing region on its
     * own, so an additional `commitText` pasted a second copy (the "text
     * appears twice after exiting the keyboard" bug). This path can't double,
     * and it commits no autosuggestion -- [display] is exactly the text the
     * user typed (English) or its greedy fidel reading (Amharic), never a
     * dictionary completion.
     */
    fun finish() {
        if (buffer.isEmpty()) return
        inputConnection()?.apply {
            setComposingText(display, 1)
            finishComposingText()
        }
        buffer.clear()
    }

    /**
     * Adopt an already-typed word [prefix] that is sitting in the field just
     * before the caret, so that continuing to type extends THAT word instead
     * of starting a fresh one. Used when the user moves the caret back to the
     * end of a previously committed word and resumes typing ("infor", tap
     * away, come back, type "mation" -> "information", with the strip keying
     * off the whole "informa…" rather than "mation").
     *
     * The caller is responsible for turning the existing field text into the
     * composing region first (deleting it and re-inserting it, or
     * setComposingRegion); here we just seed the buffer and re-push it so the
     * composing text and buffer stay in lockstep. Only meaningful when the
     * buffer is empty (we're not already composing) -- callers guard on
     * [isComposing].
     */
    fun resume(prefix: String) {
        if (prefix.isEmpty()) return
        buffer.setLength(0)
        buffer.append(prefix)
        pushComposing()
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
        // Freeze the rendered word into the field wherever the composing
        // region currently sits (for Amharic, this resolves the underlined
        // Latin to its greedy fidel), then drop the buffer.
        inputConnection()?.commitText(display, 1)
        buffer.clear()
    }

    private fun pushComposing() {
        // newCursorPosition=1 means: place caret at the end of the composed
        // text (offset 1 past its last char), which is the natural "keep
        // typing" position.
        inputConnection()?.setComposingText(composingText(buffer.toString()), 1)
    }
}
