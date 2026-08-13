package com.addiyon.keyboard.suggestion

class SQLiteMorphLexicon(
    private val store: SQLiteLanguageStore,
    private val normalize: (String) -> String,
) {
    @Volatile
    private var surfaceStatistics: Map<String, Int>? = null

    data class NounQuery(val sql: String, val args: List<String>)

    data class Lexeme(
        val lexemeId: Long,
        val kind: Int,
        val surface: String,
        val features: String,
        val morphBits: Long,
        val stemClass: Int,
        val frequency: Int,
    )

    fun nounEntries(
        exactSurfaces: Collection<String>,
        completionPrefix: String,
        limit: Int,
    ): List<Lexeme> {
        if (!store.isReady || limit <= 0) return emptyList()
        val query = nounQuery(exactSurfaces, completionPrefix, limit, normalize) ?: return emptyList()
        val database = store.databaseOrNull() ?: return emptyList()
        val result = ArrayList<Lexeme>(limit)
        return try {
            database.rawQuery(query.sql, query.args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    result += Lexeme(
                        lexemeId = cursor.getLong(0),
                        kind = cursor.getInt(1),
                        surface = cleanSurface(cursor.getString(2)),
                        features = cursor.getString(3),
                        morphBits = cursor.getLong(4),
                        stemClass = cursor.getInt(5),
                        frequency = cursor.getInt(6),
                    )
                }
            }
            result
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            store.reportFailure(t, "SQLiteMorphLexicon.nounEntries")
            emptyList()
        }
    }

    fun surfaceFrequencies(keys: Collection<String>): Map<String, Int> {
        if (!store.isReady || keys.isEmpty()) return emptyMap()
        val normalizedKeys = keys.asSequence().map(normalize).filter(String::isNotEmpty).distinct().toList()
        if (normalizedKeys.isEmpty()) return emptyMap()
        val statistics = surfaceStatistics ?: loadSurfaceStatistics() ?: return emptyMap()
        return buildMap {
            normalizedKeys.forEach { key -> statistics[key]?.let { put(key, it) } }
        }
    }

    fun clearCache() {
        surfaceStatistics = null
    }

    private fun loadSurfaceStatistics(): Map<String, Int>? = synchronized(this) {
        surfaceStatistics?.let { return@synchronized it }
        val database = store.databaseOrNull() ?: return emptyMap()
        try {
            database.rawQuery(
                "SELECT key, frequency FROM morph_surface_stats",
                emptyArray(),
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
                }
            }.also { surfaceStatistics = it }
        } catch (t: Throwable) {
            store.handleQueryFailure(t)
            store.reportFailure(t, "SQLiteMorphLexicon.surfaceFrequencies")
            null
        }
    }

    companion object {
        private const val PRODUCTIVE_NOMINAL_BIT = 1L

        fun nounQuery(
            exactSurfaces: Collection<String>,
            completionPrefix: String,
            limit: Int,
            normalize: (String) -> String,
        ): NounQuery? {
            if (limit <= 0) return null
            val exactKeys = exactSurfaces.asSequence()
                .map(normalize)
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
            val prefix = normalize(completionPrefix)
            val conditions = ArrayList<String>(2)
            val args = ArrayList<String>()
            val exactCondition = if (exactKeys.isEmpty()) {
                null
            } else {
                "m.key IN (${exactKeys.joinToString(",") { "?" }})"
            }
            if (exactCondition != null) {
                conditions += exactCondition
                args.addAll(exactKeys)
            }
            if (prefix.isEmpty()) {
                if (exactKeys.isEmpty()) return null
            } else {
                conditions += "m.key >= ? AND m.key < ?"
                args += prefix
                args += prefixEndBound(prefix)
            }
            val exactOrder = if (exactCondition == null) "1" else exactCondition
            if (exactCondition != null) args.addAll(exactKeys)
            args += limit.toString()
            val sql = """
                SELECT m.lexeme_id, m.kind, m.form, m.features,
                       m.morph_bits, m.stem_class, COALESCE(w.freq, 1)
                FROM morph_lexemes m
                LEFT JOIN words w ON w.key = m.key
                WHERE m.key IS NOT NULL
                  AND (m.morph_bits & $PRODUCTIVE_NOMINAL_BIT) != 0
                  AND (${conditions.joinToString(" OR ")})
                ORDER BY CASE WHEN $exactOrder THEN 0 ELSE 1 END,
                         COALESCE(w.freq, 1) DESC,
                         m.form ASC
                LIMIT ?
                """.trimIndent()
            return NounQuery(sql, args)
        }

        fun cleanSurface(form: String): String = buildString(form.length) {
            for (character in form) {
                if (character != '/' && character !in '\u135D'..'\u135F') append(character)
            }
        }

        private fun prefixEndBound(prefix: String): String {
            var end = prefix.length
            while (end > 0) {
                val codePoint = prefix.codePointBefore(end)
                val start = end - Character.charCount(codePoint)
                if (codePoint < Character.MAX_CODE_POINT) {
                    return prefix.substring(0, start) + String(Character.toChars(codePoint + 1))
                }
                end = start
            }
            error("Cannot compute an upper bound for an empty prefix")
        }
    }
}
