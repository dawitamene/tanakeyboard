package com.addiyon.keyboard.ui

import androidx.compose.runtime.Immutable
import com.addiyon.keyboard.suggestion.EmailChip
import com.addiyon.keyboard.voice.VoiceUiState

@Immutable
sealed interface SuggestionUiState {
    data object Toolbar : SuggestionUiState

    data object LoadingLanguage : SuggestionUiState

    data object LoadingPredictions : SuggestionUiState

    /**
     * Word completions are being looked up for a word that has no previous
     * completions to keep showing. Renders the same blank three-slot strip as
     * [LoadingPredictions] so the row holds its shape instead of flashing the
     * toolbar icons between keystrokes.
     */
    data object LoadingCompletions : SuggestionUiState

    @Immutable
    data class WordCompletions(
        val words: List<String>,
        val actionGeneration: Long = 0L
    ) : SuggestionUiState {
        init {
            require(words.isNotEmpty())
        }
    }

    @Immutable
    data class NextWordPredictions(
        val words: List<String>,
        val actionGeneration: Long = 0L
    ) : SuggestionUiState {
        init {
            require(words.isNotEmpty())
        }
    }

    @Immutable
    data class EmailSuggestions(
        val chips: List<EmailChip>,
        val actionGeneration: Long = 0L
    ) : SuggestionUiState {
        init {
            require(chips.isNotEmpty())
        }
    }

    data object Private : SuggestionUiState

    @Immutable
    data class Voice(val voiceUiState: VoiceUiState) : SuggestionUiState

    companion object {
        fun completions(words: List<String>): SuggestionUiState =
            if (words.isEmpty()) Toolbar else WordCompletions(words.toList())

        fun predictions(words: List<String>): SuggestionUiState =
            if (words.isEmpty()) Toolbar else NextWordPredictions(words.toList())

        fun email(chips: List<EmailChip>): SuggestionUiState =
            if (chips.isEmpty()) Toolbar else EmailSuggestions(chips.toList())
    }
}

@Immutable
data class SuggestionTap(
    val word: String,
    val actionGeneration: Long
)
