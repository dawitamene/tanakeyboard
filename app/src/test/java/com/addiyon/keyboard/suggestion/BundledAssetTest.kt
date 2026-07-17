package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Loads the REAL bundled `.dat` assets straight off the repo filesystem (JVM
 * unit tests need no emulator for that) through the exact same parse +
 * [WordTrie.build] path as [WordDictionary.load].
 *
 * This is the guard for the asset<->trie contract: [WordTrie.build]'s
 * streaming construction requires the lines sorted by the normalized key in
 * UTF-16 code-unit order and throws otherwise, so merely building here proves
 * the `tools/` scripts and the Kotlin side agree on that order -- Python's
 * code-point sort vs Kotlin's `lowercaseChar` keying, or the two hand-mirrored
 * copies of the homoglyph fold table (build_amharic_dict.py vs
 * [EthiopicNormalizer]) drifting apart, are exactly what this would catch.
 * The content assertions pin down that regeneration didn't silently break
 * cleaning (junk tokens), casing (proper nouns), or homoglyph merging.
 */
class BundledAssetTest {

    /** Same line format as [WordDictionary.load]: `word<TAB>frequency`. */
    private fun loadAsset(name: String, keyChar: (Char) -> Char = Char::lowercaseChar): WordTrie {
        // Working directory is the app module for Gradle test JVMs, the repo
        // root for some IDE runners -- accept either.
        val file = listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: error("asset $name not found from ${File(".").absolutePath}")
        GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            return WordTrie.build(
                lines.mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    val word = line.substring(0, tab)
                    val frequency = line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null
                    word to frequency
                },
                keyChar,
            )
        }
    }

    @Test
    fun amharicAssetBuildsAndContainsRealWordsButNoJunk() {
        val t = loadAsset("amharic_words.dat", EthiopicNormalizer::normalize)

        // Top corpus words present, with real (large) frequencies.
        val naw = t.frequencyOf("ነው")
        assertNotNull(naw)
        assertTrue("expected a real corpus frequency for ነው, got $naw", naw!! > 10_000)
        assertNotNull(t.frequencyOf("ላይ"))
        assertNotNull(t.frequencyOf("ውስጥ"))

        // A high-fan-out prefix completes, ranked by frequency.
        val ye = t.suggestionEntries("የ", limit = 3)
        assertEquals(3, ye.size)
        assertTrue(ye[0].frequency >= ye[1].frequency && ye[1].frequency >= ye[2].frequency)

        // Kept abbreviations are suggestable from their fidel prefix.
        assertNotNull(t.frequencyOf("ዓ.ም"))
        assertTrue(t.suggestions("ዓ.", limit = 5).isNotEmpty())

        // Homoglyph variants merged at asset-build time: every spelling of
        // "country" resolves to the same (summed) entry...
        val hager = t.frequencyOf("ሀገር")
        assertNotNull(hager)
        assertEquals(hager, t.frequencyOf("ሃገር"))
        assertEquals(hager, t.frequencyOf("ሐገር"))
        assertEquals(hager, t.frequencyOf("ኀገር"))
        // ...and the corpus-canonical spelling survives as the display form
        // even when it uses a variant character (ኃይል's key path is ሀይል).
        assertTrue(t.suggestions("ሀይ", limit = 5).contains("ኃይል"))
        assertTrue(t.suggestions("ኃይ", limit = 5).contains("ኃይል"))

        // Prefix-stripping fallback over the real asset: ስለ + a stem the
        // corpus knows (ቴክኖሎጂ) must surface for the typed prefix ስለቴክኖ,
        // whether the prefixed form happens to be stored directly or is
        // synthesized by [AmharicPrefixCompletion] from the stem.
        val lookup = { prefix: String, limit: Int ->
            t.suggestionEntries(prefix, limit).map {
                CandidateRanker.DictionaryWord(it.word, it.frequency)
            }
        }
        val direct = lookup("ስለቴክኖ", 3)
        val combined = direct +
            AmharicPrefixCompletion.complete("ስለቴክኖ", 3 - direct.size, direct, lookup)
        assertTrue(
            "expected ስለቴክኖሎጂ among $combined",
            combined.any { it.word == "ስለቴክኖሎጂ" }
        )

        // Tokenizer junk from the source dump must have been filtered out.
        assertNull(t.frequencyOf("።"))
        assertNull(t.frequencyOf("፣"))
        assertNull(t.frequencyOf("0.002"))
        assertNull(t.frequencyOf("በ2007"))
        assertNull(t.frequencyOf("\""))
    }

    @Test
    fun englishAssetBuildsAndKeepsCanonicalCasing() {
        val t = loadAsset("english_words.dat")

        assertNotNull(t.frequencyOf("the"))
        assertNotNull(t.frequencyOf("don't"))

        // Proper-noun casing survives the flat-array rebuild: the stored
        // display form differs from the lowercased trie path.
        assertTrue(t.suggestions("engl", limit = 3).contains("England"))

        // Inflected forms of curated proper nouns carry the derived casing
        // ("Norwegian" is curated; the corpus token "norwegians" is not) ...
        assertTrue(t.suggestions("norwegia", limit = 5).contains("Norwegians"))
        assertTrue(t.suggestions("canadian", limit = 5).contains("Canadians"))
        assertTrue(t.suggestions("american", limit = 5).contains("Americans"))
        // ... while the short-stem guard keeps "I"+s and "God"+s from
        // capitalizing the ordinary words "is" and "gods".
        assertTrue(t.suggestions("is", limit = 5).contains("is"))
        assertTrue(t.suggestions("gods", limit = 5).contains("gods"))

        // The "I" contractions carry their capital I (the pronoun is always
        // capitalized), while other contractions stay lowercase.
        val iContractions = t.suggestions("i'", limit = 10)
        assertTrue(iContractions.contains("I'm"))
        assertTrue(iContractions.contains("I've"))
        assertTrue(t.suggestions("it'", limit = 10).contains("it's"))
    }
}
