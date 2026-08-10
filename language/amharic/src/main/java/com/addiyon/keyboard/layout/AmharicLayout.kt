package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout

// Keys carry ONLY their Latin letter -- the fidel corner previews are
// computed live from Transliterator.transliterate (see CharacterKey), never
// baked in here, so they can't drift out of sync with what a key types.
val AmharicLayout = KeyboardLayout(

rows = listOf(

// Row 1
listOf(
KeyData.Character("Q"),
KeyData.Character("W"),
KeyData.Character("E"),
KeyData.Character("R"),
KeyData.Character("T"),
KeyData.Character("Y"),
KeyData.Character("U"),
KeyData.Character("I"),
KeyData.Character("O"),
KeyData.Character("P")
),

// Row 2
listOf(
KeyData.Character("A"),
KeyData.Character("S"),
KeyData.Character("D"),
KeyData.Character("F"),
KeyData.Character("G"),
KeyData.Character("H"),
KeyData.Character("J"),
KeyData.Character("K"),
KeyData.Character("L")
),

// Row 3
listOf(
KeyData.Shift,
KeyData.Character("Z"),
KeyData.Character("X"),
KeyData.Character("C"),
KeyData.Character("V"),
KeyData.Character("B"),
KeyData.Character("N"),
KeyData.Character("M"),
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
