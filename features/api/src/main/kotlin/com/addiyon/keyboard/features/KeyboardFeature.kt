package com.addiyon.keyboard.features

@JvmInline
value class KeyboardFeatureId private constructor(val value: String) {
    companion object {
        fun of(value: String): KeyboardFeatureId {
            val normalized = value.trim()
            require(normalized.matches(Regex("[a-z][a-z0-9-]{0,39}")))
            return KeyboardFeatureId(normalized)
        }
    }
}

interface KeyboardFeature {
    val id: KeyboardFeatureId
}
