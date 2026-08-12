package com.addiyon.keyboard.language.android

import android.content.Context
import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguagePack

class AndroidLanguagePackEnvironment(
    val context: Context,
    val isLowMemoryDevice: Boolean,
    val onOutOfMemory: () -> Unit,
    val onFailure: (Throwable, String) -> Unit,
    val onWarning: (String) -> Unit
)

interface AndroidLanguagePackProvider {
    val languageId: LanguageId

    fun create(environment: AndroidLanguagePackEnvironment): LanguagePack
}
