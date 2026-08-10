package com.addiyon.keyboard.language.english

import com.addiyon.keyboard.composing.ResumableWord
import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.isComposingWordCharacter
import com.addiyon.keyboard.language.LanguageCapabilities
import com.addiyon.keyboard.language.LanguageContext
import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.LanguageTelemetryCategory
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine

class EnglishLanguagePack(
    override val suggestionEngine: LanguageSuggestionEngine
) : LanguagePack {
    override val id: LanguageId = LanguageId.of(EnglishSuggestionEngine.ID)
    override val localeTag: String = EnglishSuggestionEngine.ID
    override val displayName: String = "English"
    override val letterLayout: KeyboardLayout = EnglishLayout
    override val typingProfile: TypingProfile = TypingProfile(
        isWordCharacter = ::isComposingWordCharacter,
        wordEndingAtCursor = ResumableWord::latinWordEndingAtCursor
    )
    override val capabilities = LanguageCapabilities(hasLetterCase = true)
    override val voiceLocaleTag: String = EnglishSuggestionEngine.ID
    override val telemetryCategory = LanguageTelemetryCategory.ENGLISH
    override val contextReader: (CharSequence?) -> LanguageContext = { text ->
        EnglishNgramContext.extract(text).let { LanguageContext(it.prev2, it.prev1) }
    }
}
