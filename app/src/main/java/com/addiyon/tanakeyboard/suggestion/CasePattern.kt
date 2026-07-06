package com.addiyon.tanakeyboard.suggestion

/**
 * Re-applies the case pattern of the user's typed prefix to a lowercase
 * dictionary word: "Th" + "the" -> "The", "THA" + "that" -> "THAT",
 * "th" + "that" -> "that".
 *
 * Needed because the English dictionary stores every entry lowercase (its
 * source corpus is lowercased), so lookups happen against the lowercased
 * prefix -- but a suggestion chip must not silently discard the
 * capitalization the user already typed, or tapping it would "correct"
 * "Th|" into "the".
 *
 * The all-caps rule deliberately requires at least TWO typed uppercase
 * letters (not merely "no lowercase"): a single "T" -- or "I'" with its
 * apostrophe -- is just a shifted first letter, by far the common case at
 * sentence starts, and means title-case, not that the user wants "THE".
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable without an
 * emulator, same reasoning as [WordTrie].
 */
fun matchCase(typed: String, word: String): String = when {
    typed.count { it.isUpperCase() } > 1 &&
        typed.none { it.isLowerCase() } -> word.uppercase()

    typed.firstOrNull()?.isUpperCase() == true ->
        word.replaceFirstChar { it.uppercase() }

    else -> word
}
