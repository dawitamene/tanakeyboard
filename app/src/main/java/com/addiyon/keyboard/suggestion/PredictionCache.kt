package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.util.LinkedHashMap

enum class PredictionLanguage {
    AMHARIC,
    ENGLISH,
}

class PredictionCache<V>(private val capacity: Int) {
    private data class Key(
        val language: PredictionLanguage,
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
        language: PredictionLanguage,
        prev2: String?,
        prev1: String,
        limit: Int,
    ): V? = map[key(language, prev2, prev1, limit)]

    @Synchronized
    fun put(
        language: PredictionLanguage,
        prev2: String?,
        prev1: String,
        limit: Int,
        value: V,
    ) {
        map[key(language, prev2, prev1, limit)] = value
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
        language: PredictionLanguage,
        prev2: String?,
        prev1: String,
        limit: Int,
    ): Key {
        require(prev1.isNotEmpty())
        require(limit > 0)
        return Key(
            language = language,
            prev2 = prev2?.takeIf(String::isNotEmpty)?.let { normalize(language, it) },
            prev1 = normalize(language, prev1),
            limit = limit,
        )
    }

    private fun normalize(language: PredictionLanguage, word: String): String =
        when (language) {
            PredictionLanguage.AMHARIC -> EthiopicNormalizer.normalize(word)
            PredictionLanguage.ENGLISH -> buildString(word.length) {
                word.forEach { character ->
                    append(
                        when (character) {
                            '’' -> '\''
                            else -> character.lowercaseChar()
                        }
                    )
                }
            }
        }
}
