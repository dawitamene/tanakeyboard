package com.addiyon.keyboard.suggestion

/** Versioned, language-tagged dictionary for words learned only on this device. */
class PersonalDictionary private constructor(
    private val buckets: LinkedHashMap<String, LinkedHashMap<String, Int>>
) {
    fun learn(languageId: String, word: String) {
        val value = word.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) return
        val counts = buckets.getOrPut(languageBucket(languageId)) { LinkedHashMap() }
        counts[value] = (counts[value] ?: 0) + 1
        trimToLimit()
    }

    fun learnEmail(address: String) {
        val value = address.trim()
        if ('@' !in value || value.any { it.isWhitespace() }) return
        val counts = buckets.getOrPut(EMAIL_BUCKET) { LinkedHashMap() }
        counts[value] = (counts[value] ?: 0) + 1
        trimToLimit()
    }

    fun completions(languageId: String, prefix: String, limit: Int): List<String> =
        ranked(languageId, prefix, limit)

    fun allWords(): List<String> = buckets.values.flatMap { it.keys }.distinct()

    fun remove(word: String): Boolean {
        var removed = false
        buckets.values.forEach { removed = it.remove(word) != null || removed }
        buckets.entries.removeAll { it.value.isEmpty() }
        return removed
    }

    fun clear() {
        buckets.clear()
    }

    fun emailAddresses(): List<String> = buckets[EMAIL_BUCKET]
        ?.entries
        ?.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        ?.map { it.key }
        .orEmpty()

    fun ranked(languageId: String, prefix: String = "", limit: Int): List<String> =
        buckets[languageBucket(languageId)].orEmpty().entries
        .asSequence()
        .filter { it.key.startsWith(prefix, ignoreCase = true) }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }
        .toList()

    fun encode(): String = buildString {
        append(VERSION_HEADER)
        for ((bucket, counts) in buckets) {
            for ((word, count) in counts) {
                append('\n')
                append(bucket)
                append('\t')
                append(count)
                append('\t')
                append(word)
            }
        }
    }

    private fun trimToLimit() {
        while (buckets.values.sumOf { it.size } > MAX_WORDS) {
            val firstBucket = buckets.entries.firstOrNull { it.value.isNotEmpty() } ?: return
            firstBucket.value.remove(firstBucket.value.keys.first())
            if (firstBucket.value.isEmpty()) buckets.remove(firstBucket.key)
        }
    }

    companion object {
        private const val MAX_WORDS = 512
        private const val VERSION_HEADER = "addiyon-personal-dictionary-v2"
        private const val EMAIL_BUCKET = "email"
        private const val LEGACY_BUCKET = "legacy"

        private fun languageBucket(languageId: String): String = "language:${languageId.trim()}"

        fun decode(encoded: String?): PersonalDictionary {
            val lines = encoded.orEmpty().lineSequence().toList()
            val result = LinkedHashMap<String, LinkedHashMap<String, Int>>()
            if (lines.firstOrNull() == VERSION_HEADER) {
                lines.drop(1).forEach { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size != 3) return@forEach
                    val bucket = parts[0].takeIf {
                        it == EMAIL_BUCKET || it == LEGACY_BUCKET || it.startsWith("language:")
                    } ?: return@forEach
                    val count = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return@forEach
                    val word = parts[2].trim().takeIf {
                        it.isNotEmpty() && it.none(Char::isWhitespace)
                    } ?: return@forEach
                    result.getOrPut(bucket) { LinkedHashMap() }[word] = count
                }
            } else {
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val count = line.substring(0, tab).toIntOrNull()?.coerceAtLeast(1)
                        ?: return@forEach
                    val word = line.substring(tab + 1).trim()
                    if (word.isEmpty() || word.any(Char::isWhitespace)) return@forEach
                    val bucket = when {
                        '@' in word -> EMAIL_BUCKET
                        word.any { it in 'ሀ'..'፿' } -> languageBucket("am-ET")
                        word.any(Char::isLetter) -> languageBucket("en-US")
                        else -> LEGACY_BUCKET
                    }
                    result.getOrPut(bucket) { LinkedHashMap() }[word] = count
                }
            }
            return PersonalDictionary(result)
        }
    }
}
