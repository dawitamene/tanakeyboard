package com.addiyon.keyboard.model

val LatinQwertyLayout = KeyboardLayout(
    rows = listOf(
        "QWERTYUIOP".map { KeyData.Character(it.toString()) },
        "ASDFGHJKL".map { KeyData.Character(it.toString()) },
        listOf(KeyData.Shift) + "ZXCVBNM".map { KeyData.Character(it.toString()) } + KeyData.Delete,
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
