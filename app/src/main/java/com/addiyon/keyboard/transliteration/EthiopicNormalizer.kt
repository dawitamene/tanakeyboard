package com.addiyon.keyboard.transliteration

/**
 * Folds Ethiopic homoglyph/homophone spelling variants onto one canonical
 * character each, so that the different attested spellings of the same
 * Amharic word compare equal.
 *
 * WHY: Amharic retains several Ge'ez consonant series that are pronounced
 * identically in Amharic, so real-world text (and the corpus the suggestion
 * dictionary is built from) spells the same word inconsistently -- ሀገር /
 * ሃገር / ሐገር / ኀገር are all "country". Without folding, those spellings are
 * separate dictionary entries with *split* frequencies (each variant ranks
 * lower than the word deserves) and a typed prefix only matches the variant
 * the user happened to produce. Folding is used as a *key* -- the same role
 * `lowercaseChar()` plays for English in
 * [com.addiyon.keyboard.suggestion.WordTrie]: matching is done on the folded
 * form while the word's canonical (most frequent in the corpus) spelling is
 * what gets displayed and committed.
 *
 * The folded classes are the standard Amharic normalization set:
 *  - ሀ / ሐ / ኀ series -> ሀ series, plus the order-1/order-4 merge (ሃ -> ሀ):
 *    on laryngeals the two orders sound identical, so spelling varies freely.
 *    ሗ -> ኋ folds the two labialized "hwa" forms.
 *  - ሠ series -> ሰ series (ሧ -> ሷ).
 *  - ዐ series -> አ series, plus ኣ -> አ (same order-1/order-4 laryngeal merge,
 *    so ዓ also lands on አ).
 *  - ፀ series -> ጸ series.
 * ኸ is deliberately NOT folded into ሀ: it writes a distinct sound in Amharic.
 *
 * The canonical side of each mapping is always a character the
 * [Transliterator] can itself produce, so folded trie edges stay inside the
 * domain [AmharicTable.fidelSubstitutionCost] knows about.
 *
 * `tools/build_amharic_dict.py` carries the SAME table (mirrored by hand) to
 * merge variants and sort the bundled asset by folded key at build time --
 * keep the two in sync; `BundledAssetTest` catches drift by rebuilding the
 * real asset through this normalizer.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable, same reasoning as
 * [AmharicTable].
 */
object EthiopicNormalizer {

    private val fold: Map<Char, Char> = buildMap {
        fun series(variants: String, canonical: String) {
            require(variants.length == canonical.length)
            for (i in variants.indices) put(variants[i], canonical[i])
        }
        // ሃ folds with ሀ (order-1/order-4 laryngeal merge), so the canonical
        // series repeats ሀ in the 4th slot; same for ዓ -> አ below.
        series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
        series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
        put('ሃ', 'ሀ')
        put('ሗ', 'ኋ')
        series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
        series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
        put('ኣ', 'አ')
        series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")
    }

    /** Canonical representative of [c]'s homoglyph class ([c] itself if it
     *  has no variants -- including every non-Ethiopic character). */
    fun normalize(c: Char): Char = fold[c] ?: c

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(normalize(c))
        return sb.toString()
    }
}
