package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.android.AndroidLanguagePackEnvironment
import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider

object AmharicLanguagePackProvider : AndroidLanguagePackProvider {
    override val languageId: LanguageId = LanguageId.of(AmharicSuggestionEngine.ID)

    override fun create(environment: AndroidLanguagePackEnvironment): AmharicLanguagePack =
        AmharicLanguagePack(
            AmharicSuggestionEngine(
                context = environment.context,
                isLowMemoryDevice = environment.isLowMemoryDevice,
                onOutOfMemory = environment.onOutOfMemory,
                onFailure = environment.onFailure,
                onWarning = environment.onWarning
            )
        )
}
