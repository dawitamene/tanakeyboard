package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.suggestion.AmharicNounMorphology
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.SQLiteMorphLexicon
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicSuggestionEngineIntegrationTest {
    @Test
    fun exactLexemeBeatsGeneratedCompletions() = withDatabase { connection ->
        val suggestions = complete(connection, "sew")

        assertEquals("ሰው", suggestions.first())
        assertTrue(suggestions.any { it != "ሰው" && it.startsWith("ሰው") })
    }

    @Test
    fun generatedAccusativeAppearsWithoutSurfaceDictionaryRows() = withDatabase { connection ->
        assertNull(word(connection, "ሰውን"))
        assertNull(word(connection, "የሰውን"))

        assertTrue("ሰውን" in complete(connection, "sewn"))
        assertTrue("የሰውን" in complete(connection, "yesewn"))
    }

    @Test
    fun contaminatedAlternatesRequireLexicalOrMorphologicalValidation() = withDatabase { connection ->
        assertFalse("ሌ" in complete(connection, "le"))
        assertFalse("ርዕ" in complete(connection, "rE"))
        assertTrue("ርዕስ" in complete(connection, "rEs"))
    }

    @Test
    fun duplicateNameAndPlaceAnalysesProduceOneChip() = withDatabase { connection ->
        val suggestions = complete(connection, "hana")

        assertEquals(1, suggestions.count { it == "ሀና" })
    }

    @Test
    fun unavailableDatabaseReturnsSafeLiteralBehavior() {
        val pipeline = AmharicSuggestionPipeline.prepare("sewn")
        val suggestions = AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = 15,
            frequencyOf = { null },
            completionsForPrefix = { _, _ -> emptyList() },
        )

        assertEquals(listOf(pipeline.readings.first()), suggestions)
    }

    @Test
    fun morphologyQueryUsesTheIndexedKeyPlan() = withDatabase { connection ->
        val morphology = AmharicNounMorphology.query("የሰውን")
        val query = requireNotNull(
            SQLiteMorphLexicon.nounQuery(
                morphology.exactStemSurfaces,
                morphology.completionStemPrefix,
                48,
                EthiopicNormalizer::normalize,
            )
        )
        val details = connection.prepareStatement("EXPLAIN QUERY PLAN ${query.sql}").use { statement ->
            query.args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(4))
                }
            }
        }

        assertTrue(details.any { it.contains("idx_morph_lexemes_key") || it.contains("PRIMARY KEY") })
        assertFalse(details.any { it == "SCAN m" })
    }

    private fun complete(connection: Connection, raw: String): List<String> {
        val pipeline = AmharicSuggestionPipeline.prepare(raw)
        val frequencies = pipeline.readings.associateWith { reading ->
            connection.prepareStatement("SELECT freq FROM words WHERE key = ?").use { statement ->
                statement.setString(1, EthiopicNormalizer.normalize(reading))
                statement.executeQuery().use { result -> if (result.next()) result.getInt(1) else null }
            }
        }
        val completions = HashMap<String, List<CandidateRanker.DictionaryWord>>()
        return AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = 15,
            frequencyOf = frequencies::get,
            completionsForPrefix = { prefix, limit ->
                completions.getOrPut(prefix) {
                    val direct = directCompletions(connection, prefix, limit)
                    val query = AmharicNounMorphology.query(prefix)
                    val lexemes = nounLexemes(connection, query, 48)
                    direct + AmharicNounMorphology.complete(prefix, lexemes, limit, direct)
                }
            },
        )
    }

    private fun directCompletions(
        connection: Connection,
        prefix: String,
        limit: Int,
    ): List<CandidateRanker.DictionaryWord> {
        val key = EthiopicNormalizer.normalize(prefix)
        val upper = prefixEndBound(key)
        return connection.prepareStatement(
            "SELECT COALESCE(display, key), freq FROM words " +
                "WHERE key >= ? AND key < ? ORDER BY freq DESC, key LIMIT ?"
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, upper)
            statement.setInt(3, limit)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(CandidateRanker.DictionaryWord(result.getString(1), result.getInt(2)))
                    }
                }
            }
        }
    }

    private fun nounLexemes(
        connection: Connection,
        morphology: AmharicNounMorphology.Query,
        limit: Int,
    ): List<AmharicNounMorphology.Lexeme> {
        val query = SQLiteMorphLexicon.nounQuery(
            morphology.exactStemSurfaces,
            morphology.completionStemPrefix,
            limit,
            EthiopicNormalizer::normalize,
        ) ?: return emptyList()
        return connection.prepareStatement(query.sql).use { statement ->
            query.args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            AmharicNounMorphology.Lexeme(
                                kind = result.getInt(1),
                                surface = SQLiteMorphLexicon.cleanSurface(result.getString(2)),
                                features = result.getString(3),
                                frequency = result.getInt(4),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun word(connection: Connection, surface: String): String? =
        connection.prepareStatement("SELECT COALESCE(display, key) FROM words WHERE key = ?").use { statement ->
            statement.setString(1, EthiopicNormalizer.normalize(surface))
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }

    private fun prefixEndBound(prefix: String): String {
        val codePoint = prefix.codePointBefore(prefix.length)
        val start = prefix.length - Character.charCount(codePoint)
        return prefix.substring(0, start) + String(Character.toChars(codePoint + 1))
    }

    private fun withDatabase(block: (Connection) -> Unit) {
        val database = listOf(
            File("build/generated/dictionaryAssets/amharic.db"),
            File("language/amharic/build/generated/dictionaryAssets/amharic.db"),
        ).first { it.exists() }
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use(block)
    }
}
