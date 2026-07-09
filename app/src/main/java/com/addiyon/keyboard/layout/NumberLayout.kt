package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout

val NumberLayout = KeyboardLayout(

rows = listOf(

// Row 1
listOf(
KeyData.Character("1"),
KeyData.Character("2"),
KeyData.Character("3"),
KeyData.Character("4"),
KeyData.Character("5"),
KeyData.Character("6"),
KeyData.Character("7"),
KeyData.Character("8"),
KeyData.Character("9"),
KeyData.Character("0")
),

// Row 2
listOf(
KeyData.Character("@"),
KeyData.Character("#"),
KeyData.Character("$"),
KeyData.Character("%"),
KeyData.Character("&"),
KeyData.Character("-"),
KeyData.Character("+"),
KeyData.Character("("),
KeyData.Character(")")
),

// Row 3
listOf(
KeyData.SymbolsToggle,
KeyData.Character("*"),
KeyData.Character("\""),
KeyData.Character("'"),
KeyData.Character(":"),
KeyData.Character(";"),
KeyData.Character("!"),
KeyData.Character("?"),
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
