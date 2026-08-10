package com.addiyon.keyboard

import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.product.LanguageKeyBehavior
import com.addiyon.keyboard.product.ProductId

internal val AddiyonKeyboardProduct = KeyboardProduct(
    id = ProductId.of("addiyon"),
    defaultInputLanguageId = "am-ET",
    orderedInputLanguageIds = listOf("am-ET", "en-US"),
    languageKeyBehavior = LanguageKeyBehavior.SWITCH_INSTALLED_PACK,
    telemetryProductId = "addiyon"
)
