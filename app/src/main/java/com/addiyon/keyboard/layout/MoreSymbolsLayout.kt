package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout

val MoreSymbolsLayout = KeyboardLayout(

    rows = listOf(

        listOf(
            KeyData.Character("["),
            KeyData.Character("]"),
            KeyData.Character("<"),
            KeyData.Character(">"),
            KeyData.Character("/"),
            KeyData.Character("\\"),
            KeyData.Character("_"),
            KeyData.Character("§"),
            KeyData.Character("¶"),
            KeyData.Character("✓")
        ),

        listOf(
            KeyData.Character("©"),
            KeyData.Character("®"),
            KeyData.Character("™"),
            KeyData.Character("♪"),
            KeyData.Character("♥"),
            KeyData.Character("★"),
            KeyData.Character("±"),
            KeyData.Character("∞"),
            KeyData.Character("≠"),
            KeyData.Character("≈")
        ),

        listOf(
            KeyData.SymbolsToggle,
            KeyData.Character("¿"),
            KeyData.Character("¡"),
            KeyData.Character("«"),
            KeyData.Character("»"),
            KeyData.Character("‹"),
            KeyData.Character("›"),
            KeyData.Character("…"),
            KeyData.Delete
        ),

        listOf(
            KeyData.NumberToggle,
            KeyData.Character(","),
            KeyData.LanguageToggle,
            KeyData.Space,
            KeyData.Character("."),
            KeyData.Enter
        )
    )
)
