package com.addiyon.keyboard.model

/**
 * The states the "123" / "=\<" / "፩፪" keys move between:
 *
 * OFF           - a letter layout (Amharic or English) is showing.
 * NUMBERS       - the digits + common-symbols page ("123").
 * SYMBOLS       - the second, denser symbols page ("=\<").
 * MORE_SYMBOLS  - the third symbols page ("<>/") with additional punctuation.
 * GEEZ_NUMBERS  - Ge'ez numerals (፩-፼), present only when Amharic mode is active.
 *
 * "123"/"ABC" (NumberToggle) always jumps between OFF and NUMBERS, from
 * any numeric page -- it never lands on SYMBOLS, MORE_SYMBOLS, or GEEZ_NUMBERS directly.
 * "=\<"/"<>/"/"፩፪"/"123" (SymbolsToggle) cycles through the numeric pages
 * when Amharic is on (NUMBERS -> GEEZ_NUMBERS -> SYMBOLS -> MORE_SYMBOLS ->
 * NUMBERS), and NUMBERS -> SYMBOLS -> MORE_SYMBOLS -> NUMBERS when Amharic is off.
 */
enum class NumbersMode {
    OFF,
    NUMBERS,
    SYMBOLS,
    MORE_SYMBOLS,
    GEEZ_NUMBERS
}
