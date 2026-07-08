package com.addiyon.tanakeyboard.composing

import android.view.inputmethod.InputConnection

/**
 * Owns the "currently-being-typed" word: a raw key buffer. One instance per
 * language, differing in [composesInline] and [render]:
 *
 *   - Amharic: [composesInline] = false. Nothing is written to the field
 *     while typing -- the raw Latin ([raw]) and its live fidel reading
 *     ([display], via [render] = Transliterator.transliterate) are instead
 *     surfaced in a preview strip above the suggestion row (see
 *     [com.addiyon.tanakeyboard.TanaKeyboardService.amharicBufferLatin] --
 *     its fidel reading isn't duplicated there, since it's already the first
 *     suggestion chip). The field is only ever touched by [commit] /
 *     [commitSuggestion], both of which use `commitText` directly -- no
 *     composing region is involved. Backspace removes one Latin character at
 *     a time (default [lastUnitStart]) from the buffer only.
 *   - English: [composesInline] = true (default), all defaults -- the buffer
 *     IS both the composing text and the display, mirrored into the field's
 *     composing region (underlined, replaceable) as you type, backspace
 *     removes one character.
 *
 * WHY AMHARIC COMPOSES OUT-OF-FIELD
 *
 * Composing the raw Latin inline (underlined in the field) used to be how
 * Amharic worked, but [finish] can only lock in whatever the composing region
 * CURRENTLY SHOWS -- so if the input view went away before the word was
 * committed (tapping away, hiding the keyboard), the raw Latin ("selam") got
 * finalized into the field instead of its fidel reading, or instead of
 * nothing. Keeping the buffer entirely out of the field until [commit] /
 * [commitSuggestion] makes that class of bug impossible by construction: an
 * uncommitted word simply never touched the field, so there's nothing to
 * strand there. English keeps the inline behavior -- it works correctly
 * there, and a "Latin | fidel" split is meaningless for a language with no
 * transliteration step.
 *
 * WHY A COMPOSER AT ALL
 *
 * For English, the composing region is Android's first-class primitive for
 * "this word may still be replaced" -- text set with setComposingText is
 * rendered underlined and is atomically swapped out by the next
 * setComposingText / commitText call. That swap is exactly how a tapped
 * suggestion replaces the half-typed word ([commitSuggestion]), so words are
 * composed rather than committed keypress-by-keypress. This is the same
 * approach AOSP's LatinIME takes. Amharic doesn't need the region at all --
 * [commitText] replaces nothing, it just inserts -- since nothing is in the
 * field yet to replace.
 *
 * DESIGN
 *
 * - The raw buffer is the source of truth. Every keystroke mutates it; if
 *   [composesInline] the whole buffer is re-rendered and pushed into the
 *   composing region, while [render] derives the committed/looked-up form
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
     * Whether this composer mirrors its buffer into the field's composing
     * region as the user types. True for English (the underlined "sh" the
     * user sees IS the buffer). False for Amharic: the buffer lives only in
     * [raw] / [display] for the preview strip to read, and the field is
     * untouched until [commit] / [commitSuggestion].
     */
    private val composesInline: Boolean = true,
    private val lastUnitStart: (String) -> Int = { it.length - 1 }
) {

    private val buffer = StringBuilder()
    private var _rawCache: String = ""
    private var rawDirty = true

    /** True while there's an active composing region we're responsible for. */
    val isComposing: Boolean
        get() = buffer.isNotEmpty()

    /**
     * The rendered buffer -- the form committed into the field and that
     * suggestions key off. For Amharic that's the live fidel (looking words
     * up by the raw Latin would require reverse transliteration, which is
     * ambiguous -- see WordTrie's class doc), and for English it's simply the
     * word typed so far. For Amharic this is the fidel half of the preview
     * strip; the field itself shows nothing until [commit].
     */
    val display: String
        get() = render(raw)

    /**
     * The raw, unrendered key buffer -- for Amharic the romanized SERA Latin
     * behind the fidel [display]. The service uses it to offer the alternate
     * "separated" reading of an ambiguous digraph (Transliterator.transliterateSplit),
     * which can't be recovered from [display] because forward transliteration
     * isn't reliably invertible.
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
     * A character key was pressed. Appends to the buffer and pushes the
     * re-rendered word into the composing region.
     *
     * [char] is what the key produces AFTER shift/case has been applied by
     * the caller -- this class doesn't know about shift state. In Amharic,
     * feeding "H" vs "h" is how you reach ሐ vs ሀ.
     */
    fun onCharacter(char: String) {
        buffer.append(char)
        rawDirty = true
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
        rawDirty = true
        if (composesInline) {
            if (buffer.isEmpty()) {
                // Empty setComposingText leaves an empty composing region
                // hanging around in some IMEs' bookkeeping. finishComposingText
                // resolves it cleanly to nothing.
                inputConnection()?.finishComposingText()
            } else {
                pushComposing()
            }
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
        rawDirty = true
    }

    /**
     * Finalize (English) or discard (Amharic) the in-progress word, without
     * inserting new text or resolving it to any reading.
     *
     * Used when the input view is going away (the field is losing us) and the
     * user never explicitly accepted a word (space, enter, or a tapped
     * suggestion). For English, locks in whatever is CURRENTLY shown in the
     * composing region -- via `finishComposingText`, which finalizes the
     * existing span without replacing it -- rather than [display]; applying
     * [display] here would silently promote the top suggestion to committed
     * text on exit even though the user never chose it. For Amharic there is
     * no composing region and nothing in the field to finalize, so this is a
     * pure buffer discard -- the fix for the old "leftover raw Latin
     * stranded in the field" bug: nothing was ever written there to strand.
     *
     * `commitText` on the way out duplicated the word for English: as an
     * input session ends, the framework finalizes the still-active composing
     * region on its own, so an additional `commitText` pasted a second copy
     * (the "text appears twice after exiting the keyboard" bug). This path
     * can't double.
     */
    fun finish() {
        if (buffer.isEmpty()) return
        if (composesInline) inputConnection()?.finishComposingText()
        buffer.clear()
        rawDirty = true
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
        rawDirty = true
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
        rawDirty = true
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
        rawDirty = true
    }

    /**
     * The user moved the cursor out from under the composing region (they
     * tapped elsewhere in the text). For English, freeze whatever is
     * currently underlined into the field as-is -- we can't keep rewriting a
     * word the user has visibly walked away from. For Amharic there's no
     * composing region and nothing in the field to freeze, so this is a pure
     * discard: the half-typed word simply vanishes, matching the
     * "uncommitted word never touches the field" contract.
     *
     * Distinct from [reset], which drops the buffer without touching the
     * field, and from [commit], which is called at natural word boundaries
     * (space/enter/language toggle). This is the "involuntary" commit (or,
     * for Amharic, discard) triggered by external cursor movement.
     */
    fun abandon() {
        if (buffer.isEmpty()) return
        if (composesInline) inputConnection()?.commitText(display, 1)
        buffer.clear()
        rawDirty = true
    }

    private fun pushComposing() {
        if (!composesInline) return
        inputConnection()?.setComposingText(render(raw), 1)
    }
}
