package com.addiyon.keyboard.transliteration

/**
 * Best-effort fidel -> Latin reversal, for adopting a committed Amharic word
 * back into composition when the caret lands on it (cursor-aware resume) and
 * this session's own commit history ([com.addiyon.keyboard.AddiyonKeyboardService]'s
 * `amharicCommitHistory`) doesn't already know it -- an earlier session's
 * word, or one pasted in.
 *
 * Forward transliteration isn't reliably invertible in general (see
 * [AmharicTable]'s and [WordTrie]'s class docs -- digraphs collapse, several
 * Latin spellings reach the same glyph), so this builds ONE plausible Latin
 * spelling per fidel character from [AmharicTable.families] (the family's
 * first-declared Latin key + the vowel spelling for that form's order index;
 * the labialized "ua" form -> key + "ua") and then ROUND-TRIP VERIFIES the
 * whole word: [Transliterator.transliterate] of the guess must reproduce the
 * exact original fidel. That guard is what makes an inherently ambiguous
 * reversal safe to use -- a wrong guess is simply rejected rather than
 * silently resuming into the wrong word, same as this class returning null
 * for a character it doesn't recognize at all (e.g. the velar ኀ series,
 * reachable only as a suggestion-strip alternate, never a keyboard spelling).
 */
object AmharicWordReverser {

    /**
     * fidel char -> a Latin spelling that transliterates back to it in
     * isolation. Built once from [AmharicTable.families]: iteration order is
     * the map's declaration order (base families before the x/c keyboard
     * aliases), and [putIfAbsent]-style first-wins registration means a
     * glyph shared by two spellings (e.g. "ph"/"P", both ጰ's family) settles
     * on whichever is declared first, deterministically.
     */
    private val reverseMap: Map<Char, String> = buildMap {
        for ((key, family) in AmharicTable.families) {
            for ((index, char) in family.forms.withIndex()) {
                val spelling = if (index == AmharicTable.BARE_FORM_INDEX) {
                    key
                } else {
                    val vowelSpelling = AmharicTable.vowels.first { it.second == index }.first
                    key + vowelSpelling
                }
                if (char !in this) put(char, spelling)
            }
            family.ua?.let { uaChar ->
                if (uaChar !in this) put(uaChar, key + "ua")
            }
        }
    }

    /**
     * A Latin spelling that round-trips to exactly [fidel], or null if any
     * character isn't reachable via [reverseMap] or the assembled guess
     * doesn't verify.
     */
    fun reverse(fidel: String): String? {
        if (fidel.isEmpty()) return null
        val guess = StringBuilder(fidel.length)
        for (char in fidel) {
            guess.append(reverseMap[char] ?: return null)
        }
        val latin = guess.toString()
        return if (Transliterator.transliterate(latin) == fidel) latin else null
    }
}
