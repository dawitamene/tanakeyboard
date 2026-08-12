package com.addiyon.keyboard.model

val StandardLatinNumberRow = "1234567890".map { KeyData.Character(it.toString()) }

val StandardCommonSymbolsRow = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    .map { KeyData.Character(it) }

val StandardExtendedSymbolsRow = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "°", "^")
    .map { KeyData.Character(it) }

val StandardNumberLayout = KeyboardLayout(
    rows = listOf(
        StandardLatinNumberRow,
        StandardCommonSymbolsRow,
        listOf(KeyData.SymbolsToggle) +
            listOf("*", "\"", "'", ":", ";", "!", "?").map { KeyData.Character(it) } +
            KeyData.Delete,
        listOf(
            KeyData.NumberToggle,
            KeyData.Character(","),
            KeyData.KeypadToggle,
            KeyData.Space,
            KeyData.Character("."),
            KeyData.Enter
        )
    )
)

val StandardSymbolsLayout = KeyboardLayout(
    rows = listOf(
        StandardLatinNumberRow,
        StandardExtendedSymbolsRow,
        listOf(KeyData.SymbolsToggle) +
            listOf("£", "¢", "€", "¥", "=", "{", "}").map { KeyData.Character(it) } +
            KeyData.Delete,
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

val StandardMoreSymbolsLayout = KeyboardLayout(
    rows = listOf(
        listOf("[", "]", "<", ">", "/", "\\", "_", "§", "¶", "✓")
            .map { KeyData.Character(it) },
        listOf("©", "®", "™", "♪", "♥", "★", "±", "∞", "≠", "≈")
            .map { KeyData.Character(it) },
        listOf(KeyData.SymbolsToggle) +
            listOf("¿", "¡", "«", "»", "‹", "›", "…").map { KeyData.Character(it) } +
            KeyData.Delete,
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

val StandardKeypadLayout = KeyboardLayout(
    columns = 5.3f,
    rowColumns = listOf(5.3f, 5.3f, 5.3f, 5.3f),
    rows = listOf(
        listOf(
            KeyData.Character("+", 0.7f, true),
            KeyData.Character("1", 1.3f),
            KeyData.Character("2", 1.3f),
            KeyData.Character("3", 1.3f),
            KeyData.Character("/", 0.7f, true)
        ),
        listOf(
            KeyData.Character("-", 0.7f, true),
            KeyData.Character("4", 1.3f),
            KeyData.Character("5", 1.3f),
            KeyData.Character("6", 1.3f),
            KeyData.Space
        ),
        listOf(
            KeyData.Character("*", 0.7f, true),
            KeyData.Character("7", 1.3f),
            KeyData.Character("8", 1.3f),
            KeyData.Character("9", 1.3f),
            KeyData.Delete
        ),
        listOf(
            KeyData.NumberToggle,
            KeyData.Character(",", 0.55f, true),
            KeyData.SymbolsToggle,
            KeyData.Character("0", 1.3f),
            KeyData.Character("=", 0.65f, true),
            KeyData.Character(".", 0.65f, true),
            KeyData.Enter
        )
    )
)

fun standardNumericRows(
    layout: KeyboardLayout,
    numbersMode: NumbersMode,
    numberRowEnabled: Boolean
): List<List<KeyData>> = when {
    !numberRowEnabled -> layout.rows
    numbersMode == NumbersMode.NUMBERS ->
        listOf(layout.rows[0], layout.rows[1], StandardExtendedSymbolsRow) + layout.rows.drop(2)
    numbersMode == NumbersMode.SYMBOLS ->
        listOf(layout.rows.first(), StandardCommonSymbolsRow) + layout.rows.drop(1)
    numbersMode == NumbersMode.MORE_SYMBOLS || numbersMode == NumbersMode.GEEZ_NUMBERS ->
        listOf(StandardLatinNumberRow) + layout.rows
    else -> layout.rows
}
