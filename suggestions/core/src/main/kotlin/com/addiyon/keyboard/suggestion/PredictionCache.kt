package com.addiyon.keyboard.suggestion

import java.util.LinkedHashMap

class PredictionCache<V>(
    private val capacity: Int,
    private val normalizeWord: (languageId: String, word: String) -> String = { _, word ->
        buildString(word.length) {
            word.forEach { character ->
                append(if (character == '’') '\'' else character.lowercaseChar())
            }
        }
    }
) {
    private data class Key(
        val languageId: String,
        val prev2: String?,
        val prev1: String,
        val limit: Int,
    )

    private val map = object : LinkedHashMap<Key, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Key, V>?): Boolean =
            size > capacity
    }

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun get(
        languageId: String,
        prev2: String?,
        prev1: String,
        limit: Int,
    ): V? = map[key(languageId, prev2, prev1, limit)]

    @Synchronized
    fun put(
        languageId: String,
        prev2: String?,
        prev1: String,
        limit: Int,
        value: V,
    ) {
        map[key(languageId, prev2, prev1, limit)] = value
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun trimToSize(maxEntries: Int) {
        require(maxEntries >= 0)
        while (map.size > maxEntries) {
            val iterator = map.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    @Synchronized
    fun size(): Int = map.size

    private fun key(
        languageId: String,
        prev2: String?,
        prev1: String,
        limit: Int,
    ): Key {
        require(prev1.isNotEmpty())
        require(limit > 0)
        return Key(
            languageId = languageId,
            prev2 = prev2?.takeIf(String::isNotEmpty)?.let { normalizeWord(languageId, it) },
            prev1 = normalizeWord(languageId, prev1),
            limit = limit,
        )
    }
}
