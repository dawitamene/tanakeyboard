package com.addiyon.keyboard.ui.settings

internal object PreferenceValueSanitizer {
    fun boolean(value: Any?, default: Boolean): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }
        else -> default
    }

    fun float(value: Any?, default: Float, minimum: Float, maximum: Float): Float {
        val parsed = when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
        return parsed
            ?.takeIf(Float::isFinite)
            ?.coerceIn(minimum, maximum)
            ?: default.coerceIn(minimum, maximum)
    }

    fun int(value: Any?, default: Int, minimum: Int, maximum: Int): Int {
        val parsed = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: return default.coerceIn(minimum, maximum)
        return parsed.coerceIn(minimum.toLong(), maximum.toLong()).toInt()
    }

    fun string(value: Any?, default: String?, maximumLength: Int): String? {
        val parsed = value as? String ?: return default
        return parsed.take(maximumLength.coerceAtLeast(0))
    }
}
