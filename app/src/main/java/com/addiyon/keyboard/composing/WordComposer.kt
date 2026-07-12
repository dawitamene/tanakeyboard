package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection

/**
 * Owns the "currently-being-typed" word: a raw key buffer. One instance per
 * language, both composing the SAME thing inline -- the raw buffer itself,
 * underlined in the field's composing region as the user types (Gboard-style
 * pinyin IME: the romanized text is what's visible; a converted reading only
 * lands in the field on commit). They differ in [commitTransform] and
 * [discardOnExit]:
 *
 *   - English: [commitTransform] is the identity (nothing to convert) and
 *     [discardOnExit] = false -- leaving the word finalizes whatever is
 *     showing.
 *   - Amharic: [commitTransform] turns the raw SERA Latin into the fidel
 *     word to commit (the service wires this to the ranked top
 *     transliteration candidate -- see
 *     [com.addiyon.keyboard.AddiyonKeyboardService]), and [discardOnExit] =
 *     true -- the underlined Latin in the field is TENTATIVE: only [commit]
 *     / [commitSuggestion] turn it into real (fidel) text. Leaving the word
 *     any other way (keyboard hidden, cursor tapped away) REMOVES the Latin
 *     from the field instead of finalizing it, so nothing is ever committed
 *     without space / enter / punctuation / a tapped suggestion.
 *     Fidel suggestion readings live only in the suggestion strip while
 *     typing. Backspace removes one Latin character at a time (default
 *     [lastUnitStart]) and the region re-renders.
 *
 * WHY AMHARIC DISCARDS ON EXIT ([discardOnExit])
 *
 * Two failed designs preceded this one. Composing the raw LATIN inline with
 * English's finalize-on-exit semantics meant [finish] stranded "selam" in
 * the field as literal Latin when the input view went away mid-word.
 * Composing the FIDEL inline with finalize-on-exit semantics avoided that but
 * introduced auto-commit: hiding the keyboard or tapping elsewhere silently
 * committed a word the user never accepted (and forced showing the
 * often-wrong greedy reading live, rather than romanized text plus a ranked
 * suggestion strip). Discard-on-exit keeps the good half of each: the raw
 * word is visible in the field while being typed (composing region,
 * underlined), yet the field only ever KEEPS text the user explicitly
 * committed -- and what gets committed is chosen by [commitTransform] at
 * commit time, not baked into what's shown while typing. The one exception is
 * a word adopted from the field by [resume] -- that text was already
 * committed before we lifted it into the region, so exiting restores it
 * (through [commitTransform], i.e. as fidel again) rather than deleting it;
 * see [resumed].
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
    private val inputConnection: () -> InputConnection?,
    /**
     * Buffer -> the form committed into the field: identity for English,
     * the ranked top transliteration reading for Amharic. Invoked fresh at
     * every commit site (never cached) so it always reflects the current
     * buffer -- see the class doc's "stateless whole-buffer" note.
     */
    private val commitTransform: (String) -> String = { it },
    /**
     * What happens to the composing text when the word ends WITHOUT an
     * explicit commit ([finish] / [abandon]). False (English): finalize it
     * in place -- what the user sees stays. True (Amharic): delete it from
     * the field -- the word was tentative, and only space / enter /
     * punctuation / a tapped suggestion may turn it into real text. A word
     * seeded by [resume] is exempt (it was already committed field text
     * before adoption) and is restored instead, through [commitTransform].
     */
    private val discardOnExit: Boolean = false,
    private val lastUnitStart: (String) -> Int = { it.length - 1 },
    /**
     * Invoked with the raw buffer and its committed ([commitTransform])
     * form, right before the buffer clears, whenever committed text lands in
     * the field: always from [commit], and from [finish]/[abandon] for a
     * [resumed] word. Amharic uses this to remember fidel -> raw Latin for
     * words committed this session, so the caret can walk back to one and
     * resume typing it (reverse-transliterating fidel in general isn't
     * reliable -- see WordTrie's class doc -- but a word we just composed
     * ourselves, we already have the raw Latin for for free). English has no
     * use for it (the field already holds the raw text).
     */
    private val onCommit: (raw: String, display: String) -> Unit = { _, _ -> }
) {

    private val buffer = StringBuilder()
    private var _rawCache: String = ""
    private var rawDirty = true

    /**
     * True while the current word was seeded by [resume] -- i.e. it already
     * existed as committed text in the field before we lifted it into the
     * composing region. Such a word must never be DELETED by a
     * [discardOnExit] exit path: the user committed it once already, so
     * [finish]/[abandon] restore it (with any extension typed since)
     * instead. Cleared whenever the buffer clears.
     */
    private var resumed = false

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
     * A character key was pressed. Appends to the buffer and pushes the
     * updated buffer into the composing region verbatim.
     *
     * [char] is what the key produces AFTER shift/case has been applied by
     * the caller -- this class doesn't know about shift state. In Amharic,
     * feeding "H" vs "h" is how you reach ሐ vs ሀ once committed.
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
        if (buffer.isEmpty()) {
            // The user explicitly deleted the whole word, so the region
            // must end up EMPTY in the field (setComposingText("")), not
            // finalized; finishComposingText then closes the empty region's
            // bookkeeping cleanly.
            inputConnection()?.let {
                it.setComposingText("", 1)
                it.finishComposingText()
            }
            resumed = false
        } else {
            pushComposing()
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
    fun commit() {
        if (buffer.isEmpty()) return
        val committedRaw = raw
        val committedDisplay = commitTransform(committedRaw)
        inputConnection()?.commitText(committedDisplay, 1)
        onCommit(committedRaw, committedDisplay)
        clearBuffer()
    }

    /**
     * Finalize (English) or discard (Amharic) the in-progress word, without
     * resolving it through [commitTransform] unless it was [resume]d.
     *
     * Used when the input view is going away (the field is losing us) and the
     * user never explicitly accepted a word (space, enter, or a tapped
     * suggestion).
     *
     * [discardOnExit] = false (English): locks in whatever is CURRENTLY shown
     * in the composing region -- via `finishComposingText`, which finalizes
     * the existing span without replacing it. `commitText` here would
     * duplicate the word: as an input session ends, the framework finalizes
     * the still-active composing region on its own, so an additional
     * `commitText` pasted a second copy (the "text appears twice after
     * exiting the keyboard" bug).
     *
     * [discardOnExit] = true (Amharic): the tentative raw Latin is DELETED
     * from the field (best-effort -- the connection is usually still live in
     * onFinishInputView) so nothing the user never accepted gets committed.
     * Exception: a [resume]d word was already committed text before
     * adoption, so it's re-committed through [commitTransform] like
     * [commit] -- and re-reported through [onCommit] so the caret-resume
     * history stays complete.
     */
    fun finish() {
        if (buffer.isEmpty()) return
        if (discardOnExit && !resumed) {
            inputConnection()?.let {
                it.setComposingText("", 1)
                it.finishComposingText()
            }
        } else if (discardOnExit) {
            // Resumed: was already committed text, so it must land back as
            // committed text (through the transform), not raw Latin.
            val committedRaw = raw
            val committedDisplay = commitTransform(committedRaw)
            inputConnection()?.commitText(committedDisplay, 1)
            onCommit(committedRaw, committedDisplay)
        } else {
            // The framework finalizes the still-active composing region on
            // its own as the session ends, so DON'T commitText here (that
            // pasted a second copy -- the historical "text appears twice
            // after exiting the keyboard" bug); just lock in what's shown.
            inputConnection()?.finishComposingText()
            val committedRaw = raw
            onCommit(committedRaw, commitTransform(committedRaw))
        }
        clearBuffer()
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
        resumed = true
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
        clearBuffer()
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
     * tapped elsewhere in the text). For English, freeze whatever is
     * currently underlined into the field as-is -- we can't keep rewriting a
     * word the user has visibly walked away from. For Amharic
     * ([discardOnExit]) the tentative raw Latin is removed from the field
     * instead: walking away is not an accept gesture, matching the "nothing
     * commits except space or a tapped suggestion" contract -- unless the
     * word was [resume]d from already-committed field text, in which case
     * deleting it would destroy text the user once accepted, so it's
     * re-committed like English (through [commitTransform]).
     *
     * Distinct from [reset], which drops the buffer without touching the
     * field, and from [commit], which is called at natural word boundaries
     * (space/enter/language toggle). This is the "involuntary" exit
     * triggered by external cursor movement.
     */
    fun abandon() {
        if (buffer.isEmpty()) return
        if (discardOnExit && !resumed) {
            inputConnection()?.let {
                it.setComposingText("", 1)
                it.finishComposingText()
            }
        } else {
            val committedRaw = raw
            val committedDisplay = commitTransform(committedRaw)
            inputConnection()?.finishComposingText()
            onCommit(committedRaw, committedDisplay)
        }
        clearBuffer()
    }

    private fun clearBuffer() {
        buffer.clear()
        rawDirty = true
        resumed = false
    }

    private fun pushComposing() {
        inputConnection()?.setComposingText(raw, 1)
    }
}
