package com.addiyon.keyboard.suggestion

/**
 * Recovers the n-gram context (the one or two committed words before the
 * cursor) from the text the editor reports via `getTextBeforeCursor`. Reading
 * the field instead of tracking commits keeps predictions correct across
 * cursor jumps, app switches, and edits to older text, with no service state.
 *
 * Script-neutral: the walk-back algorithm is identical for both keyboards;
 * only what counts as a word character and how a word's surface form is
 * normalized differ. Each language pack supplies those two policies.
 *
 * In both cases only SURFACE normalization happens here; the homoglyph
 * fold / lowercasing that makes a context word match the model's vocab is the
 * model's own job (see [NgramModel.parse]'s `normalize`).
 */
class NgramContext(
    private val isWordChar: (Char) -> Boolean,
    private val normalizeWord: (String) -> String
) {

    data class Context(val prev2: String?, val prev1: String?)

    fun extract(textBeforeCursor: CharSequence?): Context {
        val text = textBeforeCursor ?: return EMPTY
        val mayBeTruncated = text.length >= WINDOW
        var i = text.length - 1

        // The cursor must sit after a separator; touching a word directly
        // means the user is inside/adjacent to it, not after it.
        if (i < 0 || isWordChar(text[i])) return EMPTY
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0 || !isWordChar(text[i])) return EMPTY

        val (prev1, afterPrev1) = readWordBackward(text, i, mayBeTruncated)
            ?: return EMPTY

        i = afterPrev1
        var sawSpace = false
        while (i >= 0 && text[i].isWhitespace()) {
            sawSpace = true
            i--
        }
        if (!sawSpace || i < 0 || !isWordChar(text[i])) {
            return Context(null, prev1)
        }
        val prev2 = readWordBackward(text, i, mayBeTruncated)?.first
        return Context(prev2, prev1)
    }

    /**
     * Reads the word whose last character is at [end], returning the
     * normalized word and the index just before its first character, or
     * null when the word runs into the start of a possibly-truncated window.
     */
    private fun readWordBackward(
        text: CharSequence,
        end: Int,
        mayBeTruncated: Boolean
    ): Pair<String, Int>? {
        var start = end
        while (start >= 0 && isWordChar(text[start])) start--
        if (start < 0 && mayBeTruncated) return null
        val raw = text.subSequence(start + 1, end + 1).toString()
        val word = normalizeWord(raw)
        if (word.isEmpty()) return null
        return word to start
    }

    companion object {
        /** Window passed to `getTextBeforeCursor`. A returned chunk of exactly
         * this length may be truncated mid-word at its start; a word touching
         * index 0 of such a chunk is discarded as unreliable. */
        const val WINDOW = 48

        val EMPTY = Context(null, null)
    }
}
