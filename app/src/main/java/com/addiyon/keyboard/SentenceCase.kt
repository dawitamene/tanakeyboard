package com.addiyon.keyboard

/**
 * Decides whether the caret sits at the start of a new sentence, so English
 * mode can auto-capitalize the next letter (a one-shot shift). Pure (no
 * Android) for JVM testing; the service feeds it `getTextBeforeCursor`.
 *
 * A new sentence starts at the very beginning of the field (nothing but
 * whitespace before the caret), at the start of a fresh line, or after a
 * sentence terminator (. ! ?) followed by whitespace. Requiring that trailing
 * space keeps mid-token dots ("www.google", "3.14") from triggering a capital
 * -- the cap instead fires once the user types the space that actually ends
 * the sentence. Abbreviations ("e.g. ") are still treated as sentence ends,
 * the same limitation every keyboard's auto-capitalization has.
 *
 * NULL is not EMPTY: an empty CharSequence means the editor reported "no
 * text before the caret" (genuinely at the start), while null means the
 * editor couldn't be read at all -- getTextBeforeCursor commonly returns
 * null right as the keyboard opens, or in apps that withhold surrounding
 * text. The caret could be anywhere in an unreadable field (including the
 * middle of a sentence), so never guess a capital from null.
 *
 * Amharic never calls this: Ge'ez has no letter case, and its shift key
 * selects a different consonant family instead.
 */
object SentenceCase {

    fun startsNewSentence(textBeforeCursor: CharSequence?): Boolean {
        val text = textBeforeCursor ?: return false
        if (text.isEmpty()) return true
        val last = text[text.length - 1]
        // A brand-new line is always a sentence start.
        if (last == '\n' || last == '\r') return true
        // Otherwise the caret must sit just past whitespace; touching a
        // non-space character means we're inside/at the end of a token.
        if (last != ' ' && last != '\t') return false
        var i = text.length - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        if (i < 0) return true // only whitespace before -> start of field/line
        return when (text[i]) {
            '.', '!', '?', '\n', '\r', '…' -> true
            else -> false
        }
    }
}
