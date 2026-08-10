package com.addiyon.keyboard.product

@JvmInline
value class ProductId private constructor(val value: String) {
    companion object {
        fun of(value: String): ProductId {
            val normalized = value.trim()
            require(normalized.matches(Regex("[a-z][a-z0-9-]{0,39}")))
            return ProductId(normalized)
        }
    }
}

enum class LanguageKeyBehavior {
    SWITCH_INSTALLED_PACK,
    SWITCH_TO_NEXT_INPUT_METHOD
}

data class KeyboardProduct(
    val id: ProductId,
    val defaultInputLanguageId: String,
    val orderedInputLanguageIds: List<String>,
    val languageKeyBehavior: LanguageKeyBehavior,
    val telemetryProductId: String
) {
    init {
        require(orderedInputLanguageIds.isNotEmpty())
        require(orderedInputLanguageIds.distinct().size == orderedInputLanguageIds.size)
        require(defaultInputLanguageId in orderedInputLanguageIds)
        require(telemetryProductId.matches(Regex("[a-z][a-z0-9_]{0,39}")))
    }
}
