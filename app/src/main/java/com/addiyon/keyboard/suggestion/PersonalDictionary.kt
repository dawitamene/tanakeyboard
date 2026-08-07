package com.addiyon.keyboard.suggestion

/** Small persisted, frequency-ranked dictionary for words learned on-device. */
internal class PersonalDictionary private constructor(
    private val counts: LinkedHashMap<String, Int>
) {
    fun learn(word: String) {
        val value = word.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) return
        counts[value] = (counts[value] ?: 0) + 1
        while (counts.size > MAX_WORDS) counts.remove(counts.keys.first())
    }

    fun completions(prefix: String, limit: Int): List<String> = ranked(prefix, limit)

    fun allWords(): List<String> = counts.keys.toList()

    fun remove(word: String): Boolean {
        val removed = counts.remove(word) != null
        return removed
    }

    fun clear() {
        counts.clear()
    }

    fun emailAddresses(): List<String> = counts.keys.filter { '@' in it }

    fun ranked(prefix: String = "", limit: Int): List<String> = counts.entries
        .asSequence()
        .filter { it.key.startsWith(prefix, ignoreCase = true) }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }
        .toList()

    fun encode(): String = counts.entries.joinToString("\n") { "${it.value}\t${it.key}" }

    companion object {
        private const val MAX_WORDS = 512

        fun decode(encoded: String?): PersonalDictionary {
            val result = LinkedHashMap<String, Int>()
            encoded.orEmpty().lineSequence().forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val count = line.substring(0, tab).toIntOrNull()?.coerceAtLeast(1) ?: return@forEach
                    val word = line.substring(tab + 1).trim()
                    if (word.isNotEmpty() && word.none { it.isWhitespace() }) result[word] = count
                }
            }
            return PersonalDictionary(result)
        }
    }
}
