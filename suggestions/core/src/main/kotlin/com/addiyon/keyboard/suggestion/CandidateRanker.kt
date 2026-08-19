package com.addiyon.keyboard.suggestion

/**
 * Reorders transliteration readings and dictionary results without touching
 * the deterministic composing buffer. The legacy [rank] API is still a stable
 * exact-word partition; [bestCommitCandidate] and [rankAmharic] add the scored
 * Amharic suggestion behavior used by the service.
 */
object CandidateRanker {
    data class DictionaryWord(val word: String, val frequency: Int)
    data class FuzzyWord(val word: String, val frequency: Int, val editDistance: Int)

    enum class CandidateSource {
        EXACT_LEXEME,
        GREEDY_LITERAL,
        ATTESTED_SURFACE,
        GENERATED_MORPHOLOGY,
        GUESSER_MORPHOLOGY,
        PERSONAL,
        FUZZY,
    }

    data class PersonalEvidence(
        val count: Int,
        val recency: Int,
    )

    data class AmharicCandidate(
        val word: String,
        val source: CandidateSource,
        val lexicalFrequency: Int = 0,
        val surfaceFrequency: Int? = null,
        val personalCount: Int = 0,
        val personalRecency: Int = 0,
        val morphologyCost: Int = 0,
        val editDistance: Int = 0,
        val evidenceSources: Set<CandidateSource> = emptySet(),
    )

    data class RankedCandidate(
        val candidate: AmharicCandidate,
        val score: Int,
        val structuralIndex: Int,
        val exactReading: Boolean,
    )

    private data class ScoredSuggestion(
        val ranked: RankedCandidate,
    )

    private const val EXACT_READING_BONUS = 250_000
    private const val LITERAL_BONUS = 210_000
    private const val EXACT_LEXEME_COMPLETION_BONUS = 170_000
    private const val ATTESTED_SURFACE_BONUS = 130_000
    private const val GENERATED_MORPHOLOGY_BONUS = 90_000
    private const val GUESSER_MORPHOLOGY_BONUS = 70_000
    private const val FUZZY_BONUS = 50_000
    private const val STRUCTURAL_PENALTY = 180
    private const val COMPLETION_LENGTH_PENALTY = 20
    private const val FUZZY_EDIT_PENALTY = 5_000
    private const val MORPHOLOGY_COST_PENALTY = 1_000
    private const val PERSONAL_COUNT_SCALE = 500
    private const val PERSONAL_MAX_BONUS = 4_000
    private const val PERSONAL_RECENCY_MAX_BONUS = 1_000
    private const val COMPLETION_CONTEXT_MAX_BONUS = 5_000
    private const val WITHIN_TIER_MAX_PENALTY = 10_000

    // N-gram context boost: base + scaled model weight (0-255), capped below
    // the source-tier gaps so context cannot promote fuzzy or generated forms
    // across a stronger validity tier.
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
        completionsForPrefix: (String, Int) -> List<AmharicCandidate>,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<FuzzyWord> = emptyList(),
        quirkReadings: Set<String> = emptySet(),
        ngramNext: Map<String, Int> = emptyMap(),
        personalEvidence: Map<String, PersonalEvidence> = emptyMap(),
        preferGreedy: Boolean = false,
        normalize: (String) -> String = { it }
    ): List<String> = rankAmharicDetailed(
        readings = readings,
        limit = limit,
        frequencyOf = frequencyOf,
        completionsForPrefix = completionsForPrefix,
        visibleReadings = visibleReadings,
        fuzzyWords = fuzzyWords,
        quirkReadings = quirkReadings,
        ngramNext = ngramNext,
        personalEvidence = personalEvidence,
        preferGreedy = preferGreedy,
        normalize = normalize,
    ).map { it.candidate.word }

    fun rankAmharicDetailed(
        readings: List<String>,
        limit: Int,
        frequencyOf: (String) -> Int?,
        completionsForPrefix: (String, Int) -> List<AmharicCandidate>,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<FuzzyWord> = emptyList(),
        quirkReadings: Set<String> = emptySet(),
        ngramNext: Map<String, Int> = emptyMap(),
        personalEvidence: Map<String, PersonalEvidence> = emptyMap(),
        preferGreedy: Boolean = false,
        normalize: (String) -> String = { it }
    ): List<RankedCandidate> {
        if (readings.isEmpty() || limit <= 0) return emptyList()

        val scored = ArrayList<ScoredSuggestion>()
        var greedyIsExactWord = false

        for ((index, reading) in readings.withIndex()) {
            // Structural SPLIT (quirk) readings never win the promoted "exact
            // word" default -- otherwise a dictionary hit on the re-segmented
            // ም+እ would suppress the greedy መ for "me". They still surface as
            // completion prefixes below and as dictionary-backed quirk chips
            // via visibleReadings.
            if (reading in quirkReadings) continue
            val frequency = frequencyOf(reading) ?: continue
            if (index == 0) greedyIsExactWord = true
            val candidate = withPersonalEvidence(
                AmharicCandidate(
                    word = reading,
                    source = CandidateSource.EXACT_LEXEME,
                    lexicalFrequency = frequency,
                ),
                personalEvidence,
                normalize,
            )
            scored += score(
                candidate = candidate,
                structuralIndex = index,
                exactReading = true,
                lengthDelta = 0,
                contextBonus = ngramBoost(ngramNext, reading, normalize),
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
            scored += score(
                candidate = AmharicCandidate(
                    word = readings.first(),
                    source = CandidateSource.GREEDY_LITERAL,
                ),
                structuralIndex = 0,
                exactReading = false,
                lengthDelta = 0,
                contextBonus = 0,
            )
        }

        for ((index, reading) in visibleReadings.withIndex()) {
            if (reading == readings.first() || frequencyOf(reading) == null) continue
            val candidate = withPersonalEvidence(
                AmharicCandidate(
                    word = reading,
                    source = CandidateSource.EXACT_LEXEME,
                    lexicalFrequency = frequencyOf(reading) ?: 0,
                ),
                personalEvidence,
                normalize,
            )
            scored += score(
                candidate = candidate,
                structuralIndex = index + 1,
                exactReading = false,
                lengthDelta = 0,
                contextBonus = ngramBoost(ngramNext, reading, normalize),
            )
        }

        for ((index, reading) in readings.withIndex()) {
            val completions = completionsForPrefix(reading, limit)
            for (completion in completions) {
                if (index > 0 && reading !in quirkReadings && normalize(completion.word) != normalize(reading)) {
                    continue
                }
                val candidate = withPersonalEvidence(completion, personalEvidence, normalize)
                scored += score(
                    candidate = candidate,
                    structuralIndex = index,
                    exactReading = false,
                    lengthDelta = completion.word.length - reading.length,
                    contextBonus = ngramBoost(ngramNext, completion.word, normalize),
                )
            }
        }

        for (word in fuzzyWords) {
            scored += score(
                candidate = AmharicCandidate(
                    word = word.word,
                    source = CandidateSource.FUZZY,
                    lexicalFrequency = word.frequency,
                    editDistance = word.editDistance,
                ),
                structuralIndex = Int.MAX_VALUE,
                exactReading = false,
                lengthDelta = 0,
                contextBonus = 0,
            )
        }

        val bestByWord = LinkedHashMap<String, ScoredSuggestion>(scored.size)
        for (scoredCandidate in scored) {
            val candidate = scoredCandidate.ranked
            val normalizedWord = normalize(candidate.candidate.word)
            val current = bestByWord[normalizedWord]?.ranked
            if (
                current == null ||
                candidate.score > current.score ||
                candidate.score == current.score &&
                    sourceRank(candidate.candidate.source) < sourceRank(current.candidate.source) ||
                candidate.score == current.score &&
                    sourceRank(candidate.candidate.source) == sourceRank(current.candidate.source) &&
                    candidate.structuralIndex < current.structuralIndex
            ) {
                bestByWord[normalizedWord] = scoredCandidate
            }
        }
        val ranked = bestByWord.values
            .sortedWith(
                compareByDescending<ScoredSuggestion> { it.ranked.score }
                    .thenBy { sourceRank(it.ranked.candidate.source) }
                    .thenBy { it.ranked.structuralIndex }
                    .thenBy { it.ranked.candidate.word }
            )
            .map { it.ranked }
            .take(limit)
        if (!preferGreedy || ranked.firstOrNull()?.candidate?.word == readings.first()) return ranked
        return buildList {
            ranked.firstOrNull { it.candidate.word == readings.first() }?.let(::add)
            addAll(ranked.filterNot { it.candidate.word == readings.first() })
        }.take(limit)
    }

    private fun exactScore(frequency: Int, structuralIndex: Int): Int =
        EXACT_READING_BONUS + frequencyScore(frequency) - structuralIndex * STRUCTURAL_PENALTY

    private fun score(
        candidate: AmharicCandidate,
        structuralIndex: Int,
        exactReading: Boolean,
        lengthDelta: Int,
        contextBonus: Int,
    ): ScoredSuggestion {
        require(candidate.source != CandidateSource.PERSONAL)
        val sourceBonus = when {
            exactReading -> EXACT_READING_BONUS
            candidate.source == CandidateSource.GREEDY_LITERAL -> LITERAL_BONUS
            candidate.source == CandidateSource.EXACT_LEXEME -> EXACT_LEXEME_COMPLETION_BONUS
            candidate.source == CandidateSource.ATTESTED_SURFACE -> ATTESTED_SURFACE_BONUS
            candidate.source == CandidateSource.GENERATED_MORPHOLOGY -> GENERATED_MORPHOLOGY_BONUS
            candidate.source == CandidateSource.GUESSER_MORPHOLOGY -> GUESSER_MORPHOLOGY_BONUS
            else -> FUZZY_BONUS
        }
        val frequencyEvidence = when {
            exactReading ->
                frequencyScore(candidate.lexicalFrequency)
            candidate.source == CandidateSource.EXACT_LEXEME ->
                frequencyScore(candidate.lexicalFrequency).coerceAtMost(15_000)
            candidate.source == CandidateSource.ATTESTED_SURFACE ->
                candidate.surfaceFrequency.orZero().coerceAtMost(10_000) +
                    lexicalEvidenceScore(candidate.lexicalFrequency).coerceAtMost(3_000)
            candidate.source == CandidateSource.GENERATED_MORPHOLOGY ||
            candidate.source == CandidateSource.GUESSER_MORPHOLOGY ->
                lexicalEvidenceScore(candidate.lexicalFrequency).coerceAtMost(3_000)
            candidate.source == CandidateSource.FUZZY ->
                frequencyScore(candidate.lexicalFrequency).coerceAtMost(15_000)
            else -> 0
        }
        val penalties = (
            structuralIndex.coerceAtMost(1_000) * STRUCTURAL_PENALTY +
                lengthDelta.coerceIn(0, 500) * COMPLETION_LENGTH_PENALTY +
                candidate.morphologyCost.coerceIn(0, 10) * MORPHOLOGY_COST_PENALTY +
                candidate.editDistance.coerceIn(0, 10) * FUZZY_EDIT_PENALTY
            ).coerceAtMost(WITHIN_TIER_MAX_PENALTY)
        val score = sourceBonus +
            frequencyEvidence +
            personalBonus(candidate) +
            contextBonus.coerceAtMost(if (exactReading) NGRAM_MAX_BONUS else COMPLETION_CONTEXT_MAX_BONUS) -
            penalties
        return ScoredSuggestion(
            RankedCandidate(
                candidate = candidate,
                score = score,
                structuralIndex = structuralIndex,
                exactReading = exactReading,
            )
        )
    }

    private fun withPersonalEvidence(
        candidate: AmharicCandidate,
        personalEvidence: Map<String, PersonalEvidence>,
        normalize: (String) -> String,
    ): AmharicCandidate {
        if (
            candidate.source != CandidateSource.EXACT_LEXEME &&
            candidate.source != CandidateSource.ATTESTED_SURFACE &&
            candidate.source != CandidateSource.GENERATED_MORPHOLOGY &&
            candidate.source != CandidateSource.GUESSER_MORPHOLOGY
        ) {
            return candidate
        }
        val evidence = personalEvidence[normalize(candidate.word)] ?: return candidate
        return candidate.copy(
            personalCount = maxOf(candidate.personalCount, evidence.count),
            personalRecency = maxOf(candidate.personalRecency, evidence.recency),
            evidenceSources = candidate.evidenceSources + CandidateSource.PERSONAL,
        )
    }

    private fun personalBonus(candidate: AmharicCandidate): Int {
        if (CandidateSource.PERSONAL !in candidate.evidenceSources) return 0
        val cappedCount = candidate.personalCount.coerceIn(
            0,
            PERSONAL_MAX_BONUS / PERSONAL_COUNT_SCALE,
        )
        val count = cappedCount * PERSONAL_COUNT_SCALE
        val recency = (candidate.personalRecency.coerceAtLeast(0) * 2)
            .coerceAtMost(PERSONAL_RECENCY_MAX_BONUS)
        return count + recency
    }

    private fun lexicalEvidenceScore(frequency: Int): Int {
        var remaining = frequency.coerceAtLeast(0)
        var magnitude = 0
        while (remaining > 0) {
            magnitude += 1
            remaining = remaining ushr 1
        }
        return (magnitude * 300).coerceAtMost(5_000)
    }

    private fun sourceRank(source: CandidateSource): Int = when (source) {
        CandidateSource.EXACT_LEXEME -> 0
        CandidateSource.GREEDY_LITERAL -> 1
        CandidateSource.ATTESTED_SURFACE -> 2
        CandidateSource.GENERATED_MORPHOLOGY -> 3
        CandidateSource.GUESSER_MORPHOLOGY -> 4
        CandidateSource.FUZZY -> 5
        CandidateSource.PERSONAL -> 6
    }

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
        val normalized = normalize(word)
        val weight = ngramNext[normalized]
            ?: (if (normalized.contains('\'')) ngramNext[normalized.replace("'", "")] else null)
            ?: return 0
        return (NGRAM_BASE_BONUS + weight * NGRAM_WEIGHT_SCALE)
            .coerceAtMost(NGRAM_MAX_BONUS)
    }

    private fun frequencyScore(frequency: Int): Int =
        frequency.coerceAtLeast(0).coerceAtMost(30_000)

    private fun Int?.orZero(): Int = this ?: 0
}
