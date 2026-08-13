package com.addiyon.keyboard.language.english

import android.content.Context
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.suggestion.SQLiteDictionary
import com.addiyon.keyboard.suggestion.SQLiteLanguageStore
import com.addiyon.keyboard.suggestion.SQLiteNgramModel
import com.addiyon.keyboard.suggestion.matchCase

class EnglishSuggestionEngine(
    context: Context,
    isLowMemoryDevice: Boolean,
    onOutOfMemory: () -> Unit,
    onFailure: (Throwable, String) -> Unit,
    onWarning: (String) -> Unit
) : LanguageSuggestionEngine {
    private val store = SQLiteLanguageStore(
        context = context,
        assetName = "english.db",
        metadataAssetName = "english_dictionary_manifest.properties",
        isLowRam = isLowMemoryDevice,
        onOutOfMemory = onOutOfMemory,
        onFailure = onFailure,
        onWarning = onWarning
    )
    private val dictionary = SQLiteDictionary(store, 2, ::englishFold)
    private val ngrams = SQLiteNgramModel(store, ::englishFold)

    override val languageId: String = ID
    override val isReady: Boolean get() = store.isReady
    override val isLoading: Boolean get() = store.isLoading

    override fun loadAsync(onReady: () -> Unit) = store.loadAsync(onReady)

    override fun complete(query: CompletionQuery): List<String> {
        if (query.raw.isEmpty()) return emptyList()
        val key = query.raw.lowercase()
        val pool = dictionary.suggestionEntries(key, COMPLETION_POOL)
            .map { CandidateRanker.DictionaryWord(it.word, it.frequency) }
        val merged = ArrayList<String>(SUGGESTION_LIMIT)
        CandidateRanker.rankByContext(
            pool,
            query.contextWeights,
            ::englishFold,
            SUGGESTION_LIMIT
        ).forEach { word ->
            val cased = query.contextCasing[englishFold(word)] ?: word
            if (cased !in merged && merged.size < SUGGESTION_LIMIT) merged.add(cased)
        }
        if (merged.size < SUGGESTION_LIMIT && !query.lowMemory) {
            dictionary.fuzzySuggestions(key, fuzzyEditBudget(key.length), FUZZY_LIMIT)
                .filter { it.frequency >= FUZZY_MIN_FREQUENCY }
                .forEach { match ->
                    if (match.word !in merged && merged.size < SUGGESTION_LIMIT) {
                        merged.add(match.word)
                    }
                }
        }
        query.personalCompletions.completions(query.raw, COMPLETION_POOL).forEach { word ->
            if (merged.size < SUGGESTION_LIMIT) {
                val cased = matchCase(query.raw, word.word)
                if (cased !in merged) merged.add(cased)
            }
        }
        return merged.map { matchCase(query.raw, it) }
    }

    override fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion> =
        ngrams.predict(prev2, prev1, limit).map { EngineSuggestion(it.word, it.weight) }

    override fun topFrequentWords(limit: Int): List<EngineSuggestion> =
        ngrams.topFrequentWords(limit).map { EngineSuggestion(it.word, it.weight) }

    override fun normalize(word: String): String = englishFold(word)

    override fun clearCaches() = dictionary.clearCache()

    override fun release() {
        dictionary.clearCache()
        ngrams.release()
    }

    companion object {
        const val ID = "en-US"
        private const val SUGGESTION_LIMIT = 15
        private const val COMPLETION_POOL = 24
        private const val FUZZY_LIMIT = 2
        private const val FUZZY_MIN_FREQUENCY = 500

        fun englishFold(word: String): String =
            buildString(word.length) { word.forEach { append(it.lowercaseChar()) } }

        private fun fuzzyEditBudget(length: Int): Int = when {
            length <= 2 -> 0
            length <= 6 -> 1
            else -> 2
        }
    }
}
