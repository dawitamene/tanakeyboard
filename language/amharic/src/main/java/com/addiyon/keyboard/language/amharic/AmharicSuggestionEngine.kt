package com.addiyon.keyboard.language.amharic

import android.content.Context
import com.addiyon.keyboard.suggestion.AmharicCommitPolicy
import com.addiyon.keyboard.suggestion.AmharicNounMorphology
import com.addiyon.keyboard.suggestion.AmharicVerbLexicon
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.suggestion.MorphologyIdentity
import com.addiyon.keyboard.suggestion.SQLiteDictionary
import com.addiyon.keyboard.suggestion.SQLiteLanguageStore
import com.addiyon.keyboard.suggestion.SQLiteMorphLexicon
import com.addiyon.keyboard.suggestion.SQLiteNgramModel
import com.addiyon.keyboard.suggestion.SubstitutionCost
import com.addiyon.keyboard.suggestion.SuggestionTrace
import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import com.addiyon.keyboard.transliteration.Transliterator
import java.util.Collections
import java.util.LinkedHashMap

class AmharicSuggestionEngine(
    context: Context,
    isLowMemoryDevice: Boolean,
    onOutOfMemory: () -> Unit,
    onFailure: (Throwable, String) -> Unit,
    onWarning: (String) -> Unit
) : LanguageSuggestionEngine {
    private data class SuggestionCacheKey(
        val modelVersion: String,
        val raw: String,
        val contextWeights: List<Pair<String, Int>>,
        val personalEvidence: List<Pair<String, CandidateRanker.PersonalEvidence>>,
        val lowMemory: Boolean,
    )

    private val store = SQLiteLanguageStore(
        context = context,
        assetName = "amharic.db",
        metadataAssetName = "amharic_dictionary_manifest.properties",
        isLowRam = isLowMemoryDevice,
        onOutOfMemory = onOutOfMemory,
        onFailure = onFailure,
        onWarning = onWarning
    )
    private val dictionary = SQLiteDictionary(store, 1, EthiopicNormalizer::normalize)
    private val ngrams = SQLiteNgramModel(store, EthiopicNormalizer::normalize)
    private val morphLexicon = SQLiteMorphLexicon(store, EthiopicNormalizer::normalize)
    private val verbLexicon = AmharicVerbLexicon.load(context.assets, onWarning)
    private val suggestionCache = Collections.synchronizedMap(
        object : LinkedHashMap<SuggestionCacheKey, List<String>>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<SuggestionCacheKey, List<String>>,
            ) =
                size > CACHE_SIZE
        }
    )
    private val commitCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > CACHE_SIZE
        }
    )

    override val languageId: String = ID
    override val isReady: Boolean get() = store.isReady
    override val isLoading: Boolean get() = store.isLoading

    override fun loadAsync(onReady: () -> Unit) = store.loadAsync(onReady)

    override fun complete(query: CompletionQuery): List<String> {
        val latin = query.raw
        if (latin.isEmpty() || Thread.currentThread().isInterrupted) return emptyList()
        val pipeline = AmharicSuggestionPipeline.prepare(latin)
        val readings = pipeline.readings
        val quirkReadings = pipeline.quirkReadings
        val personalEvidence = personalEvidence(readings, query)
        val cacheKey = suggestionCacheKey(query, personalEvidence)
        if (dictionary.isReady) suggestionCache[cacheKey]?.let { return it }
        val readingFrequencies = dictionary.frequenciesOf(readings)
        if (Thread.currentThread().isInterrupted) return emptyList()
        commitCache[latin] = CandidateRanker.bestCommitCandidate(
            readings,
            readingFrequencies::get,
            quirkReadings,
            pipeline.preferGreedy
        ) ?: readings.first()
        val directCompletions = dictionary.suggestionEntriesForPrefixes(
            readings.distinct(),
            SUGGESTION_LIMIT
        )
        if (Thread.currentThread().isInterrupted) return emptyList()
        val preferredAlternate = (
            Transliterator.vowelAlternateReading(latin)
                ?: Transliterator.bareVowelAlternateReading(latin)
            )?.takeIf {
            it.length > 1 && dictionary.isReady && readingFrequencies.containsKey(it)
        }
        val directCompletionCache = directCompletions.mapValuesTo(HashMap()) { (_, entries) ->
            entries.map {
                CandidateRanker.AmharicCandidate(
                    word = it.word,
                    source = CandidateRanker.CandidateSource.EXACT_LEXEME,
                    lexicalFrequency = it.frequency,
                )
            }
        }
        val completionCache = HashMap<String, List<CandidateRanker.AmharicCandidate>>()
        val morphologyCache = HashMap<String, List<CandidateRanker.AmharicCandidate>>()
        val dictionaryLookup = { prefix: String, limit: Int ->
            dictionary.suggestionEntries(prefix, limit).map {
                CandidateRanker.AmharicCandidate(
                    word = it.word,
                    source = CandidateRanker.CandidateSource.EXACT_LEXEME,
                    lexicalFrequency = it.frequency,
                )
            }
        }
        val completionsForPrefix = { prefix: String, limit: Int ->
            if (Thread.currentThread().isInterrupted) {
                emptyList()
            } else completionCache.getOrPut(prefix) {
                val direct = directCompletionCache[prefix] ?: dictionaryLookup(prefix, limit)
                if (Thread.currentThread().isInterrupted) return@getOrPut emptyList()
                val generated = morphologyCache.getOrPut(prefix) {
                    morphologyCompletions(prefix, limit, direct)
                }
                direct + generated
            }
        }
        val ranked = SuggestionTrace.section("candidate_ranking") {
            AmharicSuggestionPipeline.rank(
                context = pipeline,
                limit = SUGGESTION_LIMIT,
                frequencyOf = readingFrequencies::get,
                completionsForPrefix = completionsForPrefix,
                ngramNext = query.contextWeights,
                personalEvidence = personalEvidence,
            )
        }
        if (
            ranked.size >= SUGGESTION_LIMIT ||
            readings.none { it.length <= MAX_FUZZY_READING_LENGTH } ||
            query.lowMemory
        ) {
            return cache(cacheKey, pinPreferredAlternate(ranked, preferredAlternate))
        }
        val fuzzy = ArrayList<CandidateRanker.FuzzyWord>(SUGGESTION_LIMIT)
        var fuzzyReadings = 0
        for (reading in readings) {
            if (reading.length > MAX_FUZZY_READING_LENGTH) continue
            if (fuzzyReadings >= MAX_FUZZY_READINGS || fuzzy.size >= SUGGESTION_LIMIT) break
            val budget = fuzzyEditBudget(reading.length)
                .coerceAtMost(if (fuzzyReadings == 0) 2 else 1)
            fuzzyReadings++
            dictionary.fuzzySuggestions(
                reading,
                budget,
                SUGGESTION_LIMIT,
                FIDEL_COST,
                AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST
            ).forEach { match ->
                fuzzy += CandidateRanker.FuzzyWord(match.word, match.frequency, match.editDistance)
            }
            if (fuzzy.size < SUGGESTION_LIMIT) {
                verbLexicon.fuzzy(
                    surface = reading,
                    maxEdits = budget,
                    limit = SUGGESTION_LIMIT - fuzzy.size,
                    substitutionCost = FIDEL_COST,
                    insertCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                    deleteCost = AmharicTable.DIFFERENT_CONSONANT_SUBSTITUTION_COST,
                ).forEach { match ->
                    fuzzy += CandidateRanker.FuzzyWord(
                        match.word,
                        match.frequency,
                        match.editDistance,
                    )
                }
            }
        }
        val rankedFuzzy = SuggestionTrace.section("candidate_ranking") {
            AmharicSuggestionPipeline.rank(
                context = pipeline,
                limit = SUGGESTION_LIMIT,
                frequencyOf = readingFrequencies::get,
                completionsForPrefix = completionsForPrefix,
                fuzzyWords = fuzzy,
                ngramNext = query.contextWeights,
                personalEvidence = personalEvidence,
            )
        }
        return cache(
            cacheKey,
            pinPreferredAlternate(rankedFuzzy, preferredAlternate)
        )
    }

    override fun cachedCompletion(query: CompletionQuery): List<String>? {
        if (query.raw.isEmpty() || !dictionary.isReady) return null
        val readings = AmharicSuggestionPipeline.prepare(query.raw).readings
        return suggestionCache[suggestionCacheKey(query, personalEvidence(readings, query))]
    }

    override fun commitCandidate(raw: String): String =
        AmharicCommitPolicy.resolve(raw, commitCache[raw])

    override fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion> =
        ngrams.predict(prev2, prev1, limit).map { EngineSuggestion(it.word, it.weight) }

    override fun topFrequentWords(limit: Int): List<EngineSuggestion> =
        ngrams.topFrequentWords(limit).map { EngineSuggestion(it.word, it.weight) }

    override fun normalize(word: String): String = EthiopicNormalizer.normalize(word)

    override fun morphologyIdentity(word: String): MorphologyIdentity? =
        verbLexicon.exact(word)?.bestAnalysis?.let {
            MorphologyIdentity(it.lemmaId, it.analysisId)
        }

    override fun clearCaches() {
        suggestionCache.clear()
        commitCache.clear()
        dictionary.clearCache()
        morphLexicon.clearCache()
        verbLexicon.clearCache()
    }

    override fun release() {
        clearCaches()
        ngrams.release()
    }

    private fun cache(
        cacheKey: SuggestionCacheKey,
        suggestions: List<String>,
    ): List<String> {
        if (dictionary.isReady) suggestionCache[cacheKey] = suggestions
        return suggestions
    }

    private fun personalEvidence(
        readings: List<String>,
        query: CompletionQuery,
    ): Map<String, CandidateRanker.PersonalEvidence> = buildMap {
        readings.distinct().forEach { reading ->
            query.personalCompletions.completions(reading, SUGGESTION_LIMIT).forEach { completion ->
                val key = EthiopicNormalizer.normalize(completion.word)
                val current = get(key)
                put(
                    key,
                    CandidateRanker.PersonalEvidence(
                        count = maxOf(current?.count ?: 0, completion.count),
                        recency = maxOf(current?.recency ?: 0, completion.recency),
                    )
                )
            }
        }
    }

    private fun suggestionCacheKey(
        query: CompletionQuery,
        personalEvidence: Map<String, CandidateRanker.PersonalEvidence>,
    ) = SuggestionCacheKey(
        modelVersion = "$RANKING_MODEL_VERSION:${store.modelVersion}",
        raw = query.raw,
        contextWeights = query.contextWeights.entries
            .sortedBy { it.key }
            .map { it.key to it.value },
        personalEvidence = personalEvidence.entries
            .sortedBy { it.key }
            .map { it.key to it.value },
        lowMemory = query.lowMemory,
    )

    private fun morphologyCompletions(
        typed: String,
        limit: Int,
        alreadyFound: List<CandidateRanker.AmharicCandidate>,
    ): List<CandidateRanker.AmharicCandidate> {
        val query = AmharicNounMorphology.query(typed)
        val lexemes = SuggestionTrace.section("nominal_lookup") {
            morphLexicon.nounEntries(
                exactSurfaces = query.exactStemSurfaces,
                completionPrefix = query.completionStemPrefix,
                limit = MORPH_LEXEME_LIMIT,
            ).map {
                AmharicNounMorphology.Lexeme(
                    kind = it.kind,
                    surface = it.surface,
                    features = it.features,
                    frequency = it.frequency,
                    lexemeId = it.lexemeId,
                    morphBits = it.morphBits,
                    stemClassCode = it.stemClass,
                )
            }
        }
        if (Thread.currentThread().isInterrupted) return emptyList()
        val nominal = SuggestionTrace.section("nominal_rule_graph") {
            AmharicNounMorphology.complete(typed, lexemes, limit, alreadyFound) { keys ->
                SuggestionTrace.section("surface_stats_lookup") {
                    morphLexicon.surfaceFrequencies(keys)
                }
            }
        }
        if (Thread.currentThread().isInterrupted) return emptyList()
        val seen = (alreadyFound.asSequence() + nominal.asSequence())
            .mapTo(HashSet()) { EthiopicNormalizer.normalize(it.word) }
        val verbs = SuggestionTrace.section("verb_automaton") {
            verbLexicon.complete(typed, limit).mapNotNull { terminal ->
                val key = EthiopicNormalizer.normalize(terminal.surface)
                if (!seen.add(key)) return@mapNotNull null
                val analysis = terminal.bestAnalysis
                CandidateRanker.AmharicCandidate(
                    word = terminal.surface,
                    source = CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    lexicalFrequency = analysis.rootFrequency,
                    morphologyCost = analysis.morphologyCost + when (analysis.sourceClass) {
                        com.addiyon.keyboard.suggestion.AmharicVerbSourceClass.REGULAR -> 1
                        com.addiyon.keyboard.suggestion.AmharicVerbSourceClass.IRREGULAR -> 2
                        com.addiyon.keyboard.suggestion.AmharicVerbSourceClass.LIGHT -> 3
                    },
                )
            }
        }
        return nominal + verbs
    }

    private fun pinPreferredAlternate(
        ranked: List<String>,
        preferredAlternate: String?
    ): List<String> {
        if (preferredAlternate == null || ranked.firstOrNull() == preferredAlternate) return ranked
        return ranked.toMutableList().apply {
            remove(preferredAlternate)
            add(minOf(1, size), preferredAlternate)
        }.take(SUGGESTION_LIMIT)
    }

    companion object {
        const val ID = "am-ET"
        private const val SUGGESTION_LIMIT = 15
        private const val CACHE_SIZE = 64
        private const val RANKING_MODEL_VERSION = 2
        private const val MAX_FUZZY_READING_LENGTH = 12
        private const val MAX_FUZZY_READINGS = 6
        private const val MORPH_LEXEME_LIMIT = 48
        private val FIDEL_COST = SubstitutionCost(AmharicTable::fidelSubstitutionCost)

        private fun fuzzyEditBudget(length: Int): Int = when {
            length <= 2 -> 0
            length <= 6 -> 1
            else -> 2
        }
    }
}
