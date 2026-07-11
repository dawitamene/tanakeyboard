package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
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
 */
class NgramModelTest {

    private val model: NgramModel by lazy {
        val stream = javaClass.getResourceAsStream("/ngram_fixture.dat")
            ?: error("ngram_fixture.dat missing from test resources")
        NgramModel.parse(GZIPInputStream(stream))
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
        val predictions = model.predict(null, "ሰላም", 5)
        assertEquals(listOf("ዓለም"), predictions.map { it.word })
        assertEquals(26, predictions.single().weight)
    }

    @Test
    fun limitAndEmptyInputsAreSafe() {
        assertTrue(model.predict(null, "ምን", 0).isEmpty())
        assertTrue(model.predict(null, "", 3).isEmpty())
        assertEquals(1, model.predict("እንደ", "ምን", 1).size)
    }

    /** Same guard as [BundledAssetTest]: the real shipped asset must parse. */
    @Test
    fun bundledNgramAssetParses() {
        val file = listOf(
            "src/main/assets/amharic_ngrams.dat",
            "app/src/main/assets/amharic_ngrams.dat"
        ).map { File(it) }.firstOrNull { it.exists() }
            ?: error("amharic_ngrams.dat not found from ${File(".").absolutePath}")
        val bundled = NgramModel.parse(GZIPInputStream(file.inputStream()))
        assertTrue(bundled.vocabSize > 50_000)
        // A ubiquitous function word must predict something.
        assertTrue(bundled.predict(null, "አዲስ", 5).isNotEmpty())
    }
}
