package com.addiyon.keyboard.emoji

/**
 * One pickable emoji: the base glyph, its CLDR name, and any skin-tone
 * variants (fully-qualified sequences, light-to-dark CLDR order; empty for
 * emoji that take no tone). [searchHaystack] is the precomputed lowercase
 * `name<US>keyword,keyword,...` blob queries match against -- built once at
 * parse time so per-keystroke search never lowercases or concatenates.
 */
class Emoji(
    val base: String,
    val name: String,
    val variants: List<String>,
    val searchHaystack: String
)

class EmojiGroup(
    val name: String,
    val emoji: List<Emoji>
)

/**
 * One slot in the picker's flat browse list: a full-width group header or a
 * single emoji cell. Prebuilt at parse time (see [EmojiData.browseCells]) so
 * the grid never assembles or allocates its item list per composition.
 */
sealed class EmojiCell {
    class Header(val title: String) : EmojiCell()
    class Item(val emoji: Emoji) : EmojiCell()
}

/**
 * Parsed emoji picker data -- the pure-Kotlin core of the emoji layer,
 * JVM-testable like [com.addiyon.keyboard.suggestion.WordTrie]. Built from
 * the bundled `emoji.dat` asset (see `tools/build_emoji_data.py` for the
 * format) by [parse]; the Android asset/gzip/renderability plumbing lives in
 * [EmojiRepository].
 */
class EmojiData(val groups: List<EmojiGroup>) {

    /** All emoji across groups, in display order -- the search corpus. */
    val allEmoji: List<Emoji> = groups.flatMap { it.emoji }

    /** Headers and emoji interleaved, in display order -- the grid's items. */
    val browseCells: List<EmojiCell>

    /** For each group (by index), its header's position in [browseCells]. */
    val headerIndices: IntArray

    init {
        val cells = ArrayList<EmojiCell>(groups.size + allEmoji.size)
        val headers = IntArray(groups.size)
        groups.forEachIndexed { index, group ->
            headers[index] = cells.size
            cells.add(EmojiCell.Header(group.name))
            group.emoji.forEach { cells.add(EmojiCell.Item(it)) }
        }
        browseCells = cells
        headerIndices = headers
    }

    /**
     * Case-insensitive search over names and keywords. Emoji whose NAME
     * starts with the query rank before keyword/substring matches ("cat"
     * puts the cat faces before everything merely tagged "cat"); within each
     * tier, display order is kept. Empty/blank queries match nothing.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<Emoji> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val prefixMatches = ArrayList<Emoji>()
        val containsMatches = ArrayList<Emoji>()
        for (emoji in allEmoji) {
            if (emoji.searchHaystack.startsWith(q)) {
                prefixMatches.add(emoji)
                if (prefixMatches.size >= limit) break
            } else if (containsMatches.size < limit && emoji.searchHaystack.contains(q)) {
                containsMatches.add(emoji)
            }
        }
        return (prefixMatches + containsMatches).take(limit)
    }

    companion object {
        const val SEARCH_LIMIT = 60

        // Separates name from keywords in the haystack. Never occurs in CLDR
        // names/keywords, so a query can't accidentally straddle the two.
        private const val HAYSTACK_SEPARATOR = '\u001F'

        /**
         * Parses `emoji.dat` lines (already decompressed) into [EmojiData].
         * [isRenderable] is consulted per emoji string during the same pass:
         * bases that fail are dropped entirely; a failing variant of a
         * passing base drops just that variant (the tone popup shrinks --
         * never shows tofu). Malformed lines are skipped, not fatal: a bad
         * record in a shipped asset should degrade to a missing emoji, not a
         * keyboard crash.
         */
        fun parse(
            lines: Sequence<String>,
            isRenderable: (String) -> Boolean = { true }
        ): EmojiData {
            val groups = ArrayList<EmojiGroup>()
            var groupName: String? = null
            var groupEmoji = ArrayList<Emoji>()

            fun closeGroup() {
                val name = groupName
                if (name != null && groupEmoji.isNotEmpty()) {
                    groups.add(EmojiGroup(name, groupEmoji))
                }
            }

            for (line in lines) {
                when {
                    line.startsWith("G\t") -> {
                        closeGroup()
                        groupName = line.substring(2)
                        groupEmoji = ArrayList()
                    }
                    line.startsWith("E\t") -> {
                        if (groupName == null) continue
                        // E<TAB>emoji<TAB>name<TAB>keywords<TAB>variants
                        val fields = line.split('\t')
                        if (fields.size != 5) continue
                        val base = fields[1]
                        val name = fields[2]
                        if (base.isEmpty() || name.isEmpty()) continue
                        if (!isRenderable(base)) continue
                        val variants = fields[4]
                            .split(' ')
                            .filter { it.isNotEmpty() && isRenderable(it) }
                        groupEmoji.add(
                            Emoji(
                                base = base,
                                name = name,
                                variants = variants,
                                searchHaystack =
                                    "$name$HAYSTACK_SEPARATOR${fields[3]}".lowercase()
                            )
                        )
                    }
                    // "V" version line and anything unrecognized: skipped.
                }
            }
            closeGroup()
            return EmojiData(groups)
        }
    }
}
