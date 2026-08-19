package com.addiyon.keyboard.suggestion

/** Versioned, language-tagged dictionary for words learned only on this device. */
class PersonalDictionary private constructor(
    private val buckets: LinkedHashMap<String, LinkedHashMap<String, Entry>>
) {
    data class Entry(
        val count: Int,
        val lemmaId: String? = null,
        val analysisId: String? = null,
    )

    fun learn(
        languageId: String,
        word: String,
        morphologyIdentity: MorphologyIdentity? = null,
    ) {
        val value = word.trim()
        val validIdentity = morphologyIdentity?.takeIf {
            validMetadataId(it.lemmaId) && validMetadataId(it.analysisId)
        }
        if (
            value.isEmpty() ||
            value.any { it == '\t' || it == '\n' || it == '\r' } ||
            validIdentity == null && value.any(Char::isWhitespace)
        ) return
        val counts = buckets.getOrPut(languageBucket(languageId)) { LinkedHashMap() }
        val current = counts.remove(value)
        counts[value] = Entry(
            count = (current?.count ?: 0) + 1,
            lemmaId = validIdentity?.lemmaId ?: current?.lemmaId,
            analysisId = validIdentity?.analysisId ?: current?.analysisId,
        )
        trimToLimit()
    }

    fun learnEmail(address: String) {
        val value = address.trim()
        if ('@' !in value || value.any { it.isWhitespace() }) return
        val counts = buckets.getOrPut(EMAIL_BUCKET) { LinkedHashMap() }
        counts[value] = Entry((counts.remove(value)?.count ?: 0) + 1)
        trimToLimit()
    }

    fun completions(languageId: String, prefix: String, limit: Int): List<String> =
        ranked(languageId, prefix, limit)

    fun completionEntries(
        languageId: String,
        prefix: String,
        limit: Int,
    ): List<PersonalCompletion> {
        val entries = buckets[languageBucket(languageId)].orEmpty().entries.toList()
        val recencyByWord = entries.mapIndexed { index, entry -> entry.key to index }.toMap()
        return entries.asSequence()
            .filter { it.key.startsWith(prefix, ignoreCase = true) }
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
            .take(limit)
            .map {
                PersonalCompletion(
                    word = it.key,
                    count = it.value.count,
                    recency = recencyByWord.getValue(it.key),
                    lemmaId = it.value.lemmaId,
                    analysisId = it.value.analysisId,
                )
            }
            .toList()
    }

    fun allWords(): List<String> = buckets.values.flatMap { it.keys }.distinct()

    fun words(languageId: String): List<String> =
        buckets[languageBucket(languageId)]?.keys?.toList().orEmpty()

    fun hasWords(languageId: String): Boolean =
        buckets[languageBucket(languageId)]?.isNotEmpty() == true

    fun remove(word: String): Boolean {
        var removed = false
        buckets.values.forEach { removed = it.remove(word) != null || removed }
        buckets.entries.removeAll { it.value.isEmpty() }
        return removed
    }

    fun remove(languageId: String, word: String): Boolean {
        val bucket = buckets[languageBucket(languageId)] ?: return false
        val removed = bucket.remove(word) != null
        if (bucket.isEmpty()) buckets.remove(languageBucket(languageId))
        return removed
    }

    fun clear() {
        buckets.clear()
    }

    fun clear(languageId: String) {
        buckets.remove(languageBucket(languageId))
    }

    fun emailAddresses(): List<String> = buckets[EMAIL_BUCKET]
        ?.entries
        ?.sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
        ?.map { it.key }
        .orEmpty()

    fun ranked(languageId: String, prefix: String = "", limit: Int): List<String> =
        buckets[languageBucket(languageId)].orEmpty().entries
        .asSequence()
        .filter { it.key.startsWith(prefix, ignoreCase = true) }
        .sortedWith(compareByDescending<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.key })
        .take(limit)
        .map { it.key }
        .toList()

    fun encode(): String = buildString {
        append(VERSION_HEADER)
        for ((bucket, counts) in buckets) {
            for ((word, entry) in counts) {
                append('\n')
                append(bucket)
                append('\t')
                append(entry.count)
                append('\t')
                append(word)
                append('\t')
                append(entry.lemmaId.orEmpty())
                append('\t')
                append(entry.analysisId.orEmpty())
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
        private const val VERSION_HEADER = "addiyon-personal-dictionary-v3"
        private const val VERSION_TWO_HEADER = "addiyon-personal-dictionary-v2"
        private const val EMAIL_BUCKET = "email"
        private const val LEGACY_BUCKET = "legacy"

        private fun languageBucket(languageId: String): String = "language:${languageId.trim()}"

        fun decode(encoded: String?): PersonalDictionary {
            val lines = encoded.orEmpty().lineSequence().toList()
            val result = LinkedHashMap<String, LinkedHashMap<String, Entry>>()
            if (lines.firstOrNull() == VERSION_HEADER) {
                lines.drop(1).forEach { line ->
                    val parts = line.split('\t', limit = 5)
                    if (parts.size != 5) return@forEach
                    val bucket = parts[0].takeIf {
                        it == EMAIL_BUCKET || it == LEGACY_BUCKET || it.startsWith("language:")
                    } ?: return@forEach
                    val count = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return@forEach
                    val lemmaId = parts[3].ifBlank { null }?.takeIf(::validMetadataId)
                    val analysisId = parts[4].ifBlank { null }?.takeIf(::validMetadataId)
                    val hasIdentity = lemmaId != null && analysisId != null
                    val word = parts[2].trim().takeIf {
                        it.isNotEmpty() &&
                            it.none { character -> character == '\t' || character == '\n' || character == '\r' } &&
                            (hasIdentity || it.none(Char::isWhitespace))
                    } ?: return@forEach
                    result.getOrPut(bucket) { LinkedHashMap() }[word] =
                        Entry(
                            count,
                            lemmaId.takeIf { hasIdentity },
                            analysisId.takeIf { hasIdentity },
                        )
                }
            } else if (lines.firstOrNull() == VERSION_TWO_HEADER) {
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
                    result.getOrPut(bucket) { LinkedHashMap() }[word] = Entry(count)
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
                    result.getOrPut(bucket) { LinkedHashMap() }[word] = Entry(count)
                }
            }
            return PersonalDictionary(result)
        }

        private fun validMetadataId(value: String): Boolean =
            value.length <= 128 && value.all { it.isLetterOrDigit() || it in ":_-" }
    }
}
