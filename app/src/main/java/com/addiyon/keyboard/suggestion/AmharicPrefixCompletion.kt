package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

/**
 * Morphology-aware fallback completions for Amharic: when the dictionary's
 * direct prefix completions come up short, strip a known productive prefix
 * (የ-, በ-, ለ-, ከ-, ስለ-, እንደ-, እየ-, ...) off the typed fidel, complete the
 * *remainder* against the stem lexicon, and re-attach the prefix to each
 * completion.
 *
 * WHY: Amharic derives an open set of surface forms by concatenating a
 * closed set of prefixes onto stems (የቤት "of the house" = የ + ቤት), so no
 * corpus-derived wordlist can store them all -- the dictionary keeps the
 * *frequent* prefixed forms (with their real, informative frequencies), and
 * this synthesizes the long tail from stems instead of storing it. That
 * split is deliberate: a stored የኢትዮጵያ ranks by its own corpus frequency,
 * while a synthesized የ+ቤቶች only ever FILLS SLOTS the direct lookup left
 * empty and carries a discounted frequency ([FREQUENCY_DIVISOR]) -- stem
 * frequency is evidence about the stem, not about the prefixed form (ነው is
 * the most frequent word in the corpus but የነው is not a word), so synthesized
 * candidates must never outrank corpus-attested ones.
 *
 * Suffixes (-ም, -ን, -ው ...) are deliberately NOT handled here: completions
 * extend the typed prefix to the right, so a stem completion already IS a
 * valid word the user can keep typing a suffix after; only prefixes block
 * the trie walk at its root.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable, same reasoning as
 * [WordTrie]. The caller supplies the dictionary lookup as a lambda.
 */
object AmharicPrefixCompletion {

    /**
     * The productive prepositional/relative/conjunctive prefixes, longest
     * first so a compound like በየ strips before its single-char prefix በ.
     * All spelled in canonical (folded) characters -- none of them contain a
     * homoglyph-variant character, so a plain `startsWith` against
     * transliterator output is safe.
     */
    private val PREFIXES = listOf(
        "እንደ", "እስከ", // as/like-, until-
        "ስለ", "እየ", "ወደ", "ያለ", // about-, while-, toward-, without-
        "በየ", "ለየ", "ከየ", // distributive "every": በ/ለ/ከ + የ
        "የ", "በ", "ለ", "ከ", // of-, in/by-, for-, from-
    )

    /** Synthesized forms carry stem frequency discounted by this factor --
     *  see the class doc for why stem frequency must not pass through as-is. */
    private const val FREQUENCY_DIVISOR = 4

    /**
     * Up to [limit] synthesized completions of the typed fidel [prefix],
     * skipping (folded-key comparison) anything in [alreadyFound] and the
     * typed prefix itself. [lookup] is the plain dictionary prefix search
     * ((prefix, limit) -> ranked entries). Empty when no known prefix
     * matches, mirroring how direct lookups return empty on a miss.
     */
    fun complete(
        prefix: String,
        limit: Int,
        alreadyFound: List<CandidateRanker.DictionaryWord>,
        lookup: (String, Int) -> List<CandidateRanker.DictionaryWord>,
    ): List<CandidateRanker.DictionaryWord> {
        if (limit <= 0 || prefix.isEmpty()) return emptyList()

        val seen = HashSet<String>()
        for (found in alreadyFound) seen.add(EthiopicNormalizer.normalize(found.word))
        seen.add(EthiopicNormalizer.normalize(prefix))

        val out = ArrayList<CandidateRanker.DictionaryWord>(limit)
        for (stripped in PREFIXES) {
            if (out.size >= limit) break
            // The remainder must be non-empty: bare የ needs no synthesis (the
            // dictionary is full of stored የ… words for the direct pass).
            if (prefix.length <= stripped.length || !prefix.startsWith(stripped)) continue
            val remainder = prefix.substring(stripped.length)
            // A few extra results cover entries lost to dedup below.
            for (stem in lookup(remainder, limit + seen.size)) {
                val word = stripped + stem.word
                if (!seen.add(EthiopicNormalizer.normalize(word))) continue
                out.add(
                    CandidateRanker.DictionaryWord(
                        word,
                        (stem.frequency / FREQUENCY_DIVISOR).coerceAtLeast(1),
                    )
                )
                if (out.size >= limit) break
            }
        }
        return out
    }
}
