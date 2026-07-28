package com.addiyon.keyboard.composing

/**
 * Extracts a word at the caret for cursor-aware resume (see
 * AddiyonKeyboardService.maybeResumeWordAtCursor). English and email text can
 * be adopted from both sides of the caret.
 *
 * Script-specific on purpose: the English composer must never adopt fidel
 * because its dictionary and case handling are Latin.
 * A word that fills the ENTIRE lookbehind window is rejected too -- its start
 * lies beyond what was read, and adopting a fragment would compose (and, in
 * Amharic, visibly rewrite) only the tail of a longer word.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable without an emulator,
 * same reasoning as [WordComposer].
 */
internal object ResumableWord {

    /**
     * How much text before the caret the service reads: comfortably longer
     * than any word worth resuming, small enough for the per-cursor-move
     * getTextBeforeCursor round-trip to stay cheap.
     */
    const val LOOKBEHIND = 48
    const val LOOKAHEAD = LOOKBEHIND

    data class AtCursor(
        val word: String,
        val cursorOffset: Int
    )

    /**
     * Word characters for the English composer: any letter OUTSIDE the
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

    fun trailingLatinWord(before: CharSequence): String? =
        trailingRun(before, ::isLatinWordChar)

    fun latinWordAtCursor(
        before: CharSequence,
        after: CharSequence
    ): AtCursor? =
        runAtCursor(before, after, ::isLatinWordChar)

    fun emailWordAtCursor(
        before: CharSequence,
        after: CharSequence
    ): AtCursor? =
        runAtCursor(before, after, ::isEmailWordChar)

    fun amharicWordAfterBackspace(
        before: CharSequence,
        after: CharSequence,
        deletedChars: Int
    ): AtCursor? {
        if (deletedChars !in 1..before.length) return null
        val deletedStart = before.length - deletedChars
        if (
            before.subSequence(deletedStart, before.length)
                .any { !isAmharicWordChar(it) }
        ) {
            return null
        }
        return runAtCursor(
            before = before.subSequence(0, deletedStart),
            after = after,
            isWordChar = ::isAmharicWordChar,
            lookbehindWasFull = before.length >= LOOKBEHIND
        )
    }

    private fun runAtCursor(
        before: CharSequence,
        after: CharSequence,
        isWordChar: (Char) -> Boolean,
        lookbehindWasFull: Boolean = before.length >= LOOKBEHIND
    ): AtCursor? {
        var start = before.length
        while (start > 0 && isWordChar(before[start - 1])) start--

        var end = 0
        while (end < after.length && isWordChar(after[end])) end++

        if (start == before.length && end == 0) return null
        if (start == 0 && lookbehindWasFull) return null
        if (end == after.length && after.length >= LOOKAHEAD) return null

        val left = before.subSequence(start, before.length).toString()
        val right = after.subSequence(0, end).toString()
        return AtCursor(
            word = left + right,
            cursorOffset = left.length
        )
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
