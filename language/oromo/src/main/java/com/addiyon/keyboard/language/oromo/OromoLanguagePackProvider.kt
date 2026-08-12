package com.addiyon.keyboard.language.oromo

import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.android.AndroidLanguagePackEnvironment
import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider

object OromoLanguagePackProvider : AndroidLanguagePackProvider {
    override val languageId: LanguageId = LanguageId.of(OromoLanguagePack.ID)

    override fun create(environment: AndroidLanguagePackEnvironment): OromoLanguagePack =
        OromoLanguagePack()
}
