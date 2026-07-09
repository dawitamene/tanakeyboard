package com.addiyon.keyboard.transliteration

/**
 * Converts a SERA-style Latin string (e.g. "selam") into its Amharic fidel
 * equivalent (e.g. "ሰላም"), using [AmharicTable] as the sole source of
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
 * mid-word backspace (the composer shrinks the romanized buffer and
 * re-renders) and later autocomplete ("selam" as the lookup key even when
 * the field shows ስላም) straightforward.
 *
 * MATCHING RULES:
 *
 * 1. At each position, try to match the longest consonant spelling from
 *    [AmharicTable.consonantsByLength], case-sensitively first. Longest-first
 *    so "sh" beats "s"+"h", "gn" beats "g"+"n", etc. Case-sensitively first
 *    so that the three families where shift genuinely changes the sound --
 *    h/H, t/T, ch/C -- keep taking priority. If nothing matches
 *    case-sensitively, retry the same lookup ignoring case: shift has no
 *    distinct family for the other 20-odd consonants (e.g. "D"), so typing
 *    the shifted key should transliterate exactly like its lowercase form
 *    instead of falling through as literal Latin.
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
 *    bare-vowel spelling from [AmharicTable.bareVowels] instead (again
 *    case-sensitive first, then case-insensitive), resolved against the
 *    family the entry names -- this is what makes a word-initial (or
 *    otherwise unprefixed) vowel like "aster" come out as አስተር rather than
 *    passed through as Latin "a". Unlike consonants, bare vowels DO have a
 *    distinct uppercase family: lowercase a/u/i/o/e resolve to the glottal
 *    ("'") አ series, while shifted A/U/I/O/E resolve to the pharyngeal ("`")
 *    ዐ series (so "Aster" -> ዐስተር). The exact-case-first order is what keeps
 *    those two apart.
 *
 * 4. If neither a consonant nor a bare vowel matched, the character is
 *    passed through -- after a lookup in [AmharicTable.punctuation], which
 *    maps the layout's punctuation keys to their Ethiopic forms ("," -> ፣,
 *    "." -> ።). Everything else (digits, spaces, already-typed fidel,
 *    symbols) passes through unchanged: silently dropping characters would
 *    be far more confusing than showing them literally while the user
 *    figures out the spelling. Note every letter key on the keyboard
 *    reaches SOME family -- SERA's single-letter aliases "x" (ሸ) and "c"
 *    (ቸ) are real entries in [AmharicTable.families] -- so no letter falls
 *    through as literal Latin.
 *
 * CASE FOLDING:
 *
 * The caller feeds the string with case already resolved by shift state.
 * Several spellings are meant to stay case-distinct: consonants "H"/"h",
 * "T"/"t", "C"/"ch" (ሐ vs ሀ, ጠ vs ተ, ጨ vs ቸ), "S"/"s" (ሠ vs ሰ), "Ts"/"ts"
 * (ፀ vs ጸ), and the bare vowels A/U/I/O/E vs a/u/i/o/e (pharyngeal ዐ vs
 * glottal አ). Every other consonant has no such distinction, so its
 * uppercase form falls back to case-insensitive matching (rules 1 and 3)
 * rather than being passed through as literal Latin.
 */
object Transliterator {

    private val ALTERNATE_START_CHARS = setOf(
        'h', 'H', 's', 'S', 't', 'T', 'k',
        'a', 'e', 'i', 'o', 'u'
    )

    fun transliterate(latin: String): String =
        build(latin, AmharicTable.consonantsByLength, alternateLevel = 0)

    /**
     * [alternateLevel] selects which reading to build: 0 is the greedy/primary
     * reading (no alternates), and 1..[AmharicTable.maxAlternateLevel] flip
     * every consonant to its Nth alternate family (and, at level 1, the bare
     * vowels to their pharyngeal<->glottal counterpart). Units with no
     * alternate at that level render as themselves, so higher levels dedupe
     * away for buffers that don't have that many alternates.
     */
    private fun build(
        latin: String,
        consonants: List<String>,
        alternateLevel: Int
    ): String {
        val out = StringBuilder(latin.length)
        forEachUnit(latin, consonants, alternateLevel) { _, text -> out.append(text) }
        return out.toString()
    }

    /**
     * The distinct plausible fidel readings of an ambiguous [latin] buffer,
     * greedy reading first, for the suggestion strip. There are two axes of
     * ambiguity a keystroke sequence can't resolve on its own:
     *
     *   - DIGRAPH: "sh" is one consonant (ሽ) or two (ስ+ህ). The greedy reading
     *     combines; [transliterateSplit] separates.
     *   - FORM: families with alternate readings (h/H -> ሀ/ሐ/ኀ, s/S, ts/Ts,
     *     k -> ከ/ቀ, and the glottal-vs-pharyngeal bare vowels) -- typing "s"
     *     could mean ስ or ሠ, "h" could mean ህ/ሕ/ኅ. Each alternate LEVEL in
     *     [AmharicTable.consonantAlternates] gets its own reading, so every
     *     form is offered without needing shift.
     *
     * Returns up to [limit] of: greedy, then one reading per alternate level
     * (1..[AmharicTable.maxAlternateLevel]), then the digraph-split reading --
     * deduped in that order. Collapses to a single entry when the buffer has
     * no ambiguity (e.g. "r" -> [ር]).
     */
    fun readings(latin: String, limit: Int = 6): List<String> {
        if (latin.isEmpty()) return emptyList()
        val greedy = build(latin, AmharicTable.consonantsByLength, alternateLevel = 0)
        if (!bufferHasAlternates(latin)) {
            return listOf(greedy)
        }
        val altReadings = (1..AmharicTable.maxAlternateLevel).map { level ->
            build(latin, AmharicTable.consonantsByLength, alternateLevel = level)
        }
        val split = build(latin, AmharicTable.singleCharConsonants, alternateLevel = 0)
        return (listOf(greedy) + altReadings + split).distinct().take(limit)
    }

    private fun bufferHasAlternates(latin: String): Boolean {
        for (ch in latin) {
            if (ch in ALTERNATE_START_CHARS) return true
        }
        return bufferHasAlternatesFull(latin)
    }

    private fun bufferHasAlternatesFull(latin: String): Boolean {
        var i = 0
        while (i < latin.length) {
            val consonant = AmharicTable.consonantsByLength
                .firstOrNull { latin.startsWith(it, i) }
                ?: AmharicTable.consonantsByLength
                    .firstOrNull { latin.startsWith(it, i, ignoreCase = true) }
            if (consonant != null) {
                if (consonant in AmharicTable.consonantAlternates) return true
                i += consonant.length
                val vowel = AmharicTable.vowels
                    .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i, ignoreCase = true) }
                if (vowel != null) i += vowel.first.length
            } else {
                val bareVowel = AmharicTable.bareVowels
                    .firstOrNull { latin.startsWith(it.spelling, i) }
                    ?: AmharicTable.bareVowels
                        .firstOrNull { latin.startsWith(it.spelling, i, ignoreCase = true) }
                if (bareVowel != null) return true
                i++
            }
        }
        return false
    }

    /**
     * The "separated" reading of [latin]: identical to [transliterate] except
     * that digraphs are never combined -- only single-letter consonant
     * spellings are matched. So "shn" comes out ስህን (s+h+n) instead of the
     * greedy ሽን, and "shtet" comes out ስህተት instead of ሽተት.
     *
     * This is the OTHER interpretation of an inherently ambiguous buffer:
     * "sh" could be one consonant (ሽ) or two (ስ+ህ), and only the user knows
     * which. The service offers both readings side by side in the suggestion
     * bar (see AddiyonKeyboardService.updateSuggestions) so the ambiguity is
     * resolved by a tap rather than being silently forced one way.
     *
     * Equal to [transliterate] whenever the buffer contains no digraph
     * (nothing to separate), which is how the caller detects "unambiguous,
     * nothing extra to offer".
     */
    fun transliterateSplit(latin: String): String =
        build(latin, AmharicTable.singleCharConsonants, alternateLevel = 0)

    /**
     * Walks [latin] left to right emitting one (start index, output text)
     * pair per unit -- either a matched consonant(+vowel) syllable or a
     * single passed-through character. Used by [build], which concatenates
     * the emitted text. The per-unit start index is exposed on the callback
     * for any caller that needs unit boundaries rather than the text.
     */
    private inline fun forEachUnit(
        latin: String,
        consonants: List<String>,
        alternateLevel: Int,
        emit: (start: Int, text: String) -> Unit
    ) {
        var i = 0

        while (i < latin.length) {
            val start = i

            // 1. Longest consonant at position i -- exact case first (so H/T/C
            // keep taking priority over their lowercase counterparts), then a
            // case-insensitive retry for every other consonant, which has no
            // distinct uppercase family for shift to select. [consonants] is
            // the full set for the greedy reading, or only the single-letter
            // spellings for the "separated" reading (transliterateSplit).
            val consonant = consonants
                .firstOrNull { latin.startsWith(it, i) }
                ?: consonants
                    .firstOrNull { latin.startsWith(it, i, ignoreCase = true) }

            if (consonant == null) {
                // No consonant matched -> try a bare (unprefixed) vowel,
                // resolved against the glottal family, before giving up and
                // passing the character through as-is. Same exact-then-
                // case-insensitive order as the consonant lookup above.
                val bareVowel = AmharicTable.bareVowels
                    .firstOrNull { latin.startsWith(it.spelling, i) }
                    ?: AmharicTable.bareVowels
                        .firstOrNull { latin.startsWith(it.spelling, i, ignoreCase = true) }

                if (bareVowel != null) {
                    // A bare vowel has exactly one alternate: the other
                    // standalone series (glottal አ "'" <-> pharyngeal ዐ "`",
                    // same order index), offered at level 1 -- so "a" surfaces
                    // both አ and ዐ. Higher levels have no further alternate, so
                    // they render the primary family and dedupe away.
                    val familyKey = if (alternateLevel == 1) {
                        when (bareVowel.familyKey) {
                            "'" -> "`"
                            "`" -> "'"
                            else -> bareVowel.familyKey
                        }
                    } else {
                        bareVowel.familyKey
                    }
                    val family = AmharicTable.families.getValue(familyKey)
                    emit(start, family.forms[bareVowel.index].toString())
                    i += bareVowel.spelling.length
                    continue
                }

                val char = latin[i]
                emit(start, (AmharicTable.punctuation[char] ?: char).toString())
                i++
                continue
            }

            i += consonant.length
            // At alternate level N >= 1, swap to this consonant's Nth alternate
            // family where one exists (h -> ሐ then ኀ, s -> ሠ, k -> ቀ, ...),
            // leaving the vowel choice untouched -- so "s" surfaces both ሰ and
            // ሠ, "h" surfaces ሀ/ሐ/ኀ. A consonant with no alternate at that
            // level resolves to its own family, which is what makes the higher
            // readings dedupe away for unambiguous buffers.
            val family = if (alternateLevel >= 1) {
                AmharicTable.consonantAlternates[consonant]?.getOrNull(alternateLevel - 1)
                    ?: AmharicTable.families.getValue(consonant)
            } else {
                AmharicTable.families.getValue(consonant)
            }

            // 2. Longest vowel at the new position (may be none). No distinct
            // family hinges on a vowel's case, so this is a single
            // case-insensitive lookup (unlike rules 1 and 3, which check
            // case-sensitively first to protect H/T/C).
            val vowel = AmharicTable.vowels
                .firstOrNull { (spelling, _) -> latin.startsWith(spelling, i, ignoreCase = true) }

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