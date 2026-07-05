package com.addiyon.tanakeyboard.transliteration

/**
 * Converts a SERA-style Latin string (e.g. "selam") into its Amharic fidel
 * equivalent (e.g. "ስላም"), using [AmharicTable] as the sole source of
 * mappings.
 *
 * DESIGN: stateless, whole-buffer.
 *
 * This function re-transliterates the entire input every call. It does NOT
 * try to incrementally patch a previous output as new characters arrive
 * ("we had ስ, now 'h' came in, swap to ሽ..."). That approach sounds
 * cheaper but creates a whole class of desync bugs -- backspace, cursor
 * jumps, mid-word edits, and suggestion replacements all become traps.
 *
 * Re-deriving from the Latin source of truth on every keystroke is trivial
 * at word length (< 1μs for typical inputs) and means the romanized buffer
 * is always the single source of truth. That property is what makes both
 * mid-word backspace (deleting exactly the Latin span behind the last
 * rendered fidel character, via [lastUnitStart]) and later autocomplete
 * ("selam" as the lookup key even when the field shows ስላም) straightforward.
 *
 * MATCHING RULES:
 *
 * 1. At each position, try to match the longest consonant spelling from
 *    [AmharicTable.consonantsByLength]. Longest-first so "sh" beats "s"+"h",
 *    "gn" beats "g"+"n", etc.
 *
 * 2. If a consonant matched, try to match the longest following vowel
 *    from [AmharicTable.vowels]. Longest-first so "ie" beats "i", "ua"
 *    beats "u".
 *
 *    - No vowel after the consonant  -> emit the bare (6th-order) form.
 *    - Vowel matched                  -> emit forms[index].
 *    - "ua" matched on a family with
 *      no labialized form defined     -> emit bare form + recursively
 *                                         transliterate "ua" (which resolves
 *                                         via the ' family: "ua" -> እ + ኡ ->
 *                                         still not perfect, but at least
 *                                         predictable and non-lossy). Families
 *                                         with a real ua form (l, m, s, sh, r,
 *                                         q, b, t, ch, n, gn, k, z, d, j, g,
 *                                         T, C, f) hit the fast path.
 *
 * 3. If no consonant matched at the current position, try the longest
 *    bare-vowel spelling from [AmharicTable.bareVowels] instead, resolved
 *    against the glottal ("'") family -- this is what makes a word-initial
 *    (or otherwise unprefixed) vowel like "aster" come out as አስተር rather
 *    than passed through as Latin "a".
 *
 * 4. If neither a consonant nor a bare vowel matched, the character is
 *    passed through unchanged. This covers digits, punctuation, spaces,
 *    already-typed fidel, and any Latin letter outside the scheme (e.g.
 *    plain "x" -- your scheme has no x family). Passing through is
 *    deliberate: silently dropping characters would be far more confusing
 *    than showing them literally while the user figures out the spelling.
 *
 * WHAT THIS FUNCTION DOES NOT DO (yet):
 *
 * - Case folding. The caller feeds the string with case already resolved
 *   by shift state (see step 6). "H" and "h" are meant to be distinct
 *   inputs mapping to distinct families (ሐ vs ሀ) and are treated as such.
 */
object Transliterator {

    fun transliterate(latin: String): String {
        val out = StringBuilder(latin.length)
        forEachUnit(latin) { _, text -> out.append(text) }
        return out.toString()
    }

    /**
     * The Latin index where the last unit (one consonant+vowel syllable, or
     * one passed-through character) begins -- i.e. how far a single
     * backspace should truncate the buffer to delete exactly the last
     * rendered fidel character. 0 if [latin] is empty.
     */
    fun lastUnitStart(latin: String): Int {
        var lastStart = 0
        forEachUnit(latin) { start, _ -> lastStart = start }
        return lastStart
    }

    /**
     * Walks [latin] left to right emitting one (start index, output text)
     * pair per unit -- either a matched consonant(+vowel) syllable or a
     * single passed-through character. Shared by [transliterate], which
     * concatenates the text, and [lastUnitStart], which only needs the
     * boundaries. Keeping one walk avoids the two ever disagreeing about
     * where a unit begins.
     */
    private inline fun forEachUnit(latin: String, emit: (start: Int, text: String) -> Unit) {
        var i = 0

        while (i < latin.length) {
            val start = i

            // 1. Longest consonant at position i.
            val consonant = AmharicTable.consonantsByLength
                .firstOrNull { latin.startsWith(it, i) }

            if (consonant == null) {
                // No consonant matched -> try a bare (unprefixed) vowel,
                // resolved against the glottal family, before giving up and
                // passing the character through as-is.
                val bareVowel = AmharicTable.bareVowels
                    .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i) }

                if (bareVowel != null) {
                    val (spelling, index) = bareVowel
                    emit(start, AmharicTable.families.getValue("'").forms[index].toString())
                    i += spelling.length
                    continue
                }

                emit(start, latin[i].toString())
                i++
                continue
            }

            i += consonant.length
            val family = AmharicTable.families.getValue(consonant)

            // 2. Longest vowel at the new position (may be none).
            val vowel = AmharicTable.vowels
                .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i) }

            if (vowel == null) {
                // Bare consonant -> 6th order.
                emit(start, family.bare.toString())
                continue
            }

            val (spelling, index) = vowel
            i += spelling.length

            if (index == AmharicTable.UA_INDEX) {
                // Labialized "ua". Use the dedicated glyph if the family
                // defines one; otherwise fall back to bare + transliterated
                // "ua" so nothing is silently dropped.
                val ua = family.ua
                val text = if (ua != null) {
                    ua.toString()
                } else {
                    family.bare.toString() + transliterate("ua")
                }
                emit(start, text)
            } else {
                emit(start, family.forms[index].toString())
            }
        }
    }
}