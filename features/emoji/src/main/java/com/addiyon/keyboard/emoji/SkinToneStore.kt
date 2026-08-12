package com.addiyon.keyboard.emoji

/**
 * Remembered skin-tone choices: base emoji -> the variant the user last
 * picked from the long-press popup, so the grid cell shows that tone from
 * then on (Gboard behavior). Picking the base (yellow) again clears the
 * entry rather than storing an identity mapping.
 *
 * Pure Kotlin with injected [load]/[save] persistence, like
 * [RecentEmojiStore]. Codec: entries joined by '\u001F', each entry
 * `base<TAB>variant` -- neither character can occur inside an emoji
 * sequence. Malformed entries in a persisted value are skipped, not fatal.
 */
class SkinToneStore(
    private val load: () -> String?,
    private val save: (String) -> Unit
) {
    private var tones: LinkedHashMap<String, String>? = null

    private fun map(): LinkedHashMap<String, String> {
        tones?.let { return it }
        val decoded = LinkedHashMap<String, String>()
        load()?.split(ENTRY_SEPARATOR)?.forEach { entry ->
            val tab = entry.indexOf(PAIR_SEPARATOR)
            if (tab <= 0 || tab == entry.length - 1) return@forEach
            decoded[entry.substring(0, tab)] = entry.substring(tab + 1)
        }
        tones = decoded
        return decoded
    }

    fun all(): Map<String, String> = map().toMap()

    fun set(base: String, variant: String) {
        if (base.isEmpty() || base.contains(ENTRY_SEPARATOR) || base.contains(PAIR_SEPARATOR)) return
        if (variant.contains(ENTRY_SEPARATOR) || variant.contains(PAIR_SEPARATOR)) return
        val m = map()
        if (variant == base || variant.isEmpty()) m.remove(base) else m[base] = variant
        save(m.entries.joinToString(ENTRY_SEPARATOR.toString()) {
            "${it.key}$PAIR_SEPARATOR${it.value}"
        })
    }

    companion object {
        private const val ENTRY_SEPARATOR = '\u001F'
        private const val PAIR_SEPARATOR = '\t'
    }
}
