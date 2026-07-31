package com.addiyon.keyboard.composing

import com.addiyon.keyboard.EditorGateway

/**
 * Re-opens an already-committed word for editing when the caret is at its end,
 * so continuing to type extends THAT word and the suggestion strip answers it —
 * without ever naming an absolute document position.
 *
 * HOW, AND WHY NOT setComposingRegion
 *
 * The obvious API for "make this existing text the composing region" is
 * `setComposingRegion(start, end)`. It takes absolute offsets, which the IME can
 * only obtain by reading the editor and trusting what it says. Compose text
 * fields report `getSurroundingText().offset == -1`; naive `getExtractedText`
 * implementations report `startOffset = 0` for a window that does not start at 0.
 * Trusting either lands the region on the wrong range, and `setComposingText`
 * then REPLACES that wrong range wholesale — which is how a composing region can
 * swallow a space or overwrite a neighbouring word. Distrusting it (the current
 * code's cross-checks) instead makes the whole feature silently do nothing in
 * those editors.
 *
 * This does the same job with two cursor-relative calls in one batch edit:
 *
 *     deleteSurroundingText(word.length, 0)   // remove the committed word
 *     setComposingText(word, 1)               // put it back, now composing
 *
 * Both are anchored to the caret, which the editor owns, so there is no position
 * to get wrong and no editor capability to depend on. The field ends up holding
 * exactly the characters it started with; only the composing span is new. If the
 * second call is refused, the first is undone by the same batch's rollback in
 * well-behaved editors and, at worst, re-inserted by [restore].
 */
internal object WordAdoption {

    /** Where the caret sits relative to the surrounding words. */
    sealed interface CaretContext {
        /** A word ends exactly here and may be re-opened for editing. */
        data class AtWordEnd(val word: String) : CaretContext

        /**
         * A word character follows the caret: the caret is inside a word, or
         * immediately before one. Typing here inserts plain text and starts no
         * composition — composing would put a region in the middle of somebody
         * else's word, which is the interior-caret case the offset-based design
         * tried to support and could not get right.
         */
        data object InWord : CaretContext

        /** Open ground: after a space, at the start of an empty field. Compose freely. */
        data object Free : CaretContext
    }

    /**
     * Read once, decide once. [wordEndingAtCursor] applies the script's
     * caret-at-word-end rule; [isWordCharacter] classifies the character that
     * follows the caret.
     */
    fun inspect(
        editor: EditorGateway,
        isWordCharacter: (Char) -> Boolean,
        wordEndingAtCursor: (before: String, after: String) -> String?
    ): CaretContext {
        val before = editor.textBeforeCursor(ResumableWord.LOOKBEHIND, optional = false)?.value
            ?: return CaretContext.Free
        // One character is all either rule needs, which keeps the per-keystroke
        // read cheap.
        val after = editor.textAfterCursor(1, optional = false)?.value ?: return CaretContext.Free
        if (after.isNotEmpty() && isWordCharacter(after[0])) return CaretContext.InWord
        val word = wordEndingAtCursor(before, after) ?: return CaretContext.Free
        return CaretContext.AtWordEnd(word)
    }

    /**
     * Perform the swap that turns [word] — which must be the text immediately
     * before the caret — into the composing region. Returns whether it took.
     */
    fun adopt(editor: EditorGateway, word: String): Boolean {
        if (word.isEmpty()) return false
        if (editor.recomposeBeforeCursor(word.length, word)) return true
        restore(editor, word)
        return false
    }

    /**
     * Best-effort repair when the compose half of the swap was refused: put the
     * characters back as committed text. Adoption is an optimisation; losing the
     * user's word to it would be far worse than not adopting.
     */
    private fun restore(editor: EditorGateway, word: String) {
        if (editor.textBeforeCursor(word.length, optional = false)?.value != word) {
            editor.commitText(word)
        }
    }
}

/**
 * Remembers, for this input session, the raw Latin each committed fidel word was
 * composed from.
 *
 * Amharic's composing buffer holds SERA Latin while the field holds fidel, so
 * re-opening a committed fidel word needs the reverse mapping. Deriving it is not
 * reliable — several Latin spellings produce the same fidel, and the homophone
 * folding in [com.addiyon.keyboard.transliteration.EthiopicNormalizer] makes more
 * of them collide — but a word this keyboard composed itself came with its raw
 * Latin for free. Anything not in here is simply not adopted.
 *
 * Bounded and session-scoped: cleared whenever the input session ends, and capped
 * so a long typing session cannot grow it without limit.
 */
internal class ResumedWordMemory(private val capacity: Int = 64) {

    private val byFidel = object : LinkedHashMap<String, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > capacity
    }

    fun remember(fidel: String, rawLatin: String) {
        if (fidel.isEmpty() || rawLatin.isEmpty()) return
        byFidel[fidel] = rawLatin
    }

    fun rawLatinFor(fidel: String): String? = byFidel[fidel]

    fun clear() {
        byFidel.clear()
    }
}
