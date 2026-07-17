package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Parses the checked-in fixture built by
 * `tools/build_ngrams.py --test-fixture` from `ngram_mini_corpus.txt`, so
 * these assertions pin the Python writer <-> Kotlin parser byte contract as
 * well as the backoff logic. The mini corpus (counts):
 *
 *   እንደ ምን አደርክ x3, እንደ ምን ዋልክ x1, ዛሬ ምን ትፈልጋለህ x5,
 *   "ቡና ። ሻይ", "ውሃ 5 ወተት", ሰላም ዓለም x3 (one ሰላም geminated)
 *
 * Expected model: bigram ምን -> [ትፈልጋለህ, አደርክ, ዋልክ]; trigram
 * (እንደ, ምን) -> [አደርክ, ዋልክ] kept, while (ዛሬ, ምን) is dropped as identical
 * to its bigram backoff. ቡና/ሻይ and ውሃ/ወተት never pair (punctuation and
 * number boundaries), so none of them enter the vocabulary.
 *
 * Fixture mode carries no dictionary, so words are stored as their FOLDED
 * forms (ዓለም -> አለም); the real asset stores dictionary display forms over
 * the same folded ordering. Context lookups fold either way.
 */
class NgramModelTest {

    private val model: NgramModel by lazy {
        val stream = javaClass.getResourceAsStream("/ngram_fixture.dat")
            ?: error("ngram_fixture.dat missing from test resources")
        NgramModel.parse(GZIPInputStream(stream), EthiopicNormalizer::normalize)
    }

    @Test
    fun trigramContextWinsOverBigramBackoff() {
        val words = model.predict("እንደ", "ምን", 3).map { it.word }
        assertEquals(listOf("አደርክ", "ዋልክ", "ትፈልጋለህ"), words)
    }

    @Test
    fun bigramOnlyContextOrdersByFrequency() {
        val words = model.predict(null, "ምን", 3).map { it.word }
        assertEquals(listOf("ትፈልጋለህ", "አደርክ", "ዋልክ"), words)
    }

    @Test
    fun redundantTrigramFallsBackToBigram() {
        // (ዛሬ, ምን)'s predictions match the bigram's, so the build drops the
        // trigram context and the lookup must transparently back off.
        val words = model.predict("ዛሬ", "ምን", 1).map { it.word }
        assertEquals(listOf("ትፈልጋለህ"), words)
    }

    @Test
    fun unknownPrev2StillYieldsBigramPredictions() {
        val words = model.predict("ቡና", "ምን", 1).map { it.word }
        assertEquals(listOf("ትፈልጋለህ"), words)
    }

    @Test
    fun punctuationAndNumberBoundariesProduceNoNgrams() {
        // ቡና precedes ሻይ and ውሃ precedes ወተት in the corpus, but only across
        // a ። / digit token -- neither pair may exist, and since the words
        // appear in no kept n-gram they aren't even in the vocabulary.
        assertTrue(model.predict(null, "ቡና", 5).isEmpty())
        assertTrue(model.predict(null, "ውሃ", 5).isEmpty())
    }

    @Test
    fun geminatedSpellingsMergeIntoOneCount() {
        // ሰላ፝ም + ሰላም x2 merge to count 3 -> weight round(ln(3) * 24) = 26.
        // The corpus spells the successor ዓለም; the fixture stores its folded
        // form አለም (no dictionary to supply a display form in fixture mode).
        val predictions = model.predict(null, "ሰላም", 5)
        assertEquals(listOf("አለም"), predictions.map { it.word })
        assertEquals(26, predictions.single().weight)
    }

    @Test
    fun contextWordsMatchAcrossHomoglyphSpellings() {
        // The user may have committed a variant spelling; the folded binary
        // search must still find the context (ሠላም folds to ሰላም).
        assertEquals(listOf("አለም"), model.predict(null, "ሠላም", 5).map { it.word })
        // And a folded word queried in its corpus spelling (ዓለም) resolves
        // too -- it just has no successors in the mini corpus.
        assertTrue(model.predict("ሠላም", "ዓለም", 5).isEmpty())
    }

    @Test
    fun limitAndEmptyInputsAreSafe() {
        assertTrue(model.predict(null, "ምን", 0).isEmpty())
        assertTrue(model.predict(null, "", 3).isEmpty())
        assertEquals(1, model.predict("እንደ", "ምን", 1).size)
    }

    /** Same guard as [BundledAssetTest]: the real shipped asset must parse. */
    @Test
    fun bundledAmharicNgramAssetParses() {
        val bundled = loadBundled("amharic_ngrams.dat", EthiopicNormalizer::normalize)
        assertTrue(bundled.vocabSize > 50_000)
        // A ubiquitous function word must predict something.
        assertTrue(bundled.predict(null, "አዲስ", 5).isNotEmpty())
    }

    @Test
    fun bundledEnglishNgramAssetParses() {
        val bundled = loadBundled("english_ngrams.dat") { it.lowercase() }
        assertTrue(bundled.vocabSize > 5_000)
        // A ubiquitous context must predict, using the dictionary's canonical
        // spelling ("the" is the top continuation of "of").
        assertTrue(bundled.predict(null, "of", 5).map { it.word }.contains("the"))
        assertTrue(bundled.predict(null, "I", 5).isNotEmpty())
        // Case-folded context lookup: "I" and "i" resolve to the same context.
        assertEquals(
            bundled.predict(null, "I", 5).map { it.word },
            bundled.predict(null, "i", 5).map { it.word }
        )
    }

    @Test
    fun bundledEnglishAssetAppliesPerContextProperNounCasing() {
        val bundled = loadBundled("english_ngrams.dat") { it.lowercase() }
        // Proper nouns come out capitalized in the contexts that attest them...
        assertTrue(bundled.predict(null, "United", 6).map { it.word }.contains("States"))
        assertTrue(bundled.predict("the", "united", 6).map { it.word }.contains("States"))
        assertTrue(bundled.predict("the", "new", 6).map { it.word }.contains("York"))
        // ...but common continuations stay lowercase (per-context, not per-word,
        // so "of the" never becomes "of The").
        val ofNext = bundled.predict(null, "of", 6).map { it.word }
        assertTrue(ofNext.contains("the"))
        assertFalse(ofNext.contains("The"))
    }

    private fun loadBundled(name: String, normalize: (String) -> String): NgramModel {
        val file = listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map { File(it) }.firstOrNull { it.exists() }
            ?: error("$name not found from ${File(".").absolutePath}")
        return NgramModel.parse(GZIPInputStream(file.inputStream()), normalize)
    }
}
