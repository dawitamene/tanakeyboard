package com.addiyon.keyboard.suggestion

internal class PerWordCache<K, V> {
    private data class Entry<K, V>(
        val key: K,
        val value: V
    )

    private var entry: Entry<K, V>? = null

    fun getOrCapture(key: K, capture: () -> V): V {
        val current = entry
        if (current != null && current.key == key) {
            return current.value
        }
        return capture().also { value ->
            entry = Entry(key, value)
        }
    }

    fun clear() {
        entry = null
    }
}
