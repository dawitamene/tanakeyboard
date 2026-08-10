package com.addiyon.keyboard.model

sealed class KeyData {

    // Deliberately just the one Latin letter -- the fidel corner preview is
    // NOT stored here. It's computed live from Transliterator.transliterate
    // in CharacterKey, so it always agrees with what tapping the key types;
    // a second baked-in glyph field is exactly what let the two drift apart.
    data class Character(
        val latin: String,
        val width: Float = 1f,
        val isSpecial: Boolean = false
    ) : KeyData()

    data object Shift : KeyData()

    data object Delete : KeyData()

    data object Space : KeyData()

    data object Enter : KeyData()

    data object NumberToggle : KeyData()

    data object SymbolsToggle : KeyData()

    /** The "1234" key on the numbers page: opens the phone-style keypad. */
    data object KeypadToggle : KeyData()

    data object LanguageToggle : KeyData()
}
