package com.addiyon.keyboard.transliteration

import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.model.KeyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransliteratorTest {

    @Test
    fun wholeWordRegression() {
        assertEquals("ሰላም", Transliterator.transliterate("selam"))
    }

    // ----- keyboard aliases: the X and C keys ---------------------------
    //
    // SERA assigns single letters to two digraph families ("x" = sh = ሸ,
    // "c" = ch = ቸ). Before these aliases existed in AmharicTable.families,
    // "x" passed through as literal Latin and "c" fell through
    // case-insensitively to the "C" (ጨ) family -- while the key previews
    // claimed otherwise.

    @Test
    fun lowercaseCIsCheFamily() {
        assertEquals("ች", Transliterator.transliterate("c"))
        assertEquals("ቸ", Transliterator.transliterate("ce"))
        assertEquals("ቻ", Transliterator.transliterate("ca"))
    }

    @Test
    fun uppercaseCStaysEjectiveFamily() {
        assertEquals("ጭ", Transliterator.transliterate("C"))
        assertEquals("ጨ", Transliterator.transliterate("Ce"))
    }

    @Test
    fun xIsShFamilyInBothCases() {
        assertEquals("ሽ", Transliterator.transliterate("x"))
        assertEquals("ሸ", Transliterator.transliterate("xe"))
        assertEquals("ሿ", Transliterator.transliterate("xua"))
        // No distinct uppercase family -- shift falls through, like "Q"/"q".
        assertEquals("ሽ", Transliterator.transliterate("X"))
    }

    @Test
    fun digraphSpellingsStillWork() {
        assertEquals("ች", Transliterator.transliterate("ch"))
        assertEquals("ቸ", Transliterator.transliterate("che"))
        assertEquals("ሽ", Transliterator.transliterate("sh"))
        assertEquals("ሸ", Transliterator.transliterate("she"))
    }

    // ----- the three real case distinctions -----------------------------

    @Test
    fun shiftSelectsDistinctFamiliesForHTC() {
        assertEquals("ህ", Transliterator.transliterate("h"))
        assertEquals("ሕ", Transliterator.transliterate("H"))
        assertEquals("ት", Transliterator.transliterate("t"))
        assertEquals("ጥ", Transliterator.transliterate("T"))
    }

    @Test
    fun uppercaseSIsSaAtFamily() {
        // "S" -> ሠ series (distinct from "s" -> ሰ).
        assertEquals("ሥ", Transliterator.transliterate("S"))
        assertEquals("ሠ", Transliterator.transliterate("Se"))
        assertEquals("ሣ", Transliterator.transliterate("Sa"))
        // lowercase unchanged.
        assertEquals("ስ", Transliterator.transliterate("s"))
    }

    @Test
    fun tsDigraphIsTseFamilyByCase() {
        // "Ts" -> ፀ series (distinct from "ts" -> ጸ).
        assertEquals("ፅ", Transliterator.transliterate("Ts"))
        assertEquals("ፀ", Transliterator.transliterate("Tse"))
        assertEquals("ፃ", Transliterator.transliterate("Tsa"))
        // lowercase unchanged, and bare "T" still the ejective ጠ family.
        assertEquals("ጽ", Transliterator.transliterate("ts"))
        assertEquals("ጥ", Transliterator.transliterate("T"))
    }

    // ----- bare vowels: glottal (lowercase) vs pharyngeal (uppercase) ----

    @Test
    fun lowercaseBareVowelsAreGlottalFamily() {
        assertEquals("አ", Transliterator.transliterate("a"))
        assertEquals("ኡ", Transliterator.transliterate("u"))
        assertEquals("ኢ", Transliterator.transliterate("i"))
        assertEquals("ኦ", Transliterator.transliterate("o"))
        assertEquals("እ", Transliterator.transliterate("e"))
    }

    @Test
    fun uppercaseBareVowelsArePharyngealFamily() {
        assertEquals("ዐ", Transliterator.transliterate("A"))
        assertEquals("ዑ", Transliterator.transliterate("U"))
        assertEquals("ዒ", Transliterator.transliterate("I"))
        assertEquals("ዖ", Transliterator.transliterate("O"))
        assertEquals("ዕ", Transliterator.transliterate("E"))
        // Word-initial shifted vowel in context.
        assertEquals("ዐስተር", Transliterator.transliterate("Aster"))
    }

    // ----- separated reading (digraph disambiguation) --------------------

    @Test
    fun splitReadingSeparatesDigraphs() {
        // The whole point: "shn" greedily is ሽን, but separated is ስህን.
        assertEquals("ሽን", Transliterator.transliterate("shn"))
        assertEquals("ስህን", Transliterator.transliterateSplit("shn"))

        // A real word: ስህተት ("mistake"), which greedy mangles to ሽተት.
        assertEquals("ሽተት", Transliterator.transliterate("shtet"))
        assertEquals("ስህተት", Transliterator.transliterateSplit("shtet"))

        // ts / gn split too.
        assertEquals("ትስ", Transliterator.transliterateSplit("ts"))
        assertEquals("ግን", Transliterator.transliterateSplit("gn"))
    }

    @Test
    fun splitEqualsGreedyWhenNoDigraph() {
        // No digraph -> nothing to separate -> identical, which is how the
        // service detects "unambiguous, offer nothing extra".
        for (word in listOf("selam", "bet", "አማርኛ", "s", "hulet")) {
            assertEquals(
                Transliterator.transliterate(word),
                Transliterator.transliterateSplit(word)
            )
        }
    }

    // ----- readings(): the suggestion-strip candidates -------------------

    @Test
    fun unambiguousBufferHasOneReading() {
        // "r" -> just ር (no digraph, no case-alternate family).
        assertEquals(listOf("ር"), Transliterator.readings("r"))
        // The greedy reading is always first.
        assertEquals("ር", Transliterator.readings("r").first())
    }

    @Test
    fun digraphBufferOffersBothReadings() {
        // "sh" -> greedy ሽ then separated ስህ.
        assertEquals(listOf("ሽ", "ስህ"), Transliterator.readings("sh"))
        // "shtet" -> greedy ሽተት, the ጠ-family alternate ሽጠጥ (every "t" flips to
        // its ጥ reading), then the separated ስህተት.
        assertEquals(listOf("ሽተት", "ሽጠጥ", "ስህተት"), Transliterator.readings("shtet"))
    }

    @Test
    fun twoFormLettersOfferBothForms() {
        // Each of these families is reachable un-shifted via its alternate.
        // "h" has TWO alternates: the ሐ case form, then the velar ኀ series.
        assertEquals(listOf("ህ", "ሕ", "ኅ"), Transliterator.readings("h"))   // ሀ/ሐ/ኀ
        assertEquals(listOf("ስ", "ሥ"), Transliterator.readings("s"))   // ሰ/ሠ
        assertEquals(listOf("አ", "ዐ"), Transliterator.readings("a"))   // glottal/pharyngeal
        // "ts": both forms (ጽ/ፅ) plus the separated t+s reading.
        assertEquals(listOf("ጽ", "ፅ", "ትስ"), Transliterator.readings("ts"))
        // A whole word: the ሠ variant rides along behind the greedy one.
        assertEquals(listOf("ሰላም", "ሠላም"), Transliterator.readings("selam"))
    }

    @Test
    fun kOffersQFamilyAsSecondaryReading() {
        // "k" primarily writes the ከ series (bare ክ), but the ቀ series (bare
        // ቅ, = the "q" family) rides along as a secondary reading -- so the
        // two easily-confused families are both reachable without shift.
        assertEquals("ክ", Transliterator.transliterate("k"))
        assertEquals(listOf("ክ", "ቅ"), Transliterator.readings("k"))
        assertEquals(listOf("ካ", "ቃ"), Transliterator.readings("ka"))
    }

    @Test
    fun tOffersTFamilyAsSecondaryReading() {
        // "t" primarily writes the ተ series (bare ት), but the ጠ series (bare
        // ጥ) rides along as a secondary reading, reachable without shift.
        assertEquals("ት", Transliterator.transliterate("t"))
        assertEquals(listOf("ት", "ጥ"), Transliterator.readings("t"))
        assertEquals(listOf("ታ", "ጣ"), Transliterator.readings("ta"))
    }

    @Test
    fun emptyBufferHasNoReadings() {
        assertEquals(emptyList<String>(), Transliterator.readings(""))
    }

    // ----- punctuation ---------------------------------------------------

    @Test
    fun punctuationMapsToEthiopicForms() {
        assertEquals("፣", Transliterator.transliterate(","))
        assertEquals("።", Transliterator.transliterate("."))
        assertEquals("ሰላም።", Transliterator.transliterate("selam."))
    }

    /**
     * The property whose violation motivated all of the above: every
     * character key on the Amharic layout must transliterate to something
     * (fidel, never literal Latin) in BOTH shift states -- because the
     * key's corner preview is computed from this exact function, a
     * pass-through key would be a dead key with a blank preview.
     */
    @Test
    fun everyAmharicLayoutKeyProducesFidelInBothShiftStates() {
        val keys = AmharicLayout.rows.flatten().filterIsInstance<KeyData.Character>()
        for (key in keys) {
            for (resolved in listOf(key.latin.lowercase(), key.latin.uppercase())) {
                val output = Transliterator.transliterate(resolved)
                assertNotEquals("key \"$resolved\" passed through untransliterated", resolved, output)
            }
        }
    }
}
