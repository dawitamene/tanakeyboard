package com.addiyon.keyboard.suggestion

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
 * streaming construction requires the lines sorted by the lowercased word in
 * UTF-16 code-unit order and throws otherwise, so merely building here proves
 * the `tools/` scripts and the Kotlin side agree on that order (Python's
 * code-point sort vs Kotlin's `lowercaseChar` keying is exactly the kind of
 * drift this would catch). The content assertions pin down that regeneration
 * didn't silently break cleaning (junk tokens) or casing (proper nouns).
 */
class BundledAssetTest {

    /** Same line format as [WordDictionary.load]: `word<TAB>frequency`. */
    private fun loadAsset(name: String): WordTrie {
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
                }
            )
        }
    }

    @Test
    fun amharicAssetBuildsAndContainsRealWordsButNoJunk() {
        val t = loadAsset("amharic_words.dat")

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
    }
}
