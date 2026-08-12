package com.addiyon.keyboard.language

import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine

@JvmInline
value class LanguageId private constructor(val value: String) {
    companion object {
        fun of(value: String): LanguageId {
            val normalized = value.trim()
            require(normalized.isNotEmpty())
            require(normalized.length <= 35)
            require(normalized.matches(Regex("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")))
            return LanguageId(normalized)
        }
    }

    override fun toString(): String = value
}

enum class LanguagePresentationCategory {
    AMHARIC,
    ENGLISH,
    OROMO,
    OTHER
}

data class LanguageCapabilities(
    val hasLetterCase: Boolean,
    val supportsGeezNumbers: Boolean = false,
    val supportsAutoCapitalization: Boolean = hasLetterCase,
    val providesSuggestions: Boolean = true
)

interface LanguagePack {
    val id: LanguageId
    val localeTag: String
    val displayName: String
    val letterLayout: KeyboardLayout
    val languageKeyLabel: String get() = displayName.take(2)
    val typingProfile: TypingProfile
    val capabilities: LanguageCapabilities
    val voiceLocaleTag: String?
    val presentationCategory: LanguagePresentationCategory
    val suggestionEngine: LanguageSuggestionEngine
    val contextReader: (CharSequence?) -> LanguageContext
    val alternateNumbersLayout: KeyboardLayout? get() = null
    val alternateNumbersLabel: String? get() = null
    fun cornerPreview(letter: String): String? = null
}

data class LanguageContext(val prev2: String?, val prev1: String?)
