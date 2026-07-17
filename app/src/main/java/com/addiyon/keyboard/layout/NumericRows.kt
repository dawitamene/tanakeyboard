package com.addiyon.keyboard.layout

import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.NumbersMode

internal val LatinNumberRow: List<KeyData> = listOf(
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
)

internal val CommonSymbolsRow: List<KeyData> = listOf(
    KeyData.Character("@"),
    KeyData.Character("#"),
    KeyData.Character("$"),
    KeyData.Character("%"),
    KeyData.Character("&"),
    KeyData.Character("-"),
    KeyData.Character("+"),
    KeyData.Character("("),
    KeyData.Character(")"),
    KeyData.Character("/")
)

internal val ExtendedSymbolsRow: List<KeyData> = listOf(
    KeyData.Character("~"),
    KeyData.Character("`"),
    KeyData.Character("|"),
    KeyData.Character("•"),
    KeyData.Character("√"),
    KeyData.Character("π"),
    KeyData.Character("÷"),
    KeyData.Character("×"),
    KeyData.Character("°"),
    KeyData.Character("^")
)

internal fun numericRows(
    layout: KeyboardLayout,
    numbersMode: NumbersMode,
    numberRowEnabled: Boolean
): List<List<KeyData>> = when {
    !numberRowEnabled -> layout.rows
    numbersMode == NumbersMode.NUMBERS ->
        listOf(layout.rows[0], layout.rows[1], ExtendedSymbolsRow) + layout.rows.drop(2)
    numbersMode == NumbersMode.SYMBOLS ->
        listOf(layout.rows.first(), CommonSymbolsRow) + layout.rows.drop(1)
    numbersMode == NumbersMode.MORE_SYMBOLS -> listOf(LatinNumberRow) + layout.rows
    numbersMode == NumbersMode.GEEZ_NUMBERS -> listOf(LatinNumberRow) + layout.rows
    else -> layout.rows
}
