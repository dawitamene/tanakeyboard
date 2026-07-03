package com.addiyon.tanakeyboard.layout

import com.addiyon.tanakeyboard.model.KeyData
import com.addiyon.tanakeyboard.model.KeyboardLayout

val AmharicLayout = KeyboardLayout(

rows = listOf(

// Row 1
listOf(
KeyData.Character("Q", "ቀ"),
KeyData.Character("W", "ወ"),
KeyData.Character("E", "እ"),
KeyData.Character("R", "ረ"),
KeyData.Character("T", "ተ"),
KeyData.Character("Y", "የ"),
KeyData.Character("U", "ኡ"),
KeyData.Character("I", "ኢ"),
KeyData.Character("O", "ኦ"),
KeyData.Character("P", "ፐ")
),

// Row 2
listOf(
KeyData.Character("A", "አ"),
KeyData.Character("S", "ሰ"),
KeyData.Character("D", "ደ"),
KeyData.Character("F", "ፈ"),
KeyData.Character("G", "ገ"),
KeyData.Character("H", "ሀ"),
KeyData.Character("J", "ጀ"),
KeyData.Character("K", "ከ"),
KeyData.Character("L", "ለ")
),

// Row 3
listOf(
KeyData.Shift,
KeyData.Character("Z", "ዘ"),
KeyData.Character("X", "ሸ"),
KeyData.Character("C", "ቸ"),
KeyData.Character("V", "ቨ"),
KeyData.Character("B", "በ"),
KeyData.Character("N", "ነ"),
KeyData.Character("M", "መ"),
KeyData.Delete
),

// Row 4
listOf(
KeyData.NumberToggle,
KeyData.Character(",", "፣"),
KeyData.LanguageToggle,
KeyData.Space,
KeyData.Character(".", "።"),
KeyData.Enter
)
)

)
