package com.addiyon.keyboard.voice

/**
 * Pure reconciliation between the live dictation utterance and the target
 * field's composing region (no Android dependencies, so it's JVM-testable).
 *
 * The dictation model mirrors Gboard: while the recognizer refines an
 * utterance, its latest partial lives in the field's composing region
 * (pushed via setComposingText, so each refinement atomically replaces the
 * previous one); when the utterance ends, the final text replaces the region
 * via commitText. Because the text is always IN the field, any interruption
 * (pause, exit, keypress, cursor move, view teardown) just needs
 * finishComposingText() to lock in whatever the user last saw -- recognized
 * speech can never silently disappear.
 *
 * Spacing is decided once per utterance: the character before the cursor is
 * captured at the utterance's FIRST partial (the region opens right after
 * it, so it stays valid for the whole utterance) and a leading space is
 * prepended when dictation continues after existing text. Committed text
 * carries no trailing space, so typing "." right after dictating "hello"
 * yields "hello." -- the next utterance brings its own separator instead.
 */
class VoiceComposer {

    private var composing = false
    private var charBeforeUtterance: Char? = null
    private var lastPartial = ""
    private var lastPushed: String? = null

    /** True while an utterance is live in the field's composing region. */
    val isComposing: Boolean
        get() = composing

    /**
     * Folds a partial recognition result into the current utterance and
     * returns the exact string to push via setComposingText, or null when
     * there's nothing to push (blank partial, or identical to what's already
     * showing). [charBeforeCursor] is only read when this partial OPENS a
     * new utterance; callers may pass null on subsequent partials to skip
     * the InputConnection round-trip.
     */
    fun updatePartial(raw: String, charBeforeCursor: Char?): String? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        if (!composing) {
            composing = true
            charBeforeUtterance = charBeforeCursor
        }
        lastPartial = normalized
        val text = withLeadingSpaceIfNeeded(normalized, charBeforeUtterance)
        if (text == lastPushed) return null
        lastPushed = text
        return text
    }

    /**
     * Ends the current utterance and returns what to commit, or null when
     * there's nothing at all to commit. A blank/null [raw] falls back to the
     * last partial (the recognizer sometimes ends a session without a usable
     * final -- the user already saw the partial, so that's what gets kept).
     * [charBeforeCursor] is only used for a final that arrives without any
     * preceding partials; a live utterance keeps the context captured at its
     * first partial. Resets to idle either way.
     */
    fun finalize(raw: String?, charBeforeCursor: Char?): FinalCommit? {
        val charBefore = if (composing) charBeforeUtterance else charBeforeCursor
        val normalized = normalize(raw.orEmpty()).ifEmpty { lastPartial }
        reset()
        if (normalized.isEmpty()) return null
        val deleteSpaceBefore = startsWithStandalonePunctuation(normalized) &&
            charBefore?.isWhitespace() == true
        return FinalCommit(withLeadingSpaceIfNeeded(normalized, charBefore), deleteSpaceBefore)
    }

    /**
     * The composing region was closed from outside (finishComposingText on
     * pause/exit/keypress/cursor move) -- whatever was showing is already
     * locked into the field, so just forget the utterance.
     */
    fun onFinalizedExternally() = reset()

    fun reset() {
        composing = false
        charBeforeUtterance = null
        lastPartial = ""
        lastPushed = null
    }

    /**
     * [text] is ready to commit as-is (leading space included when needed).
     * [deleteSpaceBefore] flags a spoken standalone punctuation mark landing
     * after whitespace -- the caller should delete that space first so
     * "hello " + "," becomes "hello," not "hello ,".
     */
    data class FinalCommit(val text: String, val deleteSpaceBefore: Boolean)

    private fun withLeadingSpaceIfNeeded(text: String, charBefore: Char?): String {
        val needsSpace = charBefore != null && !charBefore.isWhitespace() &&
            !startsWithStandalonePunctuation(text)
        return if (needsSpace) " $text" else text
    }

    companion object {
        fun normalize(text: String): String =
            text.trim().replace(whitespaceRuns, " ")

        fun startsWithStandalonePunctuation(text: String): Boolean =
            text.trimStart().firstOrNull()?.let { it in standalonePunctuation } == true

        private val whitespaceRuns = Regex("\\s+")

        // Marks that attach to the preceding word rather than standing alone;
        // includes the Ethiopic punctuation the Amharic layout produces.
        private val standalonePunctuation = setOf(
            '.', ',', '!', '?', ';', ':', '።', '፣', '፤', '፥', '፦', '፧'
        )
    }
}
