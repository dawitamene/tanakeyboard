package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout

val KeypadLayout = KeyboardLayout(
    columns = 5.3f,
    rowColumns = listOf(5.3f, 5.3f, 5.3f, 5.3f),
    rows = listOf(
        listOf(
            KeyData.Character("+", width = 0.7f, isSpecial = true),
            KeyData.Character("1", width = 1.3f),
            KeyData.Character("2", width = 1.3f),
            KeyData.Character("3", width = 1.3f),
            KeyData.Character("/", width = 0.7f, isSpecial = true)
        ),
        listOf(
            KeyData.Character("-", width = 0.7f, isSpecial = true),
            KeyData.Character("4", width = 1.3f),
            KeyData.Character("5", width = 1.3f),
            KeyData.Character("6", width = 1.3f),
            KeyData.Space
        ),
        listOf(
            KeyData.Character("*", width = 0.7f, isSpecial = true),
            KeyData.Character("7", width = 1.3f),
            KeyData.Character("8", width = 1.3f),
            KeyData.Character("9", width = 1.3f),
            KeyData.Delete
        ),
        listOf(
            KeyData.NumberToggle,
            KeyData.Character(",", width = 0.55f, isSpecial = true),
            KeyData.SymbolsToggle,
            KeyData.Character("0", width = 1.3f),
            KeyData.Character("=", width = 0.65f, isSpecial = true),
            KeyData.Character(".", width = 0.65f, isSpecial = true),
            KeyData.Enter
        )
    )
)
