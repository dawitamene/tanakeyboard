package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

/**
 * SQLite-backed word-frequency dictionary. Replaces the in-memory
 * [WordTrie] (and its [WordDictionary] wrapper) -- same public surface so
 * the suggestion pipeline doesn't change. Keys are folded at build time and
 * stored in a `key` column; per-call [suggestionEntries] / [frequencyOf] /
 * [isWord] fold the input the same way and run a small indexed SQL query.
 *
 * Only the page-cache footprint lives in resident RAM; corpus size has no
 * direct effect on the heap -- the bulk data stays in the .db file and
 * is read on demand.
 *
 * [fuzzySuggestions] is bounded by a short LIKE prefix (first 1-2 chars)
 * to keep the candidate set small, then runs in-Kotlin two-row Levenshtein
 * over that set. The Amharic caller injects a fidel-aware substitution
 * cost; English uses the uniform default.
 */
internal class SQLiteDictionary(
    private val store: SQLiteLanguageStore,
    private val precomputedPrefixLength: Int,
    /** Whole-string fold matching the build-time `key` column for [assetName].
     *  Default: lowercase (English). Amharic dictionary injects
     *  [com.addiyon.keyboard.transliteration.EthiopicNormalizer.normalize]. */
    private val normalize: (String) -> String = { it.lowercase() },
) {
    val isReady: Boolean
        get() = store.isReady

    val isLoading: Boolean
        get() = store.isLoading

    private val cache = SuggestionCache<List<Suggestion>>(
        capacity = if (store.isLowRam) 64 else 256
    )

    /**
     * Kicks off the one-time load (or a no-op if already loaded). [onReady]
     * runs on the main thread once the DB is open. Safe to call more than
     * once -- only the first call does work. Empty / not-an-error before
     * [isReady].
     */
    fun loadAsync(onReady: () -> Unit) {
        store.loadAsync(onReady)
    }

    fun release() {
        store.release()
        cache.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    fun suggestions(prefix: String, limit: Int = 3): List<String> =
        suggestionEntries(prefix, limit).map { it.word }

    fun suggestionEntries(prefix: String, limit: Int = 3): List<Suggestion> {
        if (!isReady || limit <= 0) return emptyList()
        val cacheKey = "$limit\u0001$prefix"
        cache.get(cacheKey)?.let { return it }
        val key = normalize(prefix)
        if (key.isEmpty()) return emptyList()
        val upperBound = prefixEndBound(key)
        val database = store.databaseOrNull() ?: return emptyList()
        val result = ArrayList<Suggestion>(limit)
        try {
            val shortPrefix = key.length <= precomputedPrefixLength
            val sql = if (shortPrefix) {
                "SELECT word, freq FROM prefix_top WHERE prefix = ? ORDER BY rank LIMIT ?"
            } else {
                "SELECT word, freq FROM words WHERE key >= ? AND key < ? ORDER BY freq DESC LIMIT ?"
            }
            val args = if (shortPrefix) {
                arrayOf(key, limit.toString())
            } else {
                arrayOf(key, upperBound, limit.toString())
            }
            database.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(Suggestion(cursor.getString(0), cursor.getInt(1)))
                }
            }
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(t, "SQLiteDictionary.suggestionEntries")
            return emptyList()
        }
        cache.put(cacheKey, result)
        return result
    }

    fun frequencyOf(word: String): Int? {
        if (!isReady) return null
        val key = normalize(word)
        if (key.isEmpty()) return null
        val database = store.databaseOrNull() ?: return null
        return try {
            database.rawQuery(
                "SELECT freq FROM words WHERE key = ? LIMIT 1",
                arrayOf(key)
            ).use { cursor ->
                if (cursor.moveToNext()) {
                    val f = cursor.getInt(0)
                    if (f <= 0) null else f
                } else null
            }
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(t, "SQLiteDictionary.frequencyOf")
            null
        }
    }

    fun frequenciesOf(words: Collection<String>): Map<String, Int> {
        if (!isReady || words.isEmpty()) return emptyMap()
        val normalizedByWord = LinkedHashMap<String, String>(words.size)
        for (word in words) {
            val key = normalize(word)
            if (key.isNotEmpty()) normalizedByWord[word] = key
        }
        if (normalizedByWord.isEmpty()) return emptyMap()
        val keys = normalizedByWord.values.distinct()
        val placeholders = keys.joinToString(",") { "?" }
        val byKey = HashMap<String, Int>(keys.size)
        val database = store.databaseOrNull() ?: return emptyMap()
        try {
            database.rawQuery(
                "SELECT key, MAX(freq) FROM words WHERE key IN ($placeholders) GROUP BY key",
                keys.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    byKey[cursor.getString(0)] = cursor.getInt(1)
                }
            }
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(t, "SQLiteDictionary.frequenciesOf")
            return emptyMap()
        }
        return buildMap {
            for ((word, key) in normalizedByWord) {
                byKey[key]?.let { put(word, it) }
            }
        }
    }

    fun isWord(word: String): Boolean = frequencyOf(word) != null

    /**
     * Bounded fuzzy / typo-tolerant completions of [prefix]: small candidate
     * set from a short LIKE prefix, then in-Kotlin two-row Levenshtein. The
     * Amharic caller injects a script-aware [substitutionCost]; English uses
     * the uniform default.
     */
    fun fuzzySuggestions(
        prefix: String,
        maxEdits: Int,
        limit: Int = 3,
        substitutionCost: SubstitutionCost = SubstitutionCost { a, b -> if (a == b) 0 else 1 },
        insertCost: Int = 1,
        deleteCost: Int = 1,
    ): List<FuzzyMatch> {
        if (!isReady || maxEdits <= 0 || limit <= 0) return emptyList()
        val key = normalize(prefix)
        if (key.isEmpty()) return emptyList()
        val window = key.take(2)
        val db = store.databaseOrNull() ?: return emptyList()
        val upperBound = prefixEndBound(window)
        val candidateLimit = if (store.isLowRam) 192 else 512
        val candidates = ArrayList<Pair<String, Int>>(candidateLimit.coerceAtMost(128))
        val lower = window
        try {
            db.rawQuery(
                "SELECT word, freq FROM words WHERE key >= ? AND key < ? LIMIT ?",
                arrayOf(lower, upperBound, candidateLimit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    candidates.add(cursor.getString(0) to cursor.getInt(1))
                }
            }
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(t, "SQLiteDictionary.fuzzySuggestions")
            return emptyList()
        }
        val matcher = FuzzyMatcher(maxEdits, insertCost, deleteCost, substitutionCost)
        val matches = matcher.rank(prefix, candidates)
        return matches.take(limit)
    }

    /**
     * Computes the exclusive upper bound for an open-ended prefix range scan:
     * `key < prefixEndBound(prefix)` matches all keys with the given prefix
     * under UTF-16 code-unit order. Appends `\uFFFF` to the prefix so the
     * resulting bound is strictly greater than every key that *starts* with
     * the prefix but less than any key that exceeds the prefix at the last
     * code unit.
     */
    private fun prefixEndBound(prefix: String): String = prefix + "\uFFFF"
}
