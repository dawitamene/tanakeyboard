package com.addiyon.tanakeyboard.model

/**
 * The states the "123" / "=\<" / "፩፪" keys move between:
 *
 * OFF           - a letter layout (Amharic or English) is showing.
 * NUMBERS       - the digits + common-symbols page ("123").
 * SYMBOLS       - the second, denser symbols page ("=\<"), reachable only
 *                 from NUMBERS.
 * GEEZ_NUMBERS  - Ge'ez numerals (፩-፼), reachable from SYMBOLS only when
 *                 Amharic mode is active.
 *
 * "123"/"ABC" (NumberToggle) always jumps between OFF and NUMBERS, from
 * any numeric page -- it never lands on SYMBOLS or GEEZ_NUMBERS directly.
 * "=\<"/"፩፪"/"123" (SymbolsToggle) cycles through the three numeric pages
 * when Amharic is on (NUMBERS -> SYMBOLS -> GEEZ_NUMBERS -> NUMBERS), and
 * the old two-page NUMBERS <-> SYMBOLS cycle when Amharic is off.
 */
enum class NumbersMode {
    OFF,
    NUMBERS,
    SYMBOLS,
    GEEZ_NUMBERS
}
