package com.addiyon.keyboard

import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider
import com.addiyon.keyboard.language.amharic.AmharicLanguagePackProvider
import com.addiyon.keyboard.language.english.EnglishLanguagePackProvider
import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.product.LanguageKeyBehavior
import com.addiyon.keyboard.product.ProductId

internal val AddiyonLanguagePackProviders: List<AndroidLanguagePackProvider> = listOf(
    AmharicLanguagePackProvider,
    EnglishLanguagePackProvider
)

internal val AddiyonKeyboardProduct = KeyboardProduct(
    id = ProductId.of("addiyon"),
    defaultInputLanguageId = AmharicLanguagePackProvider.languageId.value,
    orderedInputLanguageIds = AddiyonLanguagePackProviders.map { it.languageId.value },
    languageKeyBehavior = LanguageKeyBehavior.SWITCH_INSTALLED_PACK
)
