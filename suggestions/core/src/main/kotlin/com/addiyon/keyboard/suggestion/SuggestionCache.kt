package com.addiyon.keyboard.suggestion

import java.util.LinkedHashMap

/**
 * Tiny bounded-LRU cache, parameterised only on capacity (entries). Uses
 * [LinkedHashMap]'s access-order mode for O(1) LRU eviction and `.copyOf()`
 * snapshots for the read path so the runtime doesn't have to defensively
 * copy callers' results.
 */
class SuggestionCache<V>(private val capacity: Int) {
    private val map = object : LinkedHashMap<String, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, V>?): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun put(key: String, value: V) {
        map[key] = value
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}
