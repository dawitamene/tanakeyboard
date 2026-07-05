package com.addiyon.tanakeyboard.suggestion

/**
 * A prefix trie over Amharic (Fidel) words, ranked by frequency.
 *
 * WHY KEYED BY FIDEL, NOT LATIN:
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
        var isWord: Boolean = false
        var frequency: Int = 0
    }

    /**
     * Up to [limit] words that start with [prefix], highest frequency
     * first. Empty if [prefix] is empty or matches nothing.
     *
     * Implementation: walks to the trie node for [prefix], then collects
     * every complete word in that subtree before sorting and truncating.
     * Simple correctness-first approach rather than a bounded best-first
     * search -- the worst-case single-character prefix in the bundled
     * dictionary fans out to on the order of tens of thousands of words,
     * which a plain collect+sort comfortably handles well within a frame
     * budget, and fan-out shrinks fast with every additional character
     * typed. Revisit only if real-device profiling shows otherwise.
     */
    fun suggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()

        var node = root
        for (c in prefix) {
            node = node.children[c] ?: return emptyList()
        }

        val matches = mutableListOf<Pair<String, Int>>()
        collect(node, prefix, matches)
        return matches
            .sortedByDescending { (_, frequency) -> frequency }
            .take(limit)
            .map { (word, _) -> word }
    }

    private fun collect(node: Node, prefix: String, out: MutableList<Pair<String, Int>>) {
        if (node.isWord) {
            out.add(prefix to node.frequency)
        }
        for ((c, child) in node.children) {
            collect(child, prefix + c, out)
        }
    }

    companion object {
        /** Builds a trie from (word, frequency) pairs. Later duplicates win. */
        fun build(words: List<Pair<String, Int>>): WordTrie {
            val root = Node()
            for ((word, frequency) in words) {
                if (word.isEmpty()) continue
                var node = root
                for (c in word) {
                    node = node.children.getOrPut(c) { Node() }
                }
                node.isWord = true
                node.frequency = frequency
            }
            return WordTrie(root)
        }
    }
}
