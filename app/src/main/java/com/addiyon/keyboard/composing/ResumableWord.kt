package com.addiyon.keyboard.composing

/**
 * Extracts the word ENDING at the caret, for caret-at-word-end resume (see
 * [WordAdoption]). Only the end rule exists: re-opening a word around an
 * interior caret is where the absolute-offset design's cursor bugs lived, and
 * typing mid-word inserts plain text instead (what every mainstream IME does).
 *
 * Script-specific on purpose: the English pipeline must never adopt fidel
 * because its dictionary and case handling are Latin.
 * A word that fills the ENTIRE lookbehind window is rejected too -- its start
 * lies beyond what was read, and adopting a fragment would compose (and, in
 * Amharic, visibly rewrite) only the tail of a longer word.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable without an emulator.
 */
internal object ResumableWord {

    /**
     * How much text before the caret the service reads: comfortably longer
     * than any word worth resuming, small enough for the per-cursor-move
     * getTextBeforeCursor round-trip to stay cheap.
     */
    const val LOOKBEHIND = 48
    const val LOOKAHEAD = LOOKBEHIND

    /**
     * Word characters for the English pipeline: any letter OUTSIDE the
     * Ethiopic block, plus apostrophe (contractions -- mirrors
     * isComposingWordCharacter, minus the SERA backtick that only means
     * anything to the Amharic pipeline).
     */
    private fun isLatinWordChar(char: Char): Boolean =
        (char.isLetter() && !isEthiopic(char)) || char == '\''

    private fun isEthiopic(char: Char): Boolean = char in 'ሀ'..'፿'

    private fun isAmharicWordChar(char: Char): Boolean =
        char.isLetter() && isEthiopic(char)

    private fun isEmailWordChar(char: Char): Boolean =
        char.isLetter() ||
            char == '\'' ||
            char == '`' ||
            char == '@' ||
            char == '.' ||
            char in '0'..'9'

    /**
     * The word that ends EXACTLY at the caret, or null if the caret is not at a
     * word end.
     *
     * [after] must therefore be empty (end of field) or start with a non-word
     * character. A run that fills the whole lookbehind window is rejected as well:
     * its start lies beyond what was read, and adopting a fragment would compose
     * only the tail of a longer word.
     */
    fun latinWordEndingAtCursor(before: CharSequence, after: CharSequence): String? =
        wordEndingAtCursor(before, after, ::isLatinWordChar)

    /** Fidel counterpart of [latinWordEndingAtCursor]. */
    fun amharicWordEndingAtCursor(before: CharSequence, after: CharSequence): String? =
        wordEndingAtCursor(before, after, ::isAmharicWordChar)

    /** Email-field counterpart of [latinWordEndingAtCursor]: the whole local-part@domain.tld token. */
    fun emailWordEndingAtCursor(before: CharSequence, after: CharSequence): String? =
        wordEndingAtCursor(before, after, ::isEmailWordChar)

    private fun wordEndingAtCursor(
        before: CharSequence,
        after: CharSequence,
        isWordChar: (Char) -> Boolean
    ): String? {
        if (after.isNotEmpty() && isWordChar(after[0])) return null
        return trailingRun(before, isWordChar)
    }

    private fun trailingRun(
        before: CharSequence,
        isWordChar: (Char) -> Boolean
    ): String? {
        var start = before.length
        while (start > 0 && isWordChar(before[start - 1])) start--
        if (start == before.length) return null
        // The run reaches the front of a full lookbehind window: the word may
        // extend further left than we can see -- never adopt a fragment.
        if (start == 0 && before.length >= LOOKBEHIND) return null
        return before.subSequence(start, before.length).toString()
    }
}
