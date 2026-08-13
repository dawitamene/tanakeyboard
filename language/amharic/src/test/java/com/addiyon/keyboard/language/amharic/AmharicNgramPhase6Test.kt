package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicNgramPhase6Test {
    @Test
    fun everyPredictionSurfaceMatchesTheAnalyzerAudit() = withDatabase { connection ->
        assertEquals(40, scalar(connection, "SELECT count(*) FROM ngram_vocab"))
        val auditKeys = phase6File("amharic_ngram_audit.tsv").readLines()
            .drop(1)
            .mapTo(linkedSetOf()) { it.split('\t')[1] }
        val vocabKeys = connection.createStatement().executeQuery(
            "SELECT key FROM ngram_vocab ORDER BY key"
        ).use { result ->
            buildSet {
                while (result.next()) add(result.getString(1))
            }
        }
        assertEquals(auditKeys, vocabKeys)
        assertEquals(18_251, scalar(connection, "SELECT count(*) FROM words"))
        assertNull(dictionaryWord(connection, "የሰው"))
        assertEquals("የሰው", ngramDisplay(connection, "የሰው"))
    }

    @Test
    fun inflectedSurfacesRemainPredictionOnlyAndAreRankedFromContext() = withDatabase { connection ->
        assertEquals(
            listOf("መምህራን", "ሰዎች", "ቤቶች"),
            successors(connection, "ብዙ", 3),
        )
        assertEquals("በቤት", successors(connection, "እሷ", 3).first())
        assertNull(dictionaryWord(connection, "ቤቶች"))
        assertNull(dictionaryWord(connection, "መምህራን"))
    }

    @Test
    fun unknownAndPunctuationTokensBreakAdjacencyWithoutLeakingGarbage() = withDatabase { connection ->
        assertFalse("መኪና" in successors(connection, "ሰው", 10))
        assertNull(ngramDisplay(connection, "ሃሃሃ"))
        assertNull(ngramDisplay(connection, "የነው"))
        assertNull(ngramDisplay(connection, "መኪና"))
    }

    @Test
    fun homoglyphFoldingPoolsEvidenceButPreservesCanonicalDisplay() = withDatabase { connection ->
        assertEquals("ሃገር", ngramDisplay(connection, "ሐገር"))
        assertEquals(listOf("ትልቅ", "ነው"), successors(connection, "ሐገር", 3))
    }

    @Test
    fun modelIsSparseBigramOnlyAndHeldOutTop3BeatsEmptyBaseline() = withDatabase { connection ->
        assertEquals(50, scalar(connection, "SELECT count(*) FROM bigrams"))
        assertEquals(0, scalar(connection, "SELECT count(*) FROM trigrams"))

        val quality = phase6File("amharic_ngram_quality.json").readText()
        assertEquals(
            "{\"baseline_top3_hits\":0,\"evaluated_transitions\":14," +
                "\"model_top3_accuracy\":0.785714,\"model_top3_hits\":11}\n",
            quality,
        )
    }

    @Test
    fun auditRetainsValidityAnalysisFrequencyAndAmbiguityMetadata() {
        val rows = phase6File("amharic_ngram_audit.tsv").readLines()
        assertEquals(
            "display\tnormalized_key\tvalidity_source\tpreferred_lemma\t" +
                "preferred_analysis\tambiguity_count\tsurface_frequency",
            rows.first(),
        )
        assertEquals(41, rows.size)
        assertTrue(rows.any { it.startsWith("ቤቶች\tቤቶች\toracle_surface\tቤት\tkind=0;") && it.endsWith("\t1\t17206") })
        assertTrue(rows.any { it == "ክቡር\tክቡር\texact_lexeme\t\t\t2\t0" })
        assertFalse(rows.any { it.startsWith("የነው\t") || it.startsWith("ሃሃሃ\t") })
    }

    @Test
    fun fluentReviewArtifactContainsEverySuccessorAndNoSentinel() {
        val rows = phase6File("amharic_ngram_review.tsv").readLines()

        assertEquals("order\tcontext\trank\tsuccessor\tweight", rows.first())
        assertEquals(51, rows.size)
        assertEquals(50, rows.drop(1).count { it.startsWith("bigram\t") })
        assertFalse(rows.any { "የነው" in it || "ሃሃሃ" in it })
    }

    @Test
    fun predictionQueriesUseIndexesAndStayWithinWarmLatencyBudget() = withDatabase { connection ->
        val lookupPlan = queryPlan(
            connection,
            "SELECT id FROM ngram_vocab WHERE key = ? LIMIT 1",
            EthiopicNormalizer.normalize("ብዙ"),
        )
        val successorPlan = queryPlan(
            connection,
            "SELECT succ FROM bigrams WHERE ctx = ? ORDER BY weight DESC LIMIT 3",
            ngramId(connection, "ብዙ").toString(),
        )
        assertTrue(lookupPlan.any { it.contains("idx_ngram_vocab_key") })
        assertTrue(successorPlan.any { it.contains("PRIMARY KEY") })

        repeat(10) { successors(connection, "ብዙ", 3) }
        val samples = (0 until 100).map {
            val start = System.nanoTime()
            successors(connection, "ብዙ", 3)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val p95 = samples[94]

        println("PHASE6_METRIC ngram_prediction_p95_ms=$p95 max_ms=${samples.last()}")
        assertTrue("warm prediction p95=${p95}ms", p95 <= 15.0)
    }

    private fun successors(connection: Connection, context: String, limit: Int): List<String> {
        val contextId = ngramId(connection, context)
        if (contextId < 0) return emptyList()
        return connection.prepareStatement(
            """
            SELECT v.display
            FROM bigrams b
            JOIN ngram_vocab v ON v.id = b.succ
            WHERE b.ctx = ?
            ORDER BY b.weight DESC, b.succ ASC, b.casing ASC
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            statement.setInt(1, contextId)
            statement.setInt(2, limit)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(1))
                }
            }
        }
    }

    private fun ngramId(connection: Connection, word: String): Int =
        connection.prepareStatement("SELECT id FROM ngram_vocab WHERE key = ? LIMIT 1").use { statement ->
            statement.setString(1, EthiopicNormalizer.normalize(word))
            statement.executeQuery().use { result -> if (result.next()) result.getInt(1) else -1 }
        }

    private fun ngramDisplay(connection: Connection, word: String): String? =
        connection.prepareStatement("SELECT display FROM ngram_vocab WHERE key = ?").use { statement ->
            statement.setString(1, EthiopicNormalizer.normalize(word))
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }

    private fun dictionaryWord(connection: Connection, word: String): String? =
        connection.prepareStatement("SELECT COALESCE(display, key) FROM words WHERE key = ?").use { statement ->
            statement.setString(1, EthiopicNormalizer.normalize(word))
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }

    private fun queryPlan(connection: Connection, sql: String, argument: String): List<String> =
        connection.prepareStatement("EXPLAIN QUERY PLAN $sql").use { statement ->
            statement.setString(1, argument)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(4))
                }
            }
        }

    private fun scalar(connection: Connection, sql: String): Int =
        connection.createStatement().executeQuery(sql).use { result ->
            assertTrue(result.next())
            result.getInt(1)
        }

    private fun phase6File(name: String): File = listOf(
        File("src/dictionary/$name"),
        File("language/amharic/src/dictionary/$name"),
    ).first(File::isFile)

    private fun withDatabase(block: (Connection) -> Unit) {
        val database = listOf(
            File("build/generated/dictionaryAssets/amharic.db"),
            File("language/amharic/build/generated/dictionaryAssets/amharic.db"),
        ).first(File::isFile)
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use(block)
    }
}
