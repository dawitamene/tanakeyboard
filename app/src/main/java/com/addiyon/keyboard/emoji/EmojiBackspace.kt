package com.addiyon.keyboard.emoji

/**
 * Computes how many chars a single backspace should remove from the end of
 * the text before the cursor, so one press deletes a whole emoji instead of
 * corrupting it. `deleteSurroundingText(1, 0)` removes one UTF-16 unit,
 * which leaves half a surrogate pair behind for any non-BMP emoji and
 * beheads ZWJ sequences one joint at a time -- with an emoji picker in the
 * keyboard, the character before the cursor is routinely an emoji, so the
 * field-deletion path of `onDelete` asks this first.
 *
 * Handles the emoji sequence shapes that actually occur (per UTS #51):
 * skin-tone modifiers, variation selectors, ZWJ chains (families,
 * multi-person combos), keycaps and flags (regional-indicator pairs). For
 * anything else it returns the last code point's length -- one char for BMP
 * text, so ordinary typing behaves exactly as before (combining accents are
 * still deleted mark-by-mark, matching the old behavior).
 */
object EmojiBackspace {

    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3

    private fun isToneModifier(cp: Int) = cp in 0x1F3FB..0x1F3FF
    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF
    private fun isVariationSelector(cp: Int) = cp == VS15 || cp == VS16

    /**
     * Length in chars of the emoji cluster (or single code point) ending at
     * the end of [text]. 0 for empty text; never more than [text].length.
     * Callers should clamp the result to at least 1 before deleting.
     */
    fun lastClusterLength(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        var start = text.length

        fun stepBack(): Int {
            val cp = Character.codePointBefore(text, start)
            start -= Character.charCount(cp)
            return cp
        }

        var cp = stepBack()

        // Keycap sequence: <digit/#/*> [VS16] U+20E3.
        if (cp == KEYCAP) {
            if (start > 0 && Character.codePointBefore(text, start) == VS16) stepBack()
            if (start > 0) stepBack()
            return text.length - start
        }

        // Flags are PAIRS of regional indicators, clustered left-to-right --
        // in a run of RIs, the last cluster is a pair when the run is even,
        // a lone dangling RI when it's odd.
        if (isRegionalIndicator(cp)) {
            var run = 1
            var i = start
            while (i > 0) {
                val prev = Character.codePointBefore(text, i)
                if (!isRegionalIndicator(prev)) break
                run++
                i -= Character.charCount(prev)
            }
            if (run % 2 == 0) stepBack()
            return text.length - start
        }

        // A lone ZWJ (malformed text): just the ZWJ itself.
        if (cp == ZWJ) return text.length - start

        // General emoji sequence, scanned backward:
        //   element = base [tone] [VS], chain = element (ZWJ element)*.
        while (true) {
            if (isToneModifier(cp) || isVariationSelector(cp)) {
                if (start == 0) break
                cp = stepBack()
                continue
            }
            // cp is an element's base; absorb a preceding ZWJ link, if any.
            if (start > 0 && Character.codePointBefore(text, start) == ZWJ) {
                stepBack() // the ZWJ
                if (start == 0) break
                cp = stepBack() // previous element's trailing code point
                continue
            }
            break
        }
        return text.length - start
    }
}
