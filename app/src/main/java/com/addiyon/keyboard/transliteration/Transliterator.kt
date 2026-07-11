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

    data class CandidateReading(val text: String, val isQuirk: Boolean)

    fun transliterate(latin: String): String {
        val out = StringBuilder(latin.length)
        forEachGreedyUnit(latin) { text -> out.append(text) }
        return out.toString()
    }

    /**
     * Walks [latin] left to right emitting the greedy rendering of each unit
     * -- combined digraphs, primary family forms, no alternates. Used by
     * [transliterate].
     */
    private inline fun forEachGreedyUnit(latin: String, emit: (text: String) -> Unit) {
        var i = 0
        while (i < latin.length) {
            val options = unitOptionsAt(latin, i)
            val greedy = options[0]
            emit(greedy.renderings[0])
            i += greedy.length
        }
    }

    /**
     * One matchable unit at a buffer position: how many Latin characters it
     * consumes ([length]), how structurally "greedy" this choice is
     * ([segRank] -- 0 for a combined digraph, 1 for the separated
     * single-letter alternative at a digraph position), and its ordered
     * renderings ([renderings], primary family form first, then each
     * alternate family in [AmharicTable.consonantAlternates] order).
     */
    private class UnitOption(
        val length: Int,
        val segRank: Int,
        val renderings: List<String>,
        val isQuirk: Boolean = false
    )

    /**
     * The candidate unit choices starting at position [i]: normally exactly
     * one (the greedy longest match), but at a digraph position (a matched
     * consonant spelling longer than 1 char) a second, higher-[UnitOption.segRank]
     * option is added for matching just the single-letter consonant there
     * instead -- letting the lattice explore "separated" digraph readings
     * one position at a time (see [candidates]), rather than the old
     * all-or-nothing [AmharicTable.singleCharConsonants] pass over the whole
     * buffer.
     */
    private fun unitOptionsAt(latin: String, i: Int): List<UnitOption> {
        val greedyConsonant = AmharicTable.consonantsByLength
            .firstOrNull { latin.startsWith(it, i) }
            ?: AmharicTable.consonantsByLength
                .firstOrNull { latin.startsWith(it, i, ignoreCase = true) }

        if (greedyConsonant != null) {
            val greedy = consonantUnit(latin, i, greedyConsonant, segRank = 0)
            val options = mutableListOf(greedy)
            if (greedy.length > greedyConsonant.length) {
                options.add(
                    consonantUnit(
                        latin,
                        i,
                        greedyConsonant,
                        segRank = 1,
                        consumeFollowingVowel = false,
                        isQuirk = true
                    )
                )
            }
            if (greedyConsonant.length > 1) {
                val splitConsonant = AmharicTable.singleCharConsonants
                    .firstOrNull { latin.startsWith(it, i) }
                    ?: AmharicTable.singleCharConsonants
                        .firstOrNull { latin.startsWith(it, i, ignoreCase = true) }
                if (splitConsonant != null) {
                    options.add(consonantUnit(latin, i, splitConsonant, segRank = 2))
                }
            }
            return options
        }

        bareVowelUnit(latin, i)?.let { return listOf(it) }

        val char = latin[i]
        return listOf(UnitOption(length = 1, segRank = 0, renderings = listOf((AmharicTable.punctuation[char] ?: char).toString())))
    }

    /**
     * Builds the [UnitOption] for [consonant] matched at position [i]: the
     * following vowel (if any) resolved once, then rendered against the
     * primary family and every alternate family in
     * [AmharicTable.consonantAlternates], in order -- so higher indices in
     * [UnitOption.renderings] are progressively less-primary readings of the
     * SAME segmentation and vowel choice.
     */
    private fun consonantUnit(
        latin: String,
        i: Int,
        consonant: String,
        segRank: Int,
        consumeFollowingVowel: Boolean = true,
        isQuirk: Boolean = false
    ): UnitOption {
        var pos = i + consonant.length
        val vowel = if (consumeFollowingVowel) {
            AmharicTable.vowels
                .firstOrNull { (spelling, _) -> latin.startsWith(spelling, pos, ignoreCase = true) }
        } else {
            null
        }
        val vowelIndex: Int?
        if (vowel == null) {
            vowelIndex = null
        } else {
            vowelIndex = vowel.second
            pos += vowel.first.length
        }

        val families = buildList {
            add(AmharicTable.families.getValue(consonant))
            AmharicTable.consonantAlternates[consonant]?.let { addAll(it) }
        }
        val renderings = families.map { family -> renderFamily(family, vowelIndex) }
        return UnitOption(length = pos - i, segRank = segRank, renderings = renderings, isQuirk = isQuirk)
    }

    private fun renderFamily(family: AmharicTable.Family, vowelIndex: Int?): String = when {
        vowelIndex == null -> family.bare.toString()
        vowelIndex == AmharicTable.UA_INDEX ->
            family.ua?.toString() ?: (family.bare.toString() + transliterate("ua"))
        else -> family.forms[vowelIndex].toString()
    }

    /**
     * The [UnitOption] for a bare (unprefixed) vowel at position [i], or null
     * if none matches there. Two renderings when the family has a
     * glottal<->pharyngeal counterpart (primary, then the flipped family at
     * the same order index); one otherwise.
     */
    private fun bareVowelUnit(latin: String, i: Int): UnitOption? {
        val bareVowel = AmharicTable.bareVowels.firstOrNull { latin.startsWith(it.spelling, i) }
            ?: AmharicTable.bareVowels.firstOrNull { latin.startsWith(it.spelling, i, ignoreCase = true) }
            ?: return null

        val flippedKey = when (bareVowel.familyKey) {
            "'" -> "`"
            "`" -> "'"
            else -> null
        }
        val renderings = buildList {
            if (bareVowel.spelling.equals("a", ignoreCase = true)) {
                addAll(aBareVowelRenderings(bareVowel.familyKey))
                return@buildList
            }
            add(AmharicTable.families.getValue(bareVowel.familyKey).forms[bareVowel.index].toString())
            flippedKey?.let { add(AmharicTable.families.getValue(it).forms[bareVowel.index].toString()) }
        }
        return UnitOption(length = bareVowel.spelling.length, segRank = 0, renderings = renderings)
    }

    private fun aBareVowelRenderings(primaryFamilyKey: String): List<String> {
        val glottal = AmharicTable.families.getValue("'")
        val pharyngeal = AmharicTable.families.getValue("`")
        return if (primaryFamilyKey == "'") {
            listOf(
                glottal.forms[0].toString(),
                pharyngeal.forms[3].toString(),
                pharyngeal.forms[0].toString(),
                glottal.forms[3].toString()
            )
        } else {
            listOf(
                pharyngeal.forms[0].toString(),
                pharyngeal.forms[3].toString(),
                glottal.forms[0].toString(),
                glottal.forms[3].toString()
            )
        }
    }

    /** One fully-assembled path through the lattice: its rendered text and the ordered per-unit rank pairs used to sort it. */
    private class PathCandidate(val rankKey: List<Int>, val text: String, val isQuirk: Boolean)

    private val RANK_KEY_COMPARATOR = Comparator<List<Int>> { a, b ->
        val n = minOf(a.size, b.size)
        for (idx in 0 until n) {
            val cmp = a[idx].compareTo(b[idx])
            if (cmp != 0) return@Comparator cmp
        }
        a.size.compareTo(b.size)
    }

    private const val DEFAULT_CANDIDATE_LIMIT = 48

    /** Internal beam width: generous enough that dedup/cap at the public [candidates] limit never starves a real exact-match reading. */
    private const val BEAM_WIDTH = 96

    /**
     * The distinct plausible fidel readings of [latin], greedy reading always
     * first (index 0 == [transliterate]), for the suggestion strip / commit
     * ranking.
     *
     * Built as a bounded beam search over a per-position unit lattice (see
     * [unitOptionsAt]): at every position the beam keeps the best
     * [BEAM_WIDTH] partial readings of the REMAINING suffix, ranked by
     * structural score -- segmentation choice (combined digraph before
     * split) dominates, then per-unit family rank (primary before 1st
     * alternate before 2nd), compared left-to-right unit by unit so an
     * earlier unit's segmentation/family choice outranks a later one's (i.e.
     * earlier units flip last). This fixes the old uniform-alternate-level
     * scheme's blind spot: mixed per-unit choices ("t" primary + "k"
     * alternate) are reachable here, not just globally-uniform ones.
     *
     * Deduped (by rendered text, keeping the lowest-ranked path) and capped
     * at [limit].
     */
    fun candidates(latin: String, limit: Int = DEFAULT_CANDIDATE_LIMIT): List<String> {
        return candidateReadings(latin, limit).map { it.text }
    }

    fun candidateReadings(latin: String, limit: Int = DEFAULT_CANDIDATE_LIMIT): List<CandidateReading> {
        if (latin.isEmpty()) return emptyList()
        val memo = HashMap<Int, List<PathCandidate>>()
        return bestFrom(latin, 0, memo)
            .asSequence()
            .distinctBy { it.text }
            .take(limit)
            .map { CandidateReading(it.text, it.isQuirk) }
            .toList()
    }

    private fun bestFrom(latin: String, i: Int, memo: HashMap<Int, List<PathCandidate>>): List<PathCandidate> {
        memo[i]?.let { return it }
        if (i == latin.length) return listOf(PathCandidate(emptyList(), "", isQuirk = false))

        val options = unitOptionsAt(latin, i)
        val results = ArrayList<PathCandidate>()
        for (option in options) {
            val suffixCandidates = bestFrom(latin, i + option.length, memo)
            for ((familyRank, renderText) in option.renderings.withIndex()) {
                for (suffix in suffixCandidates) {
                    val rankKey = ArrayList<Int>(2 + suffix.rankKey.size)
                    rankKey.add(option.segRank)
                    rankKey.add(familyRank)
                    rankKey.addAll(suffix.rankKey)
                    results.add(
                        PathCandidate(
                            rankKey,
                            renderText + suffix.text,
                            option.isQuirk || suffix.isQuirk
                        )
                    )
                }
            }
        }

        val sorted = results.sortedWith(compareBy(RANK_KEY_COMPARATOR) { it.rankKey })
        val deduped = ArrayList<PathCandidate>(BEAM_WIDTH)
        val seenText = HashSet<String>(BEAM_WIDTH * 2)
        for (candidate in sorted) {
            if (deduped.size >= BEAM_WIDTH) break
            if (seenText.add(candidate.text)) deduped.add(candidate)
        }
        memo[i] = deduped
        return deduped
    }
}
