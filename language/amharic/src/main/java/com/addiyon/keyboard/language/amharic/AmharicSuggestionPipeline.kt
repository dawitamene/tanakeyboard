package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import com.addiyon.keyboard.transliteration.Transliterator

object AmharicSuggestionPipeline {
    data class Context(
        val readings: List<String>,
        val quirkReadings: Set<String>,
        val preferGreedy: Boolean,
    )

    fun prepare(raw: String): Context {
        val candidates = Transliterator.candidateReadings(raw)
        return Context(
            readings = candidates.map { it.text },
            quirkReadings = candidates.filterTo(linkedSetOf()) { it.isQuirk }.mapTo(linkedSetOf()) { it.text },
            preferGreedy = Transliterator.hasExplicitFamilySelection(raw),
        )
    }

    fun rank(
        context: Context,
        limit: Int,
        frequencyOf: (String) -> Int?,
        completionsForPrefix: (String, Int) -> List<CandidateRanker.DictionaryWord>,
        fuzzyWords: List<CandidateRanker.FuzzyWord> = emptyList(),
        ngramNext: Map<String, Int> = emptyMap(),
    ): List<String> = CandidateRanker.rankAmharic(
        readings = context.readings,
        limit = limit,
        frequencyOf = frequencyOf,
        completionsForPrefix = completionsForPrefix,
        visibleReadings = context.readings,
        fuzzyWords = fuzzyWords,
        quirkReadings = context.quirkReadings,
        ngramNext = ngramNext,
        preferGreedy = context.preferGreedy,
        normalize = EthiopicNormalizer::normalize,
    )
}
