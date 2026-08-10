package com.addiyon.keyboard.model

/**
 * The states the "123" / "=\<" / "፩፪" / "1234" keys move between:
 *
 * OFF           - a letter layout (Amharic or English) is showing.
 * NUMBERS       - the digits + common-symbols page ("123").
 * SYMBOLS       - the second, denser symbols page ("=\<").
 * MORE_SYMBOLS  - the third symbols page ("<>/") with additional punctuation.
 * GEEZ_NUMBERS  - Ge'ez numerals (፩-፼), present only when Amharic mode is active.
 * KEYPAD        - the phone-style digit pad. Engaged automatically
 *                 for number/phone/datetime fields (see
 *                 AddiyonKeyboardService.resolveKeypadMode) and manually via
 *                 the "1234" key on the NUMBERS page.
 *
 * "123"/"ABC" (NumberToggle) always jumps between OFF and NUMBERS, from any
 * numeric page (including the keypad) -- it never lands on SYMBOLS,
 * MORE_SYMBOLS, GEEZ_NUMBERS, or KEYPAD directly.
 * "=\<"/"<>/"/"፩፪"/"123" (SymbolsToggle) cycles through the numeric pages
 * when Amharic is on (NUMBERS -> GEEZ_NUMBERS -> SYMBOLS -> MORE_SYMBOLS ->
 * NUMBERS), and NUMBERS -> SYMBOLS -> MORE_SYMBOLS -> NUMBERS when Amharic is
 * off; from KEYPAD (where it reads "*#(") it exits to NUMBERS, and the
 * NUMBERS page's "1234" key (KeypadToggle) is the way back in.
 */
enum class NumbersMode {
    OFF,
    NUMBERS,
    SYMBOLS,
    MORE_SYMBOLS,
    GEEZ_NUMBERS,
    KEYPAD
}
