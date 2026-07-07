package com.addiyon.tanakeyboard.suggestion

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
 * in [com.addiyon.tanakeyboard.transliteration.Transliterator] isn't
 * reliably invertible). Keying by Fidel instead means the caller just
 * prefix-matches against whatever
 * [com.addiyon.tanakeyboard.transliteration.Transliterator.transliterate]
 * already produced for the live composing buffer -- the exact same string
 * already shown on screen, no new derivation needed.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable without an
 * emulator, the same reasoning documented on
 * [com.addiyon.tanakeyboard.transliteration.AmharicTable].
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

    companion object {
        /**
         * Builds a trie from (word, frequency) pairs. Keys are matched
         * case-insensitively; when two words share a lowercased key (e.g.
         * "polish"/"Polish") the higher-frequency one wins its stored form --
         * deterministic and independent of input order.
         */
        fun build(words: List<Pair<String, Int>>): WordTrie {
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
