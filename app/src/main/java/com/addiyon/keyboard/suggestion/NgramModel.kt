package com.addiyon.keyboard.suggestion

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Bigram/trigram next-word model, parsed from the binary asset produced by
 * `tools/build_ngrams.py` (see that script for the exact byte layout). Pure
 * Kotlin so the format and backoff logic are JVM-testable. Script-neutral: the
 * Amharic and English keyboards each load their own asset and inject the
 * matching [normalize] fold (see [NgramDictionary]).
 *
 * Everything lives in primitive arrays: a sorted vocabulary (word id =
 * index, resolved by binary search), sorted context arrays, and per-context
 * successor slices already ordered by corpus count. [predict] looks up the
 * trigram context (prev2, prev1) first and backs off to the bigram context
 * (prev1) for any remaining slots.
 *
 * The vocabulary stores each word's canonical DISPLAY form but is sorted by
 * its [normalize]-folded key, and [wordId] folds the lookup word before the
 * binary search -- so a context word committed in any variant spelling (ሃገር
 * for ሀገር in Amharic, any casing in English) still finds its predictions,
 * while predictions themselves always come out in the dictionary's canonical
 * form, matching its suggestion strings exactly. The build script sorts the
 * vocab by this SAME fold, so it must agree with [normalize] (Amharic:
 * homoglyph fold; English: per-char lowercase).
 *
 * Two on-disk versions are accepted: v2 (Amharic, caseless) and v3, which adds
 * a per-successor casing flag so English predictions can carry per-context
 * proper-noun casing -- "United" -> "States", "New" -> "York" -- on top of the
 * dictionary's default (lowercase) form. The flag is 0 (as-is), 1 (capitalize
 * first letter) or 2 (all caps); v2 assets behave as all-zero.
 */
class NgramModel private constructor(
    private val vocab: Array<String>,
    private val bigramContexts: IntArray,
    private val bigramOffsets: IntArray,
    private val bigramSuccessors: IntArray,
    private val bigramWeights: ByteArray,
    private val bigramCasing: ByteArray?,
    private val trigramContexts: LongArray,
    private val trigramOffsets: IntArray,
    private val trigramSuccessors: IntArray,
    private val trigramWeights: ByteArray,
    private val trigramCasing: ByteArray?,
    private val normalize: (String) -> String
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
                        trigramSuccessors, trigramWeights, trigramCasing
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
                    bigramSuccessors, bigramWeights, bigramCasing
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
        weights: ByteArray,
        casing: ByteArray?
    ) {
        for (i in from until until) {
            if (result.size >= limit) return
            val id = successors[i]
            if (seen.add(id)) {
                val word = applyCasing(vocab[id], casing?.get(i)?.toInt() ?: 0)
                result.add(Prediction(word, weights[i].toInt() and 0xFF))
            }
        }
    }

    /** Applies a stored casing flag to a dictionary display form: 1 capitalizes
     *  the first letter (states -> States), 2 upper-cases it all (usa -> USA),
     *  0 leaves it untouched. */
    private fun applyCasing(word: String, flag: Int): String = when (flag) {
        1 -> word.replaceFirstChar { it.uppercaseChar() }
        2 -> word.uppercase()
        else -> word
    }

    /** Binary search over the folded-key-ordered vocab; the probe words are
     *  folded on the fly (a handful of log2(vocab) comparisons per call). */
    private fun wordId(word: String): Int? {
        if (word.isEmpty()) return null
        val key = normalize(word)
        var lo = 0
        var hi = vocab.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = normalize(vocab[mid]).compareTo(key)
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
        // Vocab sorted by folded key, entries are dictionary display forms
        // (both since v2). v3 appends a per-successor casing-flag byte array to
        // each section. A v1 asset would binary-search wrongly -> reject loudly.
        private const val MIN_VERSION = 2
        private const val MAX_VERSION = 3

        /**
         * Parses the raw (already un-gzipped) model bytes. [normalize] is the
         * fold the asset's vocab was sorted by (Amharic homoglyph fold /
         * English per-char lowercase) and MUST match it, since [wordId]
         * binary-searches with it. Throws [IOException] on a bad magic/version,
         * so a corrupt asset fails loudly at load time instead of mispredicting
         * silently.
         */
        fun parse(input: InputStream, normalize: (String) -> String): NgramModel {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            if (!magic.contentEquals(byteArrayOf(0x41, 0x4E, 0x47, 0x4D))) {
                throw IOException("bad ngram magic")
            }
            val version = data.readUnsignedByte()
            if (version !in MIN_VERSION..MAX_VERSION) {
                throw IOException("unsupported ngram version $version")
            }
            val hasCasing = version >= 3

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
            val bigramCasing = if (hasCasing) {
                ByteArray(bigramTotal).also { data.readFully(it) }
            } else null

            val trigramCount = data.readInt()
            val trigramContexts = LongArray(trigramCount) { data.readLong() }
            val trigramOffsets = IntArray(trigramCount + 1) { data.readInt() }
            val trigramTotal = trigramOffsets.lastOrNull() ?: 0
            val trigramSuccessors = IntArray(trigramTotal) { data.readInt() }
            val trigramWeights = ByteArray(trigramTotal).also { data.readFully(it) }
            val trigramCasing = if (hasCasing) {
                ByteArray(trigramTotal).also { data.readFully(it) }
            } else null

            return NgramModel(
                vocab,
                bigramContexts, bigramOffsets, bigramSuccessors, bigramWeights, bigramCasing,
                trigramContexts, trigramOffsets, trigramSuccessors, trigramWeights, trigramCasing,
                normalize
            )
        }
    }
}
