package com.addiyon.keyboard.composing

/**
 * Extracts the word the caret just landed at the end of, for cursor-aware
 * resume (see AddiyonKeyboardService.maybeResumeWordAtCursor): given the text
 * BEFORE the caret, returns the trailing run of word characters -- the word
 * the composer should adopt via [WordComposer.resume] -- or null when the
 * caret isn't at a resumable word end.
 *
 * Script-specific on purpose: the English composer must never adopt fidel
 * (its dictionary and case handling are Latin), and the Amharic composer only
 * ever adopts fidel (the field never holds committed Latin in Amharic mode).
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

    /**
     * Word characters for the English composer: any letter OUTSIDE the
     * Ethiopic block, plus apostrophe (contractions -- mirrors
     * isComposingWordCharacter, minus the SERA backtick that only means
     * anything to the Amharic pipeline).
     */
    private fun isLatinWordChar(char: Char): Boolean =
        (char.isLetter() && !isEthiopic(char)) || char == '\''

    /**
     * Word characters for the Amharic composer: the Ethiopic syllable range.
     * Deliberately ends at ፚ (U+135A) so the Ethiopic punctuation (፣ ። ...)
     * and numerals that follow it in the block are word BOUNDARIES, exactly
     * as they are while typing.
     */
    private fun isEthiopicSyllable(char: Char): Boolean = char in 'ሀ'..'ፚ'

    private fun isEthiopic(char: Char): Boolean = char in 'ሀ'..'፿'

    fun trailingLatinWord(before: CharSequence): String? =
        trailingRun(before, ::isLatinWordChar)

    fun trailingEthiopicWord(before: CharSequence): String? =
        trailingRun(before, ::isEthiopicSyllable)

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
