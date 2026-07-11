package com.addiyon.keyboard.transliteration

import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.model.KeyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ----- candidates(): the suggestion-strip / commit-ranking readings ---

    @Test
    fun emptyBufferHasNoCandidates() {
        assertEquals(emptyList<String>(), Transliterator.candidates(""))
    }

    @Test
    fun candidatesZeroIsAlwaysTheGreedyReading() {
        for (word in listOf("r", "sh", "shtet", "h", "s", "a", "ts", "selam", "k", "ka", "t", "ta", "fkr")) {
            assertEquals(
                "candidates(\"$word\")[0] must equal the greedy reading",
                Transliterator.transliterate(word),
                Transliterator.candidates(word).first()
            )
        }
    }

    @Test
    fun unambiguousBufferHasOneCandidate() {
        // "r" -> just ር (no digraph, no case-alternate family).
        assertEquals(listOf("ር"), Transliterator.candidates("r"))
    }

    @Test
    fun digraphPositionOffersTheSplitReading() {
        // "sh": greedy ሽ, then the separated ስህ (+ the s/S family alternate
        // riding along on the split's "s" unit).
        val readings = Transliterator.candidates("sh")
        assertEquals("ሽ", readings.first())
        assertTrue("ስህ" in readings)
    }

    @Test
    fun twoFormLettersOfferBothFormsAsCandidates() {
        // Each of these families is reachable un-shifted via its alternate.
        // "h" has TWO alternates: the velar ኀ series, then the ሐ case form.
        assertEquals(listOf("ህ", "ኅ", "ሕ"), Transliterator.candidates("h"))
        assertEquals(setOf("ስ", "ሥ"), Transliterator.candidates("s").toSet())
        assertEquals(listOf("አ", "ዓ", "ዐ", "ኣ"), Transliterator.candidates("a"))
        assertEquals(listOf("ች", "ጭ"), Transliterator.candidates("c"))
        assertEquals(listOf("ፕ", "ጵ"), Transliterator.candidates("p"))
        // "ts": both forms (ጽ/ፅ) plus the separated t+s reading.
        assertTrue(Transliterator.candidates("ts").containsAll(listOf("ጽ", "ፅ", "ትስ")))
        // A whole word: the ሠ variant rides along behind the greedy one.
        val selam = Transliterator.candidates("selam")
        assertEquals("ሰላም", selam.first())
        assertTrue("ሠላም" in selam)
    }

    @Test
    fun kOffersQFamilyAsSecondaryReading() {
        // "k" primarily writes the ከ series (bare ክ), but the ቀ series (bare
        // ቅ, = the "q" family) rides along as a secondary reading -- so the
        // two easily-confused families are both reachable without shift.
        assertEquals("ክ", Transliterator.transliterate("k"))
        assertEquals(listOf("ክ", "ቅ"), Transliterator.candidates("k"))
        val ka = Transliterator.candidates("ka")
        assertEquals("ካ", ka.first())
        assertTrue("ቃ" in ka)
    }

    @Test
    fun tOffersTFamilyAsSecondaryReading() {
        // "t" primarily writes the ተ series (bare ት), but the ጠ series (bare
        // ጥ) rides along as a secondary reading, reachable without shift.
        assertEquals("ት", Transliterator.transliterate("t"))
        assertEquals(listOf("ት", "ጥ"), Transliterator.candidates("t"))
        val ta = Transliterator.candidates("ta")
        assertEquals("ታ", ta.first())
        assertTrue("ጣ" in ta)
    }

    @Test
    fun eOffersTheIeOrderAsSecondaryReading() {
        // "e" after a consonant primarily writes order 1 (ለ/በ/መ), but the
        // order-5 "ie" form (ሌ/ቤ/ሜ) rides along as a secondary reading, so the
        // user doesn't have to type "ie".
        assertEquals("ለ", Transliterator.transliterate("le"))
        val le = Transliterator.candidates("le")
        assertEquals("ለ", le.first())
        // The order-5 alternate ranks immediately after the primary reading.
        assertEquals("ሌ", le[1])
        val be = Transliterator.candidates("be")
        assertEquals("በ", be.first())
        assertEquals("ቤ", be[1])
        // Reaches the same glyph as spelling the vowel "ie" outright.
        assertTrue("ቤት" in Transliterator.candidates("bet"))
        // Greedy transliteration is unchanged (primary order only).
        assertEquals("በት", Transliterator.transliterate("bet"))
    }

    @Test
    fun vowelAlternateReadingFlipsEveryESyllableToItsIeOrder() {
        // The clean "write it with the ie vowel" secondary reading: same
        // segmentation as the greedy reading, only the e-order flips to order 5.
        assertEquals("ሜ", Transliterator.vowelAlternateReading("me"))
        assertEquals("ሜላት", Transliterator.vowelAlternateReading("melat"))
        assertEquals("ቤት", Transliterator.vowelAlternateReading("bet"))
        // Reaches the same glyphs as spelling the vowel "ie" outright.
        assertEquals(Transliterator.transliterate("mielat"), Transliterator.vowelAlternateReading("melat"))
        // Every e in the word flips, not just the first.
        assertEquals("ቤቤ", Transliterator.vowelAlternateReading("bebe"))
        assertEquals("ሴላም", Transliterator.vowelAlternateReading("selam"))
        // Null when there's nothing to flip (no e syllable).
        assertNull(Transliterator.vowelAlternateReading("bota"))
        assertNull(Transliterator.vowelAlternateReading("selam".replace("e", "a")))
        assertNull(Transliterator.vowelAlternateReading(""))
    }

    @Test
    fun fkrCandidatesIncludeTheQFamilyReading() {
        // The motivating example: "fkr" greedily reads ፍክር (not a word), but
        // ፍቅር (a real word) must be reachable so CandidateRanker can promote
        // it via the dictionary.
        assertEquals("ፍክር", Transliterator.transliterate("fkr"))
        assertTrue("ፍቅር" in Transliterator.candidates("fkr"))
    }

    @Test
    fun mixedPerUnitAlternatesAreReachable() {
        // The old uniform-alternate-level scheme could only flip EVERY
        // consonant to its Nth alternate at once, so "t primary + k
        // alternate" was never offered even though both are plausible. The
        // lattice explores per-unit combinations, so it must be present.
        val readings = Transliterator.candidates("tak")
        // Primary+primary is the greedy reading.
        assertEquals(Transliterator.transliterate("tak"), readings.first())
        // "t" alternate (ጠ family, ጣ) + "k" primary (ክ) -- a MIXED choice.
        assertTrue("ጣክ" in readings)
        // "t" primary (ታ) + "k" alternate (ቀ family, ቅ) -- the other mix.
        assertTrue("ታቅ" in readings)
    }

    @Test
    fun dedupesIdenticalRenderings() {
        val readings = Transliterator.candidates("mala")
        assertEquals(readings.toSet().size, readings.size)
    }

    @Test
    fun capsAtTheRequestedLimit() {
        val readings = Transliterator.candidates("shtet", limit = 2)
        assertEquals(2, readings.size)
        assertEquals(Transliterator.transliterate("shtet"), readings[0])
    }

    @Test
    fun caseSensitivityIsPreservedInCandidates() {
        // H/T/C keep their distinct families; case-insensitive letters don't
        // spuriously gain alternates from the other case.
        assertEquals(setOf("ሕ", "ህ", "ኅ"), Transliterator.candidates("H").toSet())
        // "T" has no entry of its own in consonantAlternates (only "t" ->
        // T is defined, not the reverse), so it has exactly one candidate.
        assertEquals(listOf("ጥ"), Transliterator.candidates("T"))
        assertEquals(setOf("ጵ", "ፕ"), Transliterator.candidates("P").toSet())
        assertEquals(setOf("ዐ", "ዓ", "አ", "ኣ"), Transliterator.candidates("A").toSet())
    }

    @Test
    fun vowelAfterConsonantCanSplitIntoBareConsonantAndStandaloneVowel() {
        assertEquals("ባ", Transliterator.transliterate("ba"))
        assertTrue("ብአ" in Transliterator.candidates("ba"))
        assertTrue("ብአድ" in Transliterator.candidates("bad"))
        assertTrue(
            Transliterator.candidateReadings("bad")
                .first { it.text == "ብአድ" }
                .isQuirk
        )
    }

    @Test
    fun firstLetterAlternatesSurviveSplitVowelCandidateExpansion() {
        assertTrue("ጭላዳ" in Transliterator.candidates("clada"))
    }

    @Test
    fun mixedPAndTAlternatesSurviveSplitVowelCandidateExpansion() {
        assertTrue("ጴንጤ" in Transliterator.candidates("pientie"))
    }

    // ----- punctuation ---------------------------------------------------

    @Test
    fun punctuationMapsToEthiopicForms() {
        assertEquals("፣", Transliterator.transliterate(","))
        assertEquals("።", Transliterator.transliterate("."))
        assertEquals("ሰላም።", Transliterator.transliterate("selam."))
    }

    @Test
    fun abbreviationCandidatesIncludeLiteralPunctuation() {
        val aDot = Transliterator.candidates("a.")
        assertTrue("ዓ." in aDot)
        assertTrue("ዓ።" in aDot)
        val doSlashR = Transliterator.candidates("do/r")
        assertTrue("ዶ/ር" in doSlashR)
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
