package com.addiyon.tanakeyboard.suggestion

/**
 * Reconciles the case the user typed with a dictionary [word] that already
 * carries its own CANONICAL casing: "Th" + "the" -> "The", "THA" + "that" ->
 * "THAT", "th" + "that" -> "that", and -- crucially -- "engl" + "England" ->
 * "England" (the dictionary's proper-noun casing survives a lowercase prefix).
 *
 * The dictionary stores each word in canonical form (common words lowercase,
 * proper nouns capitalized -- see [WordTrie]), and lookups are
 * case-insensitive, so a suggestion chip must (a) preserve the capitalization
 * the user themselves typed -- tapping must not "correct" "Th|" into "the" --
 * while (b) otherwise keeping the word's own casing untouched, which is what
 * the `else` branch does by returning [word] verbatim.
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
