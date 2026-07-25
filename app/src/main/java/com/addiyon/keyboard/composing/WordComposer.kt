package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import com.addiyon.keyboard.SafeLog

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
    private val inputConnection: () -> InputConnection?,
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
    private val onCommit: (raw: String, display: String) -> Unit = { _, _ -> }
) {

    private val buffer = StringBuilder()
    private var _rawCache: String = ""
    private var rawDirty = true

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
            try {
                inputConnection()?.let {
                    it.setComposingText("", 1)
                    it.finishComposingText()
                }
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "onBackspace OOM")
            } catch (t: Throwable) {
                SafeLog.e(t, "onBackspace")
            }
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
        try {
            inputConnection()?.commitText(committedDisplay, 1)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "commit OOM")
        } catch (t: Throwable) {
            SafeLog.e(t, "commit")
        }
        onCommit(committedRaw, committedDisplay)
        clearBuffer()
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
        try {
            inputConnection()?.finishComposingText()
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "finish OOM")
        } catch (t: Throwable) {
            SafeLog.e(t, "finish")
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
        try {
            inputConnection()?.commitText("$word ", 1)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "commitSuggestion OOM")
        } catch (t: Throwable) {
            SafeLog.e(t, "commitSuggestion")
        }
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
        try {
            inputConnection()?.finishComposingText()
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "abandon OOM")
        } catch (t: Throwable) {
            SafeLog.e(t, "abandon")
        }
        clearBuffer()
    }

    private fun clearBuffer() {
        buffer.clear()
        rawDirty = true
    }

    private fun pushComposing() {
        try {
            inputConnection()?.setComposingText(raw, 1)
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "pushComposing OOM")
        } catch (t: Throwable) {
            SafeLog.e(t, "pushComposing")
        }
    }
}
