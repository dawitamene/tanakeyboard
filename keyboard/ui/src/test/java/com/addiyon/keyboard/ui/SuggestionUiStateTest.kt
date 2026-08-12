package com.addiyon.keyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionUiStateTest {
    @Test
    fun fifteenCompletionsLeaveTwelveForTheExpandedView() {
        val suggestions = (1..SUGGESTION_LIST_LIMIT).map { "suggestion$it" }

        val remaining = remainingSuggestions(SuggestionUiState.WordCompletions(suggestions))

        assertEquals(SUGGESTION_LIST_LIMIT - SUGGESTION_STRIP_VISIBLE_LIMIT, remaining.size)
        assertEquals(suggestions.drop(SUGGESTION_STRIP_VISIBLE_LIMIT), remaining)
    }

    @Test
    fun fifteenPredictionsLeaveTwelveForTheExpandedView() {
        val suggestions = (1..SUGGESTION_LIST_LIMIT).map { "prediction$it" }

        val remaining = remainingSuggestions(SuggestionUiState.NextWordPredictions(suggestions))

        assertEquals(SUGGESTION_LIST_LIMIT - SUGGESTION_STRIP_VISIBLE_LIMIT, remaining.size)
        assertEquals(suggestions.drop(SUGGESTION_STRIP_VISIBLE_LIMIT), remaining)
    }
}
