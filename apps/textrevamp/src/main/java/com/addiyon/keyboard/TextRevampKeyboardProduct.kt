package com.addiyon.keyboard

import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider
import com.addiyon.keyboard.language.english.EnglishLanguagePackProvider
import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.product.LanguageKeyBehavior
import com.addiyon.keyboard.product.ProductId
import com.addiyon.keyboard.product.STANDARD_KEYBOARD_FEATURE_IDS
import com.addiyon.keyboard.product.TYPING_GUIDE_FEATURE_ID

internal val TextRevampLanguagePackProviders: List<AndroidLanguagePackProvider> = listOf(
    EnglishLanguagePackProvider
)

internal val TextRevampKeyboardProduct = KeyboardProduct(
    id = ProductId.of("textrevamp"),
    defaultInputLanguageId = EnglishLanguagePackProvider.languageId.value,
    orderedInputLanguageIds = TextRevampLanguagePackProviders.map { it.languageId.value },
    languageKeyBehavior = LanguageKeyBehavior.SWITCH_TO_NEXT_INPUT_METHOD,
    showsLanguageSwitchKey = false,
    featureIds = (STANDARD_KEYBOARD_FEATURE_IDS - TYPING_GUIDE_FEATURE_ID) + "ai"
)
