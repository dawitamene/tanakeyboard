package com.addiyon.keyboard.product

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardProductTest {
    @Test
    fun descriptorKeepsProductPolicyIndependentFromAndroidResources() {
        val product = KeyboardProduct(
            id = ProductId.of("addiyon"),
            defaultInputLanguageId = "am-ET",
            orderedInputLanguageIds = listOf("am-ET", "en-US"),
            languageKeyBehavior = LanguageKeyBehavior.SWITCH_INSTALLED_PACK,
            telemetryProductId = "addiyon"
        )

        assertEquals("am-ET", product.defaultInputLanguageId)
        assertEquals(listOf("am-ET", "en-US"), product.orderedInputLanguageIds)
    }
}
