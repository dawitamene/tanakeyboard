package com.addiyon.keyboard.language.english

import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.android.AndroidLanguagePackEnvironment
import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider

object EnglishLanguagePackProvider : AndroidLanguagePackProvider {
    override val languageId: LanguageId = LanguageId.of(EnglishSuggestionEngine.ID)

    override fun create(environment: AndroidLanguagePackEnvironment): EnglishLanguagePack =
        EnglishLanguagePack(
            EnglishSuggestionEngine(
                context = environment.context,
                isLowMemoryDevice = environment.isLowMemoryDevice,
                onOutOfMemory = environment.onOutOfMemory,
                onFailure = environment.onFailure,
                onWarning = environment.onWarning
            )
        )
}
