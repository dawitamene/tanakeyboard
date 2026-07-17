package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

/**
 * Reorders transliteration readings and dictionary results without touching
 * the deterministic composing buffer. The legacy [rank] API is still a stable
 * exact-word partition; [bestCommitCandidate] and [rankAmharic] add the scored
 * Amharic suggestion behavior used by the service.
 */
object CandidateRanker {
    data class DictionaryWord(val word: String, val frequency: Int)
    data class FuzzyWord(val word: String, val frequency: Int, val editDistance: Int)

    private data class ScoredSuggestion(
        val word: String,
        val score: Int,
        val sourceRank: Int,
        val structuralIndex: Int
    )

    private const val EXACT_WORD_BONUS = 160_000
    private const val LITERAL_BONUS = 120_000
    private const val COMPLETION_BONUS = 80_000
    private const val FUZZY_BONUS = 40_000
    private const val STRUCTURAL_PENALTY = 180
    private const val COMPLETION_LENGTH_PENALTY = 20
    private const val FUZZY_EDIT_PENALTY = 5_000

    // N-gram context boost: base + scaled model weight (0-255), capped well
    // below both the 40k source-tier gaps and the 30k frequency band, so
    // context reorders candidates *within* their tier but can never promote
    // e.g. a fuzzy match over a completion.
    private const val NGRAM_BASE_BONUS = 2_000
    private const val NGRAM_WEIGHT_SCALE = 31
    private const val NGRAM_MAX_BONUS = 10_000

    fun rank(candidates: List<String>, isWord: (String) -> Boolean): List<String> {
        val exact = ArrayList<String>(candidates.size)
        val rest = ArrayList<String>(candidates.size)
        for (candidate in candidates) {
            if (isWord(candidate)) exact.add(candidate) else rest.add(candidate)
        }
        exact.addAll(rest)
        return exact
    }

    fun bestCommitCandidate(
        candidates: List<String>,
        frequencyOf: (String) -> Int?,
        quirkReadings: Set<String> = emptySet(),
        preferGreedy: Boolean = false
    ): String? {
        if (candidates.isEmpty()) return null
        if (preferGreedy) return candidates.first()
        var bestWord: String? = null
        var bestScore = Int.MIN_VALUE
        for ((index, candidate) in candidates.withIndex()) {
            // A dictionary hit on a structural SPLIT (quirk) reading -- e.g.
            // "me" re-segmented as ም+እ -- must not hijack the commit from the
            // natural greedy reading (መ). Quirks stay tap-only suggestions.
            if (candidate in quirkReadings) continue
            val frequency = frequencyOf(candidate) ?: continue
            val score = exactScore(frequency, index)
            if (score > bestScore) {
                bestScore = score
                bestWord = candidate
            }
        }
        return bestWord ?: candidates.first()
    }

    fun rankAmharic(
        readings: List<String>,
        limit: Int,
        frequencyOf: (String) -> Int?,
        completionsForPrefix: (String, Int) -> List<DictionaryWord>,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<FuzzyWord> = emptyList(),
        quirkReadings: Set<String> = emptySet(),
        ngramNext: Map<String, Int> = emptyMap(),
        preferGreedy: Boolean = false
    ): List<String> {
        if (readings.isEmpty() || limit <= 0) return emptyList()

        val scored = ArrayList<ScoredSuggestion>()
        var hasExactReading = false
        var greedyIsExactWord = false

        for ((index, reading) in readings.withIndex()) {
            // Structural SPLIT (quirk) readings never win the promoted "exact
            // word" default -- otherwise a dictionary hit on the re-segmented
            // ም+እ would suppress the greedy መ for "me". They still surface as
            // completion prefixes below and as quirk chips via visibleReadings.
            if (reading in quirkReadings) continue
            val frequency = frequencyOf(reading) ?: continue
            hasExactReading = true
            if (index == 0) greedyIsExactWord = true
            scored.add(
                ScoredSuggestion(
                    reading,
                    exactScore(frequency, index) +
                        ngramBoost(ngramNext, reading, EthiopicNormalizer::normalize),
                    sourceRank = 0,
                    structuralIndex = index
                )
            )
        }

        // The greedy reading (readings[0]) is what's shown inline while typing,
        // so it must ALWAYS be a tap-committable chip -- even when a dictionary
        // word on an alternate reading outranks it (e.g. "fkr": ፍቅር is a word,
        // the literal ፍክር isn't). The LITERAL tier sits below exact words but
        // above completions/fuzzy, so it lands right after the dictionary hits
        // and before any other suggestion. Skipped only when the greedy reading
        // is itself an exact word (already scored higher, in the loop above).
        if (!greedyIsExactWord) {
            scored.add(
                ScoredSuggestion(
                    readings.first(),
                    LITERAL_BONUS,
                    sourceRank = 1,
                    structuralIndex = 0
                )
            )
        }

        if (!hasExactReading) {
            // Deeper structural alternates (non-greedy, non-word) only clutter
            // the strip once a real dictionary word exists, so they stay gated
            // on the no-exact-word case -- unlike the greedy literal above.
            for ((index, reading) in visibleReadings.withIndex()) {
                if (reading == readings.first()) continue
                scored.add(
                    ScoredSuggestion(
                        reading,
                        visibleReadingScore(index + 1),
                        sourceRank = 1,
                        structuralIndex = index + 1
                    )
                )
            }
        }

        for ((index, reading) in readings.withIndex()) {
            val completions = completionsForPrefix(reading, limit)
            for (completion in completions) {
                if (completion.word == reading) continue
                scored.add(
                    ScoredSuggestion(
                        completion.word,
                        completionScore(
                            completion.frequency,
                            index,
                            completion.word.length - reading.length
                        ) + ngramBoost(ngramNext, completion.word, EthiopicNormalizer::normalize),
                        sourceRank = 2,
                        structuralIndex = index
                    )
                )
            }
        }

        for (word in fuzzyWords) {
            scored.add(
                ScoredSuggestion(
                    word.word,
                    fuzzyScore(word.frequency, word.editDistance),
                    sourceRank = 3,
                    structuralIndex = Int.MAX_VALUE
                )
            )
        }

        val ranked = scored
            .groupBy { it.word }
            .values
            .map { options ->
                options.maxWithOrNull(
                    compareBy<ScoredSuggestion> { it.score }
                        .thenByDescending { -it.sourceRank }
                        .thenByDescending { -it.structuralIndex }
                )!!
            }
            .sortedWith(
                compareByDescending<ScoredSuggestion> { it.score }
                    .thenBy { it.sourceRank }
                    .thenBy { it.structuralIndex }
                    .thenBy { it.word }
            )
            .map { it.word }
            .take(limit)
        if (!preferGreedy || ranked.firstOrNull() == readings.first()) return ranked
        return buildList {
            add(readings.first())
            addAll(ranked.filterNot { it == readings.first() })
        }.take(limit)
    }

    private fun exactScore(frequency: Int, structuralIndex: Int): Int =
        EXACT_WORD_BONUS + frequencyScore(frequency) - structuralIndex * STRUCTURAL_PENALTY

    private fun completionScore(frequency: Int, structuralIndex: Int, lengthDelta: Int): Int =
        COMPLETION_BONUS +
            frequencyScore(frequency) -
            structuralIndex * STRUCTURAL_PENALTY -
            lengthDelta.coerceAtLeast(0) * COMPLETION_LENGTH_PENALTY

    private fun fuzzyScore(frequency: Int, editDistance: Int): Int =
        FUZZY_BONUS + frequencyScore(frequency) - editDistance * FUZZY_EDIT_PENALTY

    /**
     * English next-word-aware completion ordering: reorders [candidates]
     * (already frequency-ranked prefix completions from the trie) by a
     * within-tier n-gram context nudge and returns the top [limit] display
     * forms. The standalone analogue of the completion boost inside
     * [rankAmharic] -- English has no reading/quirk/literal tiers, just
     * dictionary completions. A candidate the model predicts to follow the
     * previous words rises, but only within the pool the trie already
     * returned: [frequencyScore] saturates at 30k for every common word, so
     * context breaks ties among the common completions while the capped boost
     * can never lift a genuinely rare completion over a common one.
     * [normalize] must match the keying of [ngramNext] (per-char lowercase,
     * see the service's boost-map construction).
     */
    fun rankByContext(
        candidates: List<DictionaryWord>,
        ngramNext: Map<String, Int>,
        normalize: (String) -> String,
        limit: Int
    ): List<String> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        return candidates
            .distinctBy { it.word }
            .sortedWith(
                compareByDescending<DictionaryWord> {
                    frequencyScore(it.frequency) + ngramBoost(ngramNext, it.word, normalize)
                }.thenByDescending { it.frequency }
            )
            .take(limit)
            .map { it.word }
    }

    /** [ngramNext] is keyed by the [normalize] fold (see the service's
     *  boost-map construction), so a candidate in any variant spelling / casing
     *  still collects its boost. Plain keys are unaffected: the fold is
     *  identity on them. */
    private fun ngramBoost(
        ngramNext: Map<String, Int>,
        word: String,
        normalize: (String) -> String
    ): Int {
        if (ngramNext.isEmpty()) return 0
        val weight = ngramNext[normalize(word)] ?: return 0
        return (NGRAM_BASE_BONUS + weight * NGRAM_WEIGHT_SCALE)
            .coerceAtMost(NGRAM_MAX_BONUS)
    }

    private fun visibleReadingScore(index: Int): Int =
        LITERAL_BONUS - index * STRUCTURAL_PENALTY

    private fun frequencyScore(frequency: Int): Int =
        frequency.coerceAtLeast(0).coerceAtMost(30_000)
}
