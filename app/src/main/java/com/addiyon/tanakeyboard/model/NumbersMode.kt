package com.addiyon.tanakeyboard.model

/**
 * The three states the "123" / "=\<" keys move between:
 *
 * OFF     - a letter layout (Amharic or English) is showing.
 * NUMBERS - the digits + common-symbols page ("123").
 * SYMBOLS - the second, denser symbols page ("=\<"), reachable only from
 *           NUMBERS.
 *
 * "123"/"ABC" (NumberToggle) always jumps between OFF and NUMBERS, from
 * either numeric page -- it never lands on SYMBOLS directly. "=\<"/"123"
 * (SymbolsToggle) only ever moves between NUMBERS and SYMBOLS.
 */
enum class NumbersMode {
    OFF,
    NUMBERS,
    SYMBOLS
}
