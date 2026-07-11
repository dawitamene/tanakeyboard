package com.addiyon.keyboard.suggestion

import java.util.PriorityQueue

/**
 * A prefix trie over words, ranked by frequency.
 *
 * COMPACT FLAT-ARRAY REPRESENTATION:
 *
 * Nodes are not objects. The whole trie lives in parallel primitive arrays
 * indexed by node id -- edge char in, terminal frequency, subtree max, and a
 * (offset, count) slice into a shared [childIndices] array whose entries are
 * the node's children in ascending edge-char order (child lookup is a binary
 * search over that slice). At dictionary scale this matters enormously: the
 * Amharic asset is ~411k words / ~680k trie nodes, which as one object +
 * HashMap per node (the previous design) costs well over 100MB of heap in an
 * IME process; as flat arrays it's ~15MB, and building it allocates no
 * intermediate node graph at all (see [build]), so there's no GC-pause spike
 * during the background load either.
 *
 * CASE-INSENSITIVE MATCHING, CANONICAL CASING PRESERVED:
 *
 * Path edges are keyed by the *lowercased* character, so a lookup prefix
 * matches regardless of the case it's typed in ("eng", "Eng", "ENG" all reach
 * the same node). A word's display form is reconstructed from its (lowercased)
 * path; the [canonicalWord] map holds the display form for only those words
 * where the two differ (English proper nouns and contractions -- "england" on
 * the path, "England" stored) rather than storing a String per word. For
 * scripts without case (Ge'ez/Fidel) `lowercaseChar()` is the identity and the
 * map is empty.
 *
 * SORTED-INPUT STREAMING BUILD:
 *
 * [build] requires its input ordered by the lowercased key in UTF-16
 * code-unit order (plain `String.compareTo`) and throws on out-of-order input
 * -- the bundled `.dat` assets are sorted that way by their `tools/` build
 * scripts, and the unit test that loads the real assets catches any drift.
 * Sorted input is what lets the trie be built in one streaming pass with a
 * stack of open path frames (children finalize before parents, post-order),
 * emitting straight into the flat arrays.
 *
 * WHY THE AMHARIC SIDE IS KEYED BY FIDEL, NOT LATIN:
 *
 * The obvious-looking alternative -- keying suggestions off the user's
 * romanized (SERA) buffer -- would require reverse-transliterating Fidel
 * back to a Latin spelling, which is lossy/ambiguous (the forward mapping
 * in [com.addiyon.keyboard.transliteration.Transliterator] isn't
 * reliably invertible). Keying by Fidel instead means the caller just
 * prefix-matches against whatever
 * [com.addiyon.keyboard.transliteration.Transliterator.transliterate]
 * already produced for the live composing buffer -- the exact same string
 * already shown on screen, no new derivation needed.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable without an
 * emulator, the same reasoning documented on
 * [com.addiyon.keyboard.transliteration.AmharicTable].
 */
class WordTrie private constructor(
    /** Edge char (lowercased) leading into each node; unused for the root. */
    private val edgeChar: CharArray,
    /** Terminal frequency per node, or [NO_WORD] if no word ends there. */
    private val frequency: IntArray,
    /** Highest terminal frequency in each node's subtree (itself included). */
    private val subtreeMax: IntArray,
    private val childOffset: IntArray,
    private val childCount: IntArray,
    /** All child lists, concatenated; each node's slice is edge-sorted. */
    private val childIndices: IntArray,
    /** node id -> display form, only where it differs from the node's path. */
    private val canonicalWord: HashMap<Int, String>,
    private val root: Int,
) {
    data class Suggestion(val word: String, val frequency: Int)

    private fun isWordNode(node: Int) = frequency[node] != NO_WORD

    /** Display form of the word terminating at [node], whose path is [path]. */
    private fun wordAt(node: Int, path: String): String = canonicalWord[node] ?: path

    /** Binary search of [node]'s edge-sorted child slice for edge [c]. */
    private fun childOf(node: Int, c: Char): Int {
        var lo = childOffset[node]
        var hi = lo + childCount[node] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val child = childIndices[mid]
            val edge = edgeChar[child]
            when {
                edge < c -> lo = mid + 1
                edge > c -> hi = mid - 1
                else -> return child
            }
        }
        return -1
    }

    /** Node reached by walking [prefix] (lowercased) from the root, and the
     *  lowercased path walked, or null if the prefix isn't in the trie. */
    private fun descend(prefix: String): Pair<Int, String>? {
        var node = root
        val path = StringBuilder(prefix.length)
        for (c in prefix) {
            val lc = c.lowercaseChar()
            node = childOf(node, lc)
            if (node < 0) return null
            path.append(lc)
        }
        return node to path.toString()
    }

    /**
     * Up to [limit] words that start with [prefix] (case-insensitively),
     * highest frequency first. Empty if [prefix] is empty or matches nothing.
     * The returned strings carry each word's canonical casing.
     *
     * Best-first search: a max-heap of pending items ordered by an upper bound
     * on the frequency still reachable through them (a node's [subtreeMax], a
     * word's own frequency). Because any word in a subtree has frequency <=
     * that subtree's max, popping a word means no un-expanded branch could
     * contain a higher one -- so we stop as soon as [limit] words are emitted,
     * touching work proportional to the results rather than the whole subtree.
     * This keeps latency flat as the dictionary grows (short, high-fan-out
     * prefixes like "e" no longer scan everything).
     */
    fun suggestions(prefix: String, limit: Int = 3): List<String> =
        suggestionEntries(prefix, limit).map { it.word }

    fun suggestionEntries(prefix: String, limit: Int = 3): List<Suggestion> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val (start, startPath) = descend(prefix) ?: return emptyList()

        val results = ArrayList<Suggestion>(limit)
        // Each Candidate is either a subtree to expand (node >= 0, priority =
        // subtreeMax, path = the node's lowercased path) or a word to emit
        // (emit != null, priority = frequency). Highest priority pops first.
        val frontier = PriorityQueue<Candidate>(compareByDescending { it.priority })
        frontier.add(Candidate(subtreeMax[start], start, startPath, null))

        while (results.size < limit) {
            val c = frontier.poll() ?: break
            if (c.emit != null) {
                results.add(c.emit)
                continue
            }
            val n = c.node
            if (isWordNode(n)) {
                frontier.add(Candidate(frequency[n], -1, "", Suggestion(wordAt(n, c.path), frequency[n])))
            }
            forEachChild(n) { child ->
                frontier.add(Candidate(subtreeMax[child], child, c.path + edgeChar[child], null))
            }
        }
        return results
    }

    private class Candidate(val priority: Int, val node: Int, val path: String, val emit: Suggestion?)

    private inline fun forEachChild(node: Int, action: (Int) -> Unit) {
        val off = childOffset[node]
        for (i in off until off + childCount[node]) action(childIndices[i])
    }

    /**
     * The stored frequency of [word] if it's an exact entry (case-insensitive
     * path match, a la [suggestions]), else null. O(word length); used by
     * [com.addiyon.keyboard.suggestion.CandidateRanker] to promote a
     * dictionary-exact transliteration reading ahead of the structurally
     * greedy one.
     */
    fun frequencyOf(word: String): Int? {
        if (word.isEmpty()) return null
        var node = root
        for (c in word) {
            node = childOf(node, c.lowercaseChar())
            if (node < 0) return null
        }
        return if (isWordNode(node)) frequency[node] else null
    }

    /**
     * Cost of substituting one character for another during fuzzy matching.
     * Returns 0 when the two are "equal enough" to not count as an edit. The
     * default is uniform (any mismatch costs 1); the Amharic path injects a
     * script-aware cost (a wrong vowel on the right consonant is cheaper than a
     * wrong consonant) -- see
     * [com.addiyon.keyboard.transliteration.AmharicTable.fidelSubstitutionCost].
     * Characters here are already lowercased (trie edges and the lookup prefix
     * are lowercased before matching), matching how [suggestions] keys nodes.
     */
    fun interface SubstitutionCost {
        fun of(typedChar: Char, wordChar: Char): Int
    }

    /** A fuzzy hit: the canonical [word], its [editDistance] from the typed
     *  prefix (an upper bound; see [fuzzySuggestions]), and its [frequency]. */
    data class FuzzyMatch(val word: String, val editDistance: Int, val frequency: Int)

    /**
     * Up to [limit] words whose *prefix* is within [maxEdits] of [prefix]
     * (case-insensitively), for typo / near-miss correction ("informtion" ->
     * "information"; over Fidel, የተለይ -> የተለያ… with the Amharic cost model).
     * Ordered by (editDistance asc, frequency desc). Empty for an empty prefix,
     * `maxEdits <= 0`, or no match within budget.
     *
     * FUZZY-*PREFIX* SEMANTICS: a word W matches if the typed string is within
     * [maxEdits] of some prefix of W (the rest of W is simply un-typed-yet), so
     * completion still works while a typo is present. W's distance is the min,
     * over the nodes on its path, of `editDistance(typed, thatWordPrefix)`.
     *
     * [insertCost] / [deleteCost] weight the two indel edits independently of
     * [substitutionCost] (default 1 each = plain Levenshtein). The Amharic path
     * raises both so the *only* cheap edit is a same-family vowel substitution:
     * otherwise "delete the last typed fidel, then complete freely" would let a
     * wrong-consonant word (የተለሽም for የተለይ) match as cheaply as the intended
     * same-consonant one (የተለያዩ).
     *
     * Bounded Damerau-Levenshtein over the trie: a DP row over the typed
     * positions is carried down each edge (one edge = one word char), and
     * `minSoFar` tracks the best `row[last]` seen on the path (the running
     * fuzzy-prefix distance). Two regimes keep the work bounded:
     *  - while still searching for an in-budget prefix, a subtree is pruned as
     *    soon as `row.min() > maxEdits` (no descendant row can dip back into
     *    budget -- row minima are non-decreasing down the trie);
     *  - once a prefix is in budget (`minSoFar <= maxEdits`), every word below
     *    it is a valid completion, so children the DP would otherwise prune are
     *    handed to the same frequency-ranked best-first search as [suggestions]
     *    (tagged with `minSoFar`) instead of being dropped. A word reached both
     *    ways keeps its lower (DP-accurate) distance.
     */
    fun fuzzySuggestions(
        prefix: String,
        maxEdits: Int,
        limit: Int = 3,
        substitutionCost: SubstitutionCost = SubstitutionCost { a, b -> if (a == b) 0 else 1 },
        insertCost: Int = 1,
        deleteCost: Int = 1,
    ): List<FuzzyMatch> {
        if (prefix.isEmpty() || maxEdits <= 0 || limit <= 0) return emptyList()
        val typed = CharArray(prefix.length) { prefix[it].lowercaseChar() }

        // word -> best (lowest) distance found for it.
        val best = HashMap<String, FuzzyMatch>()
        // DP row 0: turning "" (empty word-prefix) into typed[0..j) is j
        // insertions; minSoFar starts as row[last] = typed.size * insertCost.
        val initialRow = IntArray(typed.size + 1) { it * insertCost }
        val costs = Costs(substitutionCost, insertCost, deleteCost)
        val path = StringBuilder()
        searchFuzzy(root, ' ', typed, initialRow, null, initialRow[typed.size], maxEdits, limit, costs, best, path)

        return best.values
            .sortedWith(compareBy({ it.editDistance }, { -it.frequency }))
            .take(limit)
    }

    /**
     * Records [word] as a fuzzy hit at [distance] into [best], keeping the
     * lowest distance if it's seen more than once.
     */
    private fun record(best: HashMap<String, FuzzyMatch>, word: String, distance: Int, frequency: Int) {
        val existing = best[word]
        if (existing == null || distance < existing.editDistance) {
            best[word] = FuzzyMatch(word, distance, frequency)
        }
    }

    /** The three edit weights for a fuzzy walk, bundled to keep signatures small. */
    private class Costs(
        val substitution: SubstitutionCost,
        val insert: Int,
        val delete: Int,
    )

    /**
     * One step of the bounded Damerau-Levenshtein walk over the trie. [row] is
     * the DP row for the word-prefix ending at [node] (reached via edge
     * [prevLetter]); [prevLetter] is that edge, used with the row two levels up
     * (passed as [prevRow]) for the transposition term. [minSoFar] is the least
     * `row[last]` seen from the root down to [node] -- the running fuzzy-prefix
     * distance. [path] holds [node]'s lowercased path (appended/truncated as
     * the walk descends/returns). See [fuzzySuggestions] for the two regimes.
     */
    private fun searchFuzzy(
        node: Int,
        prevLetter: Char,
        typed: CharArray,
        row: IntArray,
        prevRow: IntArray?,
        minSoFar: Int,
        maxEdits: Int,
        limit: Int,
        costs: Costs,
        best: HashMap<String, FuzzyMatch>,
        path: StringBuilder,
    ) {
        // This node's own prefix is in budget -> if it terminates a word, emit
        // it at the accurate running distance.
        if (minSoFar <= maxEdits && isWordNode(node)) {
            record(best, wordAt(node, path.toString()), minSoFar, frequency[node])
        }

        forEachChild(node) { child ->
            val edge = edgeChar[child]
            val next = IntArray(typed.size + 1)
            next[0] = row[0] + costs.delete   // consuming a word char, no typed char
            var nextMin = next[0]
            for (j in 1..typed.size) {
                val insert = next[j - 1] + costs.insert
                val delete = row[j] + costs.delete
                val replace = row[j - 1] + costs.substitution.of(typed[j - 1], edge)
                var cost = minOf(insert, delete, replace)
                // Damerau transposition: typed "…ab" vs word "…ba".
                if (prevRow != null && j >= 2 &&
                    typed[j - 1] == prevLetter && typed[j - 2] == edge
                ) {
                    cost = minOf(cost, prevRow[j - 2] + 1)
                }
                next[j] = cost
                if (cost < nextMin) nextMin = cost
            }
            val childMin = minOf(minSoFar, next[typed.size])

            when {
                // The DP can still make progress (or improve the distance) here.
                nextMin <= maxEdits -> {
                    path.append(edge)
                    searchFuzzy(child, edge, typed, next, row, childMin, maxEdits, limit, costs, best, path)
                    path.setLength(path.length - 1)
                }

                // DP is exhausted for this branch, but we already have an
                // in-budget prefix above it -> everything below is a valid
                // completion at that distance; collect the best by frequency.
                minSoFar <= maxEdits ->
                    for ((word, frequency) in collectBest(child, path.toString() + edge, limit)) {
                        record(best, word, minSoFar, frequency)
                    }

                // else: no in-budget prefix and DP can't recover -> prune.
            }
        }
    }

    /** Top [limit] (word, frequency) pairs in [start]'s subtree (whose
     *  lowercased path is [startPath]), best-first -- the same bounded search
     *  as [suggestions], from an arbitrary node. */
    private fun collectBest(start: Int, startPath: String, limit: Int): List<Pair<String, Int>> {
        val results = ArrayList<Pair<String, Int>>(limit)
        val frontier = PriorityQueue<Candidate>(compareByDescending { it.priority })
        frontier.add(Candidate(subtreeMax[start], start, startPath, null))
        while (results.size < limit) {
            val c = frontier.poll() ?: break
            if (c.emit != null) {
                results.add(c.emit.word to c.emit.frequency)
                continue
            }
            val n = c.node
            if (isWordNode(n)) {
                frontier.add(Candidate(frequency[n], -1, "", Suggestion(wordAt(n, c.path), frequency[n])))
            }
            forEachChild(n) { child ->
                frontier.add(Candidate(subtreeMax[child], child, c.path + edgeChar[child], null))
            }
        }
        return results
    }

    companion object {
        /** Sentinel in [frequency] marking "no word terminates at this node".
         *  A sentinel rather than 0 so genuinely-zero frequencies still work. */
        private const val NO_WORD = Int.MIN_VALUE

        /**
         * Builds a trie from (word, frequency) pairs, sorting them first --
         * the convenience entry point for small in-memory lists (tests). Keys
         * are matched case-insensitively; when two words share a lowercased
         * key (e.g. "polish"/"Polish") the higher-frequency one wins its
         * stored form -- deterministic and independent of input order.
         */
        fun build(words: List<Pair<String, Int>>): WordTrie =
            build(words.sortedBy { lowercaseKey(it.first) }.asSequence())

        /**
         * Streaming build from a [Sequence] ALREADY SORTED by the lowercased
         * key in UTF-16 code-unit order (throws [IllegalArgumentException]
         * otherwise -- fail fast rather than silently mis-building). A caller
         * streaming lines off disk (e.g. [WordDictionary], whose `.dat` assets
         * are sorted at asset-build time) never materializes the word list OR
         * an intermediate node graph: a stack of open path frames emits
         * finalized nodes straight into the flat arrays, so peak load memory
         * is essentially the final trie itself -- which matters on low-RAM
         * devices where an allocation spike can stall the main thread via GC.
         */
        fun build(words: Sequence<Pair<String, Int>>): WordTrie {
            val builder = Builder()
            for ((word, frequency) in words) {
                if (word.isEmpty()) continue
                builder.add(word, frequency)
            }
            return builder.finish()
        }

        /** The word as trie-path key: each char lowercased, matching how
         *  edges are keyed and how lookups lowercase the prefix. */
        private fun lowercaseKey(word: String): String {
            val sb = StringBuilder(word.length)
            for (c in word) sb.append(c.lowercaseChar())
            return sb.toString()
        }
    }

    /** Growable IntArray, to build the flat arrays without boxing. */
    private class IntList(initialCapacity: Int = 1024) {
        private var array = IntArray(initialCapacity)
        var size = 0
            private set

        fun add(value: Int) {
            if (size == array.size) array = array.copyOf(size * 2)
            array[size++] = value
        }

        operator fun get(index: Int) = array[index]

        fun clear() {
            size = 0
        }

        fun toArray() = array.copyOf(size)
    }

    /**
     * Streaming trie builder over sorted input. The stack holds one [Frame]
     * per char of the current word's lowercased key (plus the root at the
     * bottom); when the next word diverges from the previous one, the frames
     * below the divergence point are finalized -- their children are already
     * final (post-order), so each flushes its child list contiguously into
     * [childIndices] and appends itself to the node arrays.
     */
    private class Builder {
        private val edgeChar = StringBuilder()
        private val frequency = IntList()
        private val subtreeMax = IntList()
        private val childOffset = IntList()
        private val childCount = IntList()
        private val childIndices = IntList()
        private val canonicalWord = HashMap<Int, String>()

        private class Frame(var edge: Char) {
            var frequency = NO_WORD
            var canonical: String? = null
            val children = IntList(initialCapacity = 4)

            fun reset(edge: Char) {
                this.edge = edge
                frequency = NO_WORD
                canonical = null
                children.clear()
            }
        }

        // stack[0] is the root frame; stack[d] corresponds to prevKey[d - 1].
        private val stack = ArrayList<Frame>().apply { add(Frame(' ')) }
        private var depth = 0
        private var prevKey = ""

        fun add(word: String, freq: Int) {
            val key = lowercaseKey(word)
            val commonPrefix = commonPrefixLength(prevKey, key)
            // Sorted means: key extends prevKey, equals it, or diverges with a
            // strictly greater char. A shorter key that never diverges (a
            // proper prefix of prevKey) or a smaller divergence char is out of
            // order.
            val inOrder = when {
                commonPrefix == key.length -> key.length == prevKey.length
                commonPrefix == prevKey.length -> true
                else -> key[commonPrefix] > prevKey[commonPrefix]
            }
            require(inOrder) {
                "input not sorted: \"$word\" (key \"$key\") after key \"$prevKey\""
            }

            while (depth > commonPrefix) popFrame()
            for (i in commonPrefix until key.length) pushFrame(key[i])

            // Duplicate lowercased keys (adjacent, since sorted): the
            // higher-frequency entry wins both the frequency and the stored
            // display form -- deterministic and independent of input order.
            val frame = stack[depth]
            if (frame.frequency == NO_WORD || freq > frame.frequency) {
                frame.frequency = freq
                frame.canonical = if (word != key) word else null
            }
            prevKey = key
        }

        fun finish(): WordTrie {
            while (depth > 0) popFrame()
            val root = finalizeFrame(stack[0])
            return WordTrie(
                edgeChar = CharArray(edgeChar.length) { edgeChar[it] },
                frequency = frequency.toArray(),
                subtreeMax = subtreeMax.toArray(),
                childOffset = childOffset.toArray(),
                childCount = childCount.toArray(),
                childIndices = childIndices.toArray(),
                canonicalWord = canonicalWord,
                root = root,
            )
        }

        private fun pushFrame(edge: Char) {
            depth++
            if (depth == stack.size) stack.add(Frame(edge)) else stack[depth].reset(edge)
        }

        private fun popFrame() {
            val node = finalizeFrame(stack[depth])
            depth--
            stack[depth].children.add(node)
        }

        /** Emits [frame] as a node (children already emitted) and returns its id. */
        private fun finalizeFrame(frame: Frame): Int {
            val node = frequency.size
            val kids = frame.children
            childOffset.add(childIndices.size)
            childCount.add(kids.size)
            var max = frame.frequency
            for (i in 0 until kids.size) {
                val child = kids[i]
                childIndices.add(child)
                if (subtreeMax[child] > max) max = subtreeMax[child]
            }
            edgeChar.append(frame.edge)
            frequency.add(frame.frequency)
            subtreeMax.add(max)
            frame.canonical?.let { canonicalWord[node] = it }
            return node
        }

        private fun commonPrefixLength(a: String, b: String): Int {
            val n = minOf(a.length, b.length)
            var i = 0
            while (i < n && a[i] == b[i]) i++
            return i
        }
    }
}
