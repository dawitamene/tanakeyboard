package com.addiyon.keyboard.suggestion

import java.text.Normalizer

/**
 * Recovers the n-gram context (the one or two committed words before the
 * cursor) from the text the editor reports via `getTextBeforeCursor`. Reading
 * the field instead of tracking commits keeps predictions correct across
 * cursor jumps, app switches, and edits to older text, with no service state.
 *
 * The rules mirror the build-time tokenizer in `tools/build_ngrams.py`:
 * a word is a run of Ethiopic syllables (gemination marks stripped, NFC),
 * and any other character is a boundary. Whitespace merely separates words;
 * a hard boundary (punctuation, digit, quote — including ። and ፣) kills the
 * context on its far side, so text ending "... ። " predicts nothing
 * (sentence start) and "ቡና ፣ ቤት " yields prev1 = ቤት with no prev2.
 */
object NgramContext {
    /** Window passed to `getTextBeforeCursor`. A returned chunk of exactly
     * this length may be truncated mid-word at its start; a word touching
     * index 0 of such a chunk is discarded as unreliable. */
    const val WINDOW = 48

    data class Context(val prev2: String?, val prev1: String?)

    val EMPTY = Context(null, null)

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
        val word = GEMINATION.replace(Normalizer.normalize(raw, Normalizer.Form.NFC), "")
        if (word.isEmpty()) return null
        return word to start
    }

    // Ethiopic syllable ranges, matching WORD_RE in tools/build_ngrams.py,
    // plus the combining gemination marks (word-internal, stripped after).
    private fun isWordChar(c: Char): Boolean =
        c in 'ሀ'..'ፚ' || c in '፝'..'፟' ||
            c in 'ᎀ'..'ᎏ' || c in 'ⶀ'..'ⷞ' ||
            c in 'ꬁ'..'ꬮ'

    private val GEMINATION = Regex("[፝-፟]")
}
