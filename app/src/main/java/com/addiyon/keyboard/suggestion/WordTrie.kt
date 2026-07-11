package com.addiyon.keyboard.suggestion

import java.util.PriorityQueue

/**
 * A prefix trie over words, ranked by frequency.
 *
 * CASE-INSENSITIVE MATCHING, CANONICAL CASING PRESERVED:
 *
 * Path edges are keyed by the *lowercased* character, so a lookup prefix
 * matches regardless of the case it's typed in ("eng", "Eng", "ENG" all reach
 * the same node). Each terminal node stores the word's *canonical* display
 * form separately (see [Node.word]) rather than rebuilding it from the path --
 * that's what lets the English dictionary carry proper-noun casing ("england"
 * on the path, "England" as the stored/returned word) while still being found
 * from a lowercase prefix. For scripts without case (Ge'ez/Fidel)
 * `lowercaseChar()` is the identity, so this is a no-op there.
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
class WordTrie private constructor(private val root: Node) {

    private class Node {
        val children = HashMap<Char, Node>()

        /**
         * Canonical display form of the word ending at this node, or null if
         * no word ends here. Stored rather than reconstructed from the path
         * because the path is lowercased for case-insensitive matching while
         * this preserves the original casing (e.g. path "england" -> "England").
         */
        var word: String? = null
        var frequency: Int = 0

        /**
         * Highest [frequency] of any complete word in this node's subtree
         * (itself included). Precomputed at build time so [suggestions] can run
         * a best-first search -- expanding only branches that could still beat
         * what's already been emitted -- instead of scanning the whole subtree.
         */
        var subtreeMaxFrequency: Int = 0
    }

    /**
     * Up to [limit] words that start with [prefix] (case-insensitively),
     * highest frequency first. Empty if [prefix] is empty or matches nothing.
     * The returned strings carry each word's canonical casing.
     *
     * Best-first search: a max-heap of pending items ordered by an upper bound
     * on the frequency still reachable through them (a node's
     * [Node.subtreeMaxFrequency], a word's own frequency). Because any word in
     * a subtree has frequency <= that subtree's max, popping a word means no
     * un-expanded branch could contain a higher one -- so we stop as soon as
     * [limit] words are emitted, touching work proportional to the results
     * rather than the whole subtree. This keeps latency flat as the dictionary
     * grows (short, high-fan-out prefixes like "e" no longer scan everything).
     */
    fun suggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()

        var node = root
        for (c in prefix) {
            node = node.children[c.lowercaseChar()] ?: return emptyList()
        }

        val results = ArrayList<String>(limit)
        // Each Candidate is either a subtree to expand (node != null, priority =
        // subtreeMaxFrequency) or a word to emit (emit != null, priority =
        // frequency). Highest priority pops first.
        val frontier = PriorityQueue<Candidate>(compareByDescending { it.priority })
        frontier.add(Candidate(node.subtreeMaxFrequency, node, null))

        while (results.size < limit) {
            val c = frontier.poll() ?: break
            if (c.emit != null) {
                results.add(c.emit)
                continue
            }
            val n = c.node!!
            n.word?.let { frontier.add(Candidate(n.frequency, null, it)) }
            for (child in n.children.values) {
                frontier.add(Candidate(child.subtreeMaxFrequency, child, null))
            }
        }
        return results
    }

    private class Candidate(val priority: Int, val node: Node?, val emit: String?)

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
            node = node.children[c.lowercaseChar()] ?: return null
        }
        return node.word?.let { node.frequency }
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
        searchFuzzy(root, ' ', typed, initialRow, null, initialRow[typed.size], maxEdits, limit, costs, best)

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
     * distance. See [fuzzySuggestions] for the two regimes.
     */
    private fun searchFuzzy(
        node: Node,
        prevLetter: Char,
        typed: CharArray,
        row: IntArray,
        prevRow: IntArray?,
        minSoFar: Int,
        maxEdits: Int,
        limit: Int,
        costs: Costs,
        best: HashMap<String, FuzzyMatch>,
    ) {
        // This node's own prefix is in budget -> if it terminates a word, emit
        // it at the accurate running distance.
        if (minSoFar <= maxEdits) node.word?.let { record(best, it, minSoFar, node.frequency) }

        for ((edge, child) in node.children) {
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
                nextMin <= maxEdits ->
                    searchFuzzy(child, edge, typed, next, row, childMin, maxEdits, limit, costs, best)

                // DP is exhausted for this branch, but we already have an
                // in-budget prefix above it -> everything below is a valid
                // completion at that distance; collect the best by frequency.
                minSoFar <= maxEdits ->
                    for ((word, frequency) in collectBest(child, limit)) {
                        record(best, word, minSoFar, frequency)
                    }

                // else: no in-budget prefix and DP can't recover -> prune.
            }
        }
    }

    /** Top [limit] (word, frequency) pairs in [start]'s subtree, best-first --
     *  the same bounded search as [suggestions], from an arbitrary node. */
    private fun collectBest(start: Node, limit: Int): List<Pair<String, Int>> {
        val results = ArrayList<Pair<String, Int>>(limit)
        val frontier = PriorityQueue<Candidate>(compareByDescending { it.priority })
        frontier.add(Candidate(start.subtreeMaxFrequency, start, null))
        while (results.size < limit) {
            val c = frontier.poll() ?: break
            if (c.emit != null) {
                results.add(c.emit to c.priority)
                continue
            }
            val n = c.node!!
            n.word?.let { frontier.add(Candidate(n.frequency, null, it)) }
            for (child in n.children.values) {
                frontier.add(Candidate(child.subtreeMaxFrequency, child, null))
            }
        }
        return results
    }

    companion object {
        /**
         * Builds a trie from (word, frequency) pairs. Keys are matched
         * case-insensitively; when two words share a lowercased key (e.g.
         * "polish"/"Polish") the higher-frequency one wins its stored form --
         * deterministic and independent of input order.
         */
        fun build(words: List<Pair<String, Int>>): WordTrie = build(words.asSequence())

        /**
         * Same as the [List] overload, but consumes a [Sequence] so a caller
         * streaming lines off disk (e.g. [WordDictionary]) never has to
         * materialize the full (word, frequency) list before building the
         * trie -- halving peak memory during the load, which matters on
         * low-RAM devices where that allocation spike can stall the main
         * thread via GC.
         */
        fun build(words: Sequence<Pair<String, Int>>): WordTrie {
            val root = Node()
            for ((word, frequency) in words) {
                if (word.isEmpty()) continue
                var node = root
                for (c in word) {
                    node = node.children.getOrPut(c.lowercaseChar()) { Node() }
                }
                if (node.word == null || frequency > node.frequency) {
                    node.word = word
                    node.frequency = frequency
                }
            }
            computeSubtreeMax(root)
            return WordTrie(root)
        }

        /** Post-order fill of [Node.subtreeMaxFrequency] over the whole trie. */
        private fun computeSubtreeMax(node: Node): Int {
            var max = if (node.word != null) node.frequency else 0
            for (child in node.children.values) {
                val childMax = computeSubtreeMax(child)
                if (childMax > max) max = childMax
            }
            node.subtreeMaxFrequency = max
            return max
        }
    }
}
