package com.addiyon.keyboard.language.oromo

import com.addiyon.keyboard.composing.ResumableWord
import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.language.LanguageCapabilities
import com.addiyon.keyboard.language.LanguageContext
import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.LanguagePresentationCategory
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.LatinQwertyLayout
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine

class OromoLanguagePack : LanguagePack {
    override val id = LanguageId.of(ID)
    override val localeTag = ID
    override val displayName = "Afaan Oromoo"
    override val letterLayout: KeyboardLayout = LatinQwertyLayout
    override val languageKeyLabel = "AO"
    override val typingProfile = TypingProfile(
        isWordCharacter = { text -> text.all { it.isLetter() || it == '\u2019' || it == '\'' } },
        wordEndingAtCursor = ResumableWord::latinWordEndingAtCursor
    )
    override val capabilities = LanguageCapabilities(
        hasLetterCase = true,
        providesSuggestions = false
    )
    override val voiceLocaleTag: String = ID
    override val presentationCategory = LanguagePresentationCategory.OROMO
    override val suggestionEngine: LanguageSuggestionEngine = OromoSuggestionEngine
    override val contextReader: (CharSequence?) -> LanguageContext = { text ->
        val words = text?.toString().orEmpty()
            .trimEnd()
            .split(Regex("[^\\p{L}'’]+"))
            .filter(String::isNotEmpty)
        LanguageContext(words.getOrNull(words.lastIndex - 1), words.lastOrNull())
    }

    companion object { const val ID = "om-ET" }
}

private object OromoSuggestionEngine : LanguageSuggestionEngine {
    override val languageId = OromoLanguagePack.ID
    override val isReady = true
    override val isLoading = false
    override fun loadAsync(onReady: () -> Unit) = onReady()
    override fun complete(query: CompletionQuery): List<String> = emptyList()
    override fun predict(prev2: String?, prev1: String, limit: Int): List<EngineSuggestion> = emptyList()
    override fun topFrequentWords(limit: Int): List<EngineSuggestion> = emptyList()
    override fun normalize(word: String): String = word.lowercase()
    override fun clearCaches() = Unit
    override fun release() = Unit
}
