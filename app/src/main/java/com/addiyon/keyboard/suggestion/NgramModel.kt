package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Amharic bigram/trigram next-word model, parsed from the binary asset
 * produced by `tools/build_ngrams.py` (see that script for the exact byte
 * layout). Pure Kotlin so the format and backoff logic are JVM-testable.
 *
 * Everything lives in primitive arrays: a sorted vocabulary (word id =
 * index, resolved by binary search), sorted context arrays, and per-context
 * successor slices already ordered by corpus count. [predict] looks up the
 * trigram context (prev2, prev1) first and backs off to the bigram context
 * (prev1) for any remaining slots.
 *
 * The vocabulary stores each word's canonical DISPLAY form but is sorted by
 * its [EthiopicNormalizer]-folded key (format v2), and [wordId] folds the
 * lookup word before the binary search -- so a context word committed in any
 * variant spelling (ሃገር for ሀገር) still finds its predictions, while
 * predictions themselves always come out canonically spelled, matching the
 * dictionary's suggestion strings exactly.
 */
class NgramModel private constructor(
    private val vocab: Array<String>,
    private val bigramContexts: IntArray,
    private val bigramOffsets: IntArray,
    private val bigramSuccessors: IntArray,
    private val bigramWeights: ByteArray,
    private val trigramContexts: LongArray,
    private val trigramOffsets: IntArray,
    private val trigramSuccessors: IntArray,
    private val trigramWeights: ByteArray
) {
    data class Prediction(val word: String, val weight: Int)

    val vocabSize: Int get() = vocab.size

    /**
     * Next-word predictions for the words preceding the cursor. Trigram
     * matches on (prev2, prev1) come first, then bigram matches on prev1
     * fill the remaining slots (deduplicated). Unknown context words simply
     * skip their order; both unknown -> empty.
     */
    fun predict(prev2: String?, prev1: String, limit: Int): List<Prediction> {
        if (limit <= 0) return emptyList()
        val id1 = wordId(prev1) ?: return emptyList()
        val result = ArrayList<Prediction>(limit)
        val seen = HashSet<Int>()

        if (prev2 != null) {
            val id2 = wordId(prev2)
            if (id2 != null) {
                val index = trigramContexts.binarySearch(
                    (id2.toLong() shl 32) or id1.toLong()
                )
                if (index >= 0) {
                    appendSuccessors(
                        result, seen, limit,
                        trigramOffsets[index], trigramOffsets[index + 1],
                        trigramSuccessors, trigramWeights
                    )
                }
            }
        }

        if (result.size < limit) {
            val index = bigramContexts.binarySearch(id1)
            if (index >= 0) {
                appendSuccessors(
                    result, seen, limit,
                    bigramOffsets[index], bigramOffsets[index + 1],
                    bigramSuccessors, bigramWeights
                )
            }
        }
        return result
    }

    private fun appendSuccessors(
        result: ArrayList<Prediction>,
        seen: HashSet<Int>,
        limit: Int,
        from: Int,
        until: Int,
        successors: IntArray,
        weights: ByteArray
    ) {
        for (i in from until until) {
            if (result.size >= limit) return
            val id = successors[i]
            if (seen.add(id)) {
                result.add(Prediction(vocab[id], weights[i].toInt() and 0xFF))
            }
        }
    }

    /** Binary search over the folded-key-ordered vocab; the probe words are
     *  folded on the fly (a handful of log2(vocab) comparisons per call). */
    private fun wordId(word: String): Int? {
        if (word.isEmpty()) return null
        val key = EthiopicNormalizer.normalize(word)
        var lo = 0
        var hi = vocab.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = EthiopicNormalizer.normalize(vocab[mid]).compareTo(key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return null
    }

    private fun IntArray.binarySearch(key: Int): Int =
        java.util.Arrays.binarySearch(this, key)

    private fun LongArray.binarySearch(key: Long): Int =
        java.util.Arrays.binarySearch(this, key)

    companion object {
        // v2: vocab sorted by folded key, entries are dictionary display
        // forms. A v1 asset would binary-search wrongly -> reject loudly.
        private const val VERSION = 2

        /**
         * Parses the raw (already un-gzipped) model bytes. Throws
         * [IOException] on a bad magic/version, so a corrupt asset fails
         * loudly at load time instead of mispredicting silently.
         */
        fun parse(input: InputStream): NgramModel {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            if (!magic.contentEquals(byteArrayOf(0x41, 0x4E, 0x47, 0x4D))) {
                throw IOException("bad ngram magic")
            }
            val version = data.readUnsignedByte()
            if (version != VERSION) throw IOException("unsupported ngram version $version")

            val vocab = Array(data.readInt()) {
                val bytes = ByteArray(data.readUnsignedShort())
                data.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }

            val bigramCount = data.readInt()
            val bigramContexts = IntArray(bigramCount) { data.readInt() }
            val bigramOffsets = IntArray(bigramCount + 1) { data.readInt() }
            val bigramTotal = bigramOffsets.lastOrNull() ?: 0
            val bigramSuccessors = IntArray(bigramTotal) { data.readInt() }
            val bigramWeights = ByteArray(bigramTotal).also { data.readFully(it) }

            val trigramCount = data.readInt()
            val trigramContexts = LongArray(trigramCount) { data.readLong() }
            val trigramOffsets = IntArray(trigramCount + 1) { data.readInt() }
            val trigramTotal = trigramOffsets.lastOrNull() ?: 0
            val trigramSuccessors = IntArray(trigramTotal) { data.readInt() }
            val trigramWeights = ByteArray(trigramTotal).also { data.readFully(it) }

            return NgramModel(
                vocab,
                bigramContexts, bigramOffsets, bigramSuccessors, bigramWeights,
                trigramContexts, trigramOffsets, trigramSuccessors, trigramWeights
            )
        }
    }
}
