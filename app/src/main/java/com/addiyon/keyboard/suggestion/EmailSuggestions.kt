package com.addiyon.keyboard.suggestion

/**
 * One chip on the email-domain suggestion strip. The label that the chip
 * displays and the text that the chip commits to the field can differ:
 *
 *   - Stage 1 (the user has not typed `@` yet) the chip shows "@gmail.com" but
 *     commits "jo@gmail.com" so the typed local part is preserved.
 *   - Stage 2 (`@` already in the typed token) the chip shows ".com" but
 *     commits "jo@mycomp.com", appending the suffix to whatever the user has
 *     already typed.
 *
 * WordComposer's [com.addiyon.keyboard.composing.WordComposer.commitSuggestion]
 * treats [commit] as the replacement text for the current composing region --
 * here that region is the full email token, so a chip tap ends up rewriting
 * the whole token plus a trailing space.
 */
data class EmailChip(val display: String, val commit: String)

/**
 * Computes the three email-domain chips to show above the keys while the
 * user is typing in an email field. Pure: takes the currently-composing
 * token and returns the chip list. Deterministic, no Android dependencies,
 * JVM-unit-testable.
 *
 * Stage 1 (token has no `@`): the chips are common email providers'
 * full domain suffixes, with the chip's [EmailChip.commit] being the typed
 * local part concatenated with the suffix.
 *
 * Stage 2 (token already contains `@`): the chips are generic TLDs the
 * user can append to whatever domain they have started typing -- the chip's
 * [EmailChip.commit] is the typed token concatenated with the suffix.
 *
 * Returns an empty list when [token] is blank or contains whitespace: the
 * email token is bounded by whitespace (it ends at the first space after
 * the start of typing), so a token spanning whitespace means we are not
 * currently in a single email token.
 */
object EmailSuggestions {

    private val DOMAIN_CHIPS = listOf(
        "@gmail.com",
        "@yahoo.com",
        "@outlook.com",
    )

    private val TLD_CHIPS = listOf(
        ".com",
        ".org",
        ".net",
    )

    fun emailChipsFor(token: String): List<EmailChip> {
        if (token.isBlank()) return emptyList()
        if (token.any { it.isWhitespace() }) return emptyList()
        val sources = if ('@' in token) TLD_CHIPS else DOMAIN_CHIPS
        return sources.map { EmailChip(display = it, commit = "$token$it") }
    }
}