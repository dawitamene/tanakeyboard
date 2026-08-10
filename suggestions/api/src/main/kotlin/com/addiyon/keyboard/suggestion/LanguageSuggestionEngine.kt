package com.addiyon.keyboard.suggestion

data class EngineSuggestion(val word: String, val weight: Int)

fun interface PersonalCompletionSource {
    fun completions(prefix: String, limit: Int): List<String>
}

data class CompletionQuery(
    val raw: String,
    val contextWeights: Map<String, Int> = emptyMap(),
    val contextCasing: Map<String, String> = emptyMap(),
    val personalCompletions: PersonalCompletionSource = PersonalCompletionSource { _, _ -> emptyList() },
    val lowMemory: Boolean = false
)

interface LanguageSuggestionEngine {
    val languageId: String
    val isReady: Boolean
    val isLoading: Boolean

    fun loadAsync(onReady: () -> Unit)
    fun complete(query: CompletionQuery): List<String>
    fun commitCandidate(raw: String): String = raw
    fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion>
    fun topFrequentWords(limit: Int): List<EngineSuggestion>
    fun normalize(word: String): String
    fun clearCaches()
    fun release()
}
