package com.addiyon.keyboard.suggestion


/**
 * SQLite-backed n-gram next-word model. Same public surface as
 * [NgramDictionary]: [predict] runs a trigram SQL lookup, then a bigram SQL
 * backoff, both indexed. Vocab is held in SQL too, so the whole model is
 * page-cached rather than in-memory arrays. The same
 * [com.addiyon.keyboard.transliteration.EthiopicNormalizer]-style fold
 * injected at runtime MUST match the build-time `key` column -- the build
 * task sorts the vocab by it.
 */
internal class SQLiteNgramModel(
    private val store: SQLiteLanguageStore,
    private val normalize: (String) -> String,
) {
    data class Prediction(val word: String, val weight: Int)

    @Volatile
    private var topWordsCache: List<Prediction>? = null

    val isReady: Boolean
        get() = store.isReady

    val isLoading: Boolean
        get() = store.isLoading

    fun loadAsync(onReady: () -> Unit) {
        store.loadAsync(onReady)
    }

    fun release() {
        store.release()
    }

    /**
     * The most frequent [limit] dictionary words by corpus frequency, used as
     * the next-word fallback when the n-gram model has no successor for the
     * current context. The data is static for a given asset (content-addressed
     * install), so the query runs once per model and is cached; callers may
     * ask for fewer than the cached count.
     */
    fun topFrequentWords(limit: Int): List<Prediction> {
        if (!isReady || limit <= 0) return emptyList()
        return try {
            val cache = topWordsCache ?: synchronized(this) {
                topWordsCache ?: fetchTopFrequentWords(TOP_FREQUENT_WORD_COUNT).also {
                    topWordsCache = it
                }
            }
            cache.take(limit)
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(
                t,
                "SQLiteNgramModel.topFrequentWords",
                com.addiyon.keyboard.telemetry.NonFatalCategory.DATABASE
            )
            emptyList()
        }
    }

    fun predict(prev2: String?, prev1: String, limit: Int): List<Prediction> {
        if (!isReady || limit <= 0) return emptyList()
        return try {
            val id1 = wordId(prev1) ?: return emptyList()
            val result = ArrayList<Prediction>(limit)
            val seen = HashSet<Int>()
            if (prev2 != null) {
                val id2 = wordId(prev2)
                if (id2 != null) {
                    val ctx = (id2.toLong() shl 32) or id1.toLong()
                    appendSuccessorsFromTrigram(ctx, result, seen, limit)
                }
            }
            if (result.size < limit) {
                appendSuccessorsFromBigram(id1, result, seen, limit)
            }
            result
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            com.addiyon.keyboard.SafeLog.e(
                t,
                "SQLiteNgramModel.predict",
                com.addiyon.keyboard.telemetry.NonFatalCategory.DATABASE
            )
            emptyList()
        }
    }

    private fun wordId(word: String): Int? {
        if (word.isEmpty()) return null
        val key = normalize(word)
        val db = store.databaseOrNull() ?: return null
        return db.rawQuery("SELECT id FROM vocab WHERE key = ? LIMIT 1", arrayOf(key)).use { cursor ->
            if (cursor.moveToNext()) cursor.getInt(0) else null
        }
    }

    private fun appendSuccessorsFromTrigram(
        ctx: Long,
        result: ArrayList<Prediction>,
        seen: HashSet<Int>,
        limit: Int,
    ) {
        val db = store.databaseOrNull() ?: return
        db.rawQuery(
            """
            SELECT t.succ, t.weight, v.text
            FROM trigrams t
            JOIN vocab v ON v.id = t.succ
            WHERE t.ctx = ?
            ORDER BY t.weight DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(ctx.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getInt(0)
                if (seen.add(id)) {
                    val word = cursor.getString(2)
                    result.add(Prediction(word, cursor.getInt(1) and 0xFF))
                }
            }
        }
    }

    private fun appendSuccessorsFromBigram(
        ctx: Int,
        result: ArrayList<Prediction>,
        seen: HashSet<Int>,
        limit: Int,
    ) {
        val db = store.databaseOrNull() ?: return
        db.rawQuery(
            """
            SELECT b.succ, b.weight, b.casing, v.text
            FROM bigrams b
            JOIN vocab v ON v.id = b.succ
            WHERE b.ctx = ?
            ORDER BY b.weight DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(ctx.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getInt(0)
                if (seen.add(id)) {
                    val base = cursor.getString(3)
                    val word = applyCasing(base, cursor.getInt(2))
                    result.add(Prediction(word, cursor.getInt(1) and 0xFF))
                }
            }
        }
    }

    private fun applyCasing(word: String, flag: Int): String = when (flag) {
        1 -> word.replaceFirstChar { it.uppercaseChar() }
        2 -> word.uppercase()
        else -> word
    }

    private fun fetchTopFrequentWords(count: Int): List<Prediction> {
        val db = store.databaseOrNull() ?: return emptyList()
        return db.rawQuery(
            "SELECT word, freq FROM words ORDER BY freq DESC LIMIT ?",
            arrayOf(count.toString())
        ).use { cursor ->
            ArrayList<Prediction>(count).apply {
                while (cursor.moveToNext()) {
                    add(Prediction(cursor.getString(0), cursor.getLong(1).coerceIn(0, 255).toInt()))
                }
            }
        }
    }

    companion object {
        private const val TOP_FREQUENT_WORD_COUNT = 10
    }
}
