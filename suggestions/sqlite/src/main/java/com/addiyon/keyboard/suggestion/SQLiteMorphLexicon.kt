package com.addiyon.keyboard.suggestion

class SQLiteMorphLexicon(
    private val store: SQLiteLanguageStore,
    private val normalize: (String) -> String,
) {
    data class NounQuery(val sql: String, val args: List<String>)

    data class Lexeme(
        val kind: Int,
        val surface: String,
        val features: String,
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
                        kind = cursor.getInt(0),
                        surface = cleanSurface(cursor.getString(1)),
                        features = cursor.getString(2),
                        frequency = cursor.getInt(3),
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

    companion object {
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
                SELECT m.kind, m.form, m.features, COALESCE(w.freq, 1)
                FROM morph_lexemes m
                LEFT JOIN words w ON w.key = m.key
                WHERE m.kind IN (0, 1, 2, 3)
                  AND m.key IS NOT NULL
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
