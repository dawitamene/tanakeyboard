package com.addiyon.tanakeyboard.model

sealed class KeyData {

    data class Character(
        val latin: String,
        val amharic: String? = null
    ) : KeyData()

    data object Shift : KeyData()

    data object Delete : KeyData()

    data object Space : KeyData()

    data object Enter : KeyData()

    data object NumberToggle : KeyData()

    data object SymbolsToggle : KeyData()

    data object LanguageToggle : KeyData()
}