package com.addiyon.tanakeyboard.layout

import com.addiyon.tanakeyboard.model.KeyData
import com.addiyon.tanakeyboard.model.KeyboardLayout

val GeezNumbersLayout = KeyboardLayout(

    rows = listOf(

        // Row 1 — ones + hundred
        listOf(
            KeyData.Character("፩"),
            KeyData.Character("፪"),
            KeyData.Character("፫"),
            KeyData.Character("፬"),
            KeyData.Character("፭"),
            KeyData.Character("፮"),
            KeyData.Character("፯"),
            KeyData.Character("፰"),
            KeyData.Character("፱"),
            KeyData.Character("፻")
        ),

        // Row 2 — tens + ten-thousand
        listOf(
            KeyData.Character("፲"),
            KeyData.Character("፳"),
            KeyData.Character("፴"),
            KeyData.Character("፵"),
            KeyData.Character("፶"),
            KeyData.Character("፷"),
            KeyData.Character("፸"),
            KeyData.Character("፹"),
            KeyData.Character("፺"),
            KeyData.Character("፼")
        ),

        // Row 3 — Ethiopic punctuation + toggle back + delete
        listOf(
            KeyData.SymbolsToggle,
            KeyData.Character("፡"),
            KeyData.Character("።"),
            KeyData.Character("፣"),
            KeyData.Character("፤"),
            KeyData.Character("፥"),
            KeyData.Character("፦"),
            KeyData.Character("/"),
            KeyData.Delete
        ),

        // Row 4
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
