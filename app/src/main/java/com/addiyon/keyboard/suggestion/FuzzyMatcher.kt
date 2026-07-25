package com.addiyon.keyboard.suggestion

/**
 * Bounded Levenshtein (insert / delete / substitute) over a small candidate
 * set, with two rolling DP rows to keep allocation tight. Same shape as the
 * old [WordTrie.searchFuzzy] fuzzy pass, just stripped of trie recursion and
 * given a pre-fetched candidate list (typically the result of a short
 * `LIKE 'X%'` prefix query, which is how the SQLite-backed runtime fetches
 * its candidates). The [SubstitutionCost] is a per-character callback so the
 * Amharic caller can route the fidel-aware edit cost through here.
 */
class FuzzyMatcher(
    private val maxEdits: Int,
    private val insertCost: Int = 1,
    private val deleteCost: Int = 1,
    private val substitutionCost: SubstitutionCost = SubstitutionCost { a, b -> if (a == b) 0 else 1 },
) {
    fun rank(prefix: String, candidates: List<Pair<String, Int>>): List<FuzzyMatch> {
        if (maxEdits < 0 || prefix.isEmpty()) return emptyList()
        val result = ArrayList<FuzzyMatch>(candidates.size.coerceAtMost(64))
        val longestCandidate = candidates.maxOfOrNull { it.first.length } ?: 0
        val previous = IntArray(longestCandidate + 1)
        val current = IntArray(longestCandidate + 1)
        for ((candidate, frequency) in candidates) {
            val minimumLengthCost = if (candidate.length >= prefix.length) {
                (candidate.length - prefix.length) * insertCost
            } else {
                (prefix.length - candidate.length) * deleteCost
            }
            if (minimumLengthCost > maxEdits) continue
            val distance = editDistance(prefix, candidate, previous, current)
            if (distance <= maxEdits) {
                result.add(FuzzyMatch(candidate, distance, frequency))
            }
        }
        result.sortWith(compareBy({ it.editDistance }, { -it.frequency }))
        return result
    }

    private fun editDistance(
        a: String,
        b: String,
        rowA: IntArray,
        rowB: IntArray
    ): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        if (m == 0) return n * insertCost
        if (n == 0) return m * deleteCost
        for (index in 0..n) rowA[index] = index * insertCost
        var prev = rowA
        var curr = rowB
        for (i in 1..m) {
            curr[0] = i * deleteCost
            val ac = a[i - 1]
            for (j in 1..n) {
                val sub = prev[j - 1] + substitutionCost.cost(ac, b[j - 1])
                val ins = curr[j - 1] + insertCost
                val del = prev[j] + deleteCost
                curr[j] = minOf(sub, ins, del)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
