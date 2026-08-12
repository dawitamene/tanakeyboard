package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.composing.ResumableWord
import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.isComposingWordCharacter
import com.addiyon.keyboard.language.LanguageCapabilities
import com.addiyon.keyboard.language.LanguageContext
import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.LanguagePresentationCategory
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.GeezNumbersLayout
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import com.addiyon.keyboard.transliteration.Transliterator

class AmharicLanguagePack(
    override val suggestionEngine: LanguageSuggestionEngine
) : LanguagePack {
    override val id: LanguageId = LanguageId.of(AmharicSuggestionEngine.ID)
    override val localeTag: String = AmharicSuggestionEngine.ID
    override val displayName: String = "አማርኛ"
    override val letterLayout: KeyboardLayout = AmharicLayout
    override val languageKeyLabel: String = "ሀለ"
    override val alternateNumbersLayout: KeyboardLayout = GeezNumbersLayout
    override val alternateNumbersLabel: String = "፩፪"
    override val typingProfile: TypingProfile = TypingProfile(
        isWordCharacter = ::isComposingWordCharacter,
        commitTransform = suggestionEngine::commitCandidate,
        transformStandalone = Transliterator::transliterate,
        wordEndingAtCursor = ResumableWord::amharicWordEndingAtCursor,
        remembersRawLatin = true
    )
    override val capabilities = LanguageCapabilities(
        hasLetterCase = false,
        supportsGeezNumbers = true,
        supportsAutoCapitalization = false
    )
    override val voiceLocaleTag: String = AmharicSuggestionEngine.ID
    override val presentationCategory = LanguagePresentationCategory.AMHARIC
    override val contextReader: (CharSequence?) -> LanguageContext = { text ->
        AmharicNgramContext.extract(text).let { LanguageContext(it.prev2, it.prev1) }
    }

    override fun cornerPreview(letter: String): String? =
        Transliterator.transliterate(letter).takeIf { it != letter }
}
