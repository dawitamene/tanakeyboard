package com.addiyon.keyboard.language.amharic

import android.content.Context
import com.addiyon.keyboard.suggestion.AmharicCommitPolicy
import com.addiyon.keyboard.suggestion.AmharicNounMorphology
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.suggestion.SQLiteDictionary
import com.addiyon.keyboard.suggestion.SQLiteLanguageStore
import com.addiyon.keyboard.suggestion.SQLiteMorphLexicon
import com.addiyon.keyboard.suggestion.SQLiteNgramModel
import com.addiyon.keyboard.suggestion.SubstitutionCost
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
    private val suggestionCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<String>>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) =
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
        if (latin.isEmpty()) return emptyList()
        if (dictionary.isReady) suggestionCache[latin]?.let { return it }
        val pipeline = AmharicSuggestionPipeline.prepare(latin)
        val readings = pipeline.readings
        val readingFrequencies = dictionary.frequenciesOf(readings)
        val quirkReadings = pipeline.quirkReadings
        val personal = buildList {
            readings.distinct().forEach { reading ->
                query.personalCompletions.completions(reading, SUGGESTION_LIMIT).forEach { word ->
                    if (word !in this && size < SUGGESTION_LIMIT) add(word)
                }
            }
        }
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
        val preferredAlternate = (
            Transliterator.vowelAlternateReading(latin)
                ?: Transliterator.bareVowelAlternateReading(latin)
            )?.takeIf {
            it.length > 1 && dictionary.isReady && readingFrequencies.containsKey(it)
        }
        val directCompletionCache = directCompletions.mapValuesTo(HashMap()) { (_, entries) ->
            entries.map { CandidateRanker.DictionaryWord(it.word, it.frequency) }
        }
        val completionCache = HashMap<String, List<CandidateRanker.DictionaryWord>>()
        val morphologyCache = HashMap<String, List<CandidateRanker.DictionaryWord>>()
        val dictionaryLookup = { prefix: String, limit: Int ->
            dictionary.suggestionEntries(prefix, limit).map {
                CandidateRanker.DictionaryWord(it.word, it.frequency)
            }
        }
        val completionsForPrefix = { prefix: String, limit: Int ->
            completionCache.getOrPut(prefix) {
                val direct = directCompletionCache[prefix] ?: dictionaryLookup(prefix, limit)
                val generated = morphologyCache.getOrPut(prefix) {
                    morphologyCompletions(prefix, limit, direct)
                }
                direct + generated
            }
        }
        val ranked = AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = SUGGESTION_LIMIT,
            frequencyOf = readingFrequencies::get,
            completionsForPrefix = completionsForPrefix,
            ngramNext = query.contextWeights,
        )
        val rankedWithPersonal = (ranked + personal).distinct().take(SUGGESTION_LIMIT)
        if (
            rankedWithPersonal.size >= SUGGESTION_LIMIT ||
            readings.none { it.length <= MAX_FUZZY_READING_LENGTH } ||
            query.lowMemory
        ) {
            return cache(latin, pinPreferredAlternate(rankedWithPersonal, preferredAlternate))
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
        }
        val rankedFuzzy = AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = SUGGESTION_LIMIT,
            frequencyOf = readingFrequencies::get,
            completionsForPrefix = completionsForPrefix,
            fuzzyWords = fuzzy,
            ngramNext = query.contextWeights,
        )
        return cache(
            latin,
            pinPreferredAlternate((rankedFuzzy + personal).distinct().take(SUGGESTION_LIMIT), preferredAlternate)
        )
    }

    override fun commitCandidate(raw: String): String =
        AmharicCommitPolicy.resolve(raw, commitCache[raw])

    override fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion> =
        ngrams.predict(prev2, prev1, limit).map { EngineSuggestion(it.word, it.weight) }

    override fun topFrequentWords(limit: Int): List<EngineSuggestion> =
        ngrams.topFrequentWords(limit).map { EngineSuggestion(it.word, it.weight) }

    override fun normalize(word: String): String = EthiopicNormalizer.normalize(word)

    override fun clearCaches() {
        suggestionCache.clear()
        commitCache.clear()
        dictionary.clearCache()
    }

    override fun release() {
        clearCaches()
        ngrams.release()
    }

    private fun cache(raw: String, suggestions: List<String>): List<String> {
        if (dictionary.isReady) suggestionCache[raw] = suggestions
        return suggestions
    }

    private fun morphologyCompletions(
        typed: String,
        limit: Int,
        alreadyFound: List<CandidateRanker.DictionaryWord>,
    ): List<CandidateRanker.DictionaryWord> {
        val query = AmharicNounMorphology.query(typed)
        val lexemes = morphLexicon.nounEntries(
            exactSurfaces = query.exactStemSurfaces,
            completionPrefix = query.completionStemPrefix,
            limit = MORPH_LEXEME_LIMIT,
        ).map {
            AmharicNounMorphology.Lexeme(
                kind = it.kind,
                surface = it.surface,
                features = it.features,
                frequency = it.frequency,
            )
        }
        return AmharicNounMorphology.complete(typed, lexemes, limit, alreadyFound)
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
