package com.addiyon.keyboard.emoji

/**
 * The "Recently used" emoji list: LRU, most-recent-first, capped at
 * [capacity]. Stores the exact committed string, so a skin-toned commit
 * shows up toned in recents (Gboard behavior).
 *
 * Pure Kotlin, JVM-testable like [EmojiData]: persistence is injected as
 * [load]/[save] lambdas (backed by SharedPreferences in the service), and
 * the codec is a '\u001F'-joined string -- the separator can't occur inside
 * an emoji sequence, so no escaping is needed. A corrupt persisted value
 * degrades to dropping the unreadable entries, never to a crash.
 */
class RecentEmojiStore(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val capacity: Int = DEFAULT_CAPACITY
) {
    // Most-recent-first. Null until first use, so constructing the store in
    // onCreate never touches persistence.
    private var recents: ArrayDeque<String>? = null

    private fun items(): ArrayDeque<String> {
        recents?.let { return it }
        val decoded = ArrayDeque<String>()
        load()?.split(SEPARATOR)?.forEach {
            if (it.isNotEmpty() && decoded.size < capacity) decoded.addLast(it)
        }
        recents = decoded
        return decoded
    }

    /** Most-recent-first copy, safe to hold across later [recordUse] calls. */
    fun snapshot(): List<String> = items().toList()

    fun recordUse(emoji: String) {
        if (emoji.isEmpty() || emoji.contains(SEPARATOR)) return
        val list = items()
        list.remove(emoji)
        list.addFirst(emoji)
        while (list.size > capacity) list.removeLast()
        save(list.joinToString(SEPARATOR.toString()))
    }

    companion object {
        const val DEFAULT_CAPACITY = 32
        private const val SEPARATOR = '\u001F'
    }
}
