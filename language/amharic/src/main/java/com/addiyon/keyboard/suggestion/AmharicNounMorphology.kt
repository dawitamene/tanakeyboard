package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

object AmharicNounMorphology {
    data class Lexeme(
        val kind: Int,
        val surface: String,
        val features: String,
        val frequency: Int,
        val lexemeId: Long = 0,
        val morphBits: Long? = null,
        val stemClassCode: Int? = null,
    ) {
        private val parsedFeatures = if (morphBits == null) NominalFeatureParser.parse(features, kind) else null
        val effectiveBits: Long = morphBits ?: NominalFeatureBits.encode(requireNotNull(parsedFeatures), kind)
        val nominalFeatures: NominalFeatures = parsedFeatures
            ?: NominalFeatureBits.decode(effectiveBits, stemClassCode ?: 0, kind)
        val stemClass: StemClass = stemClassCode
            ?.let(NominalFeatureBits::stemClass)
            ?: nominalFeatures.stemClass
    }

    data class Query(
        val prefix: String,
        val exactStemSurfaces: Set<String>,
        val completionStemPrefix: String,
    )

    private const val MIN_CANDIDATE_BUFFER = 32
    private const val CANDIDATE_BUFFER_MULTIPLIER = 4

    fun query(typed: String): Query {
        val contexts = NominalRuleGraph.prefixContexts(typed)
        val primary = contexts.drop(1).firstOrNull { !it.first.contractedInitial } ?: contexts.first()
        val exactSurfaces = contexts.flatMapTo(linkedSetOf()) { (_, fragment) ->
            NominalRuleGraph.reverseStemCandidates(fragment)
        }
        return Query(
            prefix = primary.first.surface,
            exactStemSurfaces = exactSurfaces,
            completionStemPrefix = primary.second,
        )
    }

    fun complete(
        typed: String,
        lexemes: List<Lexeme>,
        limit: Int,
        alreadyFound: List<CandidateRanker.AmharicCandidate> = emptyList(),
        surfaceFrequencyOf: (Collection<String>) -> Map<String, Int> = { emptyMap() },
    ): List<CandidateRanker.AmharicCandidate> {
        val candidates = completeCandidates(typed, lexemes, limit)
        val surfaceFrequencies = surfaceFrequencyOf(candidates.map { it.normalizedKey })
        val seen = HashSet<String>()
        alreadyFound.forEach { seen += EthiopicNormalizer.normalize(it.word) }
        return candidates.mapNotNull { candidate ->
            if (!seen.add(candidate.normalizedKey)) return@mapNotNull null
            val surfaceFrequency = surfaceFrequencies[candidate.normalizedKey]
            CandidateRanker.AmharicCandidate(
                word = candidate.word,
                source = if (surfaceFrequency == null) {
                    CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY
                } else {
                    CandidateRanker.CandidateSource.ATTESTED_SURFACE
                },
                lexicalFrequency = candidate.lexicalFrequency,
                surfaceFrequency = surfaceFrequency,
                morphologyCost = candidate.analysis.orthographicCost,
            )
        }.take(limit)
    }

    fun completeCandidates(
        typed: String,
        lexemes: List<Lexeme>,
        limit: Int,
    ): List<MorphCandidate> {
        if (typed.isEmpty() || limit <= 0 || lexemes.isEmpty()) return emptyList()
        val prefixes = NominalRuleGraph.prefixContexts(typed).map { it.first }
        val poolLimit = maxOf(MIN_CANDIDATE_BUFFER, limit * CANDIDATE_BUFFER_MULTIPLIER)
        val candidates = ArrayList<MorphCandidate>(poolLimit)
        for (lexeme in lexemes) {
            if (Thread.currentThread().isInterrupted) return emptyList()
            for (prefix in prefixes) {
                if (Thread.currentThread().isInterrupted) return emptyList()
                candidates += NominalRuleGraph.generate(
                    lexeme = lexeme,
                    prefix = prefix,
                    typed = typed,
                    limit = poolLimit - candidates.size,
                )
                if (candidates.size >= poolLimit) break
            }
            if (candidates.size >= poolLimit) break
        }
        return candidates.sortedWith(
            compareBy<MorphCandidate> { it.word.length - typed.length }
                .thenBy { it.analysis.orthographicCost }
                .thenByDescending { it.lexicalFrequency }
                .thenBy { it.word }
                .thenBy { it.analysis.lexemeId }
                .thenBy { it.analysis.affixes.toString() }
        ).take(poolLimit)
    }

    fun analyze(surface: String, lexemes: List<Lexeme>): List<Lexeme> {
        val ids = analyses(surface, lexemes).mapTo(linkedSetOf()) { it.lexemeId to it.lemma }
        return lexemes.filter { (it.lexemeId to it.surface) in ids }
    }

    fun analyses(surface: String, lexemes: List<Lexeme>): List<MorphAnalysis> {
        if (surface.isEmpty() || lexemes.isEmpty()) return emptyList()
        val prefixes = NominalRuleGraph.prefixContexts(surface).map { it.first }
        val normalized = EthiopicNormalizer.normalize(surface)
        return lexemes.flatMap { lexeme ->
            prefixes.flatMap { prefix ->
                NominalRuleGraph.generate(lexeme, prefix, surface, Int.MAX_VALUE)
                    .asSequence()
                    .filter { it.normalizedKey == normalized }
                    .map { it.analysis }
                    .toList()
            }
        }.distinctBy {
            Triple(it.lexemeId to it.lemma, it.surface, it.affixes)
        }
    }
}
