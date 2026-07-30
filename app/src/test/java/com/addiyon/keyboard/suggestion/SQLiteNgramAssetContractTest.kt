package com.addiyon.keyboard.suggestion

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SQLiteNgramAssetContractTest {
    @Test
    fun ngramLookupsUseCoveringIndexesInBothAssets() {
        listOf("english.db", "amharic.db").forEach { name ->
            withDatabase(name) { connection ->
                assertPlanUses(connection, "SELECT id FROM vocab WHERE key = ? LIMIT 1", "idx_vocab_key")
                assertPlanUses(
                    connection,
                    "SELECT succ FROM bigrams WHERE ctx = ? ORDER BY weight DESC LIMIT 3",
                    "idx_bigrams_ctx"
                )
                assertPlanUses(
                    connection,
                    "SELECT succ FROM trigrams WHERE ctx = ? ORDER BY weight DESC LIMIT 3",
                    "idx_trigrams_ctx"
                )
            }
        }
    }

    @Test
    fun englishTrigramResultsPrecedeDeduplicatedBigramBackoff() {
        withDatabase("english.db") { connection ->
            val a = wordId(connection, "a")
            val good = wordId(connection, "good")
            val trigram = successors(
                connection,
                table = "trigrams",
                context = (a.toLong() shl 32) or good.toLong(),
                limit = 3
            )
            val bigram = successors(
                connection,
                table = "bigrams",
                context = good.toLong(),
                limit = 8
            )
            val merged = (trigram + bigram).distinct().take(5)

            assertEquals(listOf("deal", "idea"), trigram)
            assertEquals(5, merged.size)
            assertEquals(trigram, merged.take(trigram.size))
            assertEquals(merged.size, merged.distinct().size)
        }
    }

    @Test
    fun ngramQueriesRespectLimitsAndStoredCasingFlags() {
        withDatabase("english.db") { connection ->
            val context = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT ctx FROM bigrams WHERE casing <> 0 ORDER BY ctx LIMIT 1"
                ).use { result ->
                    assertTrue(result.next())
                    result.getLong(1)
                }
            }
            val rows = connection.prepareStatement(
                """
                SELECT v.text, b.casing
                FROM bigrams b
                JOIN vocab v ON v.id = b.succ
                WHERE b.ctx = ?
                ORDER BY b.weight DESC
                LIMIT ?
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, context)
                statement.setInt(2, 3)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.getString(1) to result.getInt(2))
                    }
                }
            }

            assertTrue(rows.size <= 3)
            assertTrue(rows.all { it.second in 0..2 })
        }
    }

    private fun assertPlanUses(connection: Connection, sql: String, index: String) {
        val plan = connection.prepareStatement("EXPLAIN QUERY PLAN $sql").use { statement ->
            statement.setString(1, "1")
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(4))
                }.joinToString("\n")
            }
        }
        assertTrue("$sql should use $index but was $plan", plan.contains(index))
    }

    private fun wordId(connection: Connection, key: String): Int =
        connection.prepareStatement("SELECT id FROM vocab WHERE key = ? LIMIT 1").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { result ->
                assertTrue(result.next())
                result.getInt(1)
            }
        }

    private fun successors(
        connection: Connection,
        table: String,
        context: Long,
        limit: Int
    ): List<String> {
        require(table == "bigrams" || table == "trigrams")
        return connection.prepareStatement(
            """
            SELECT v.text
            FROM $table n
            JOIN vocab v ON v.id = n.succ
            WHERE n.ctx = ?
            ORDER BY n.weight DESC
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, context)
            statement.setInt(2, limit)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(1))
                }
            }
        }
    }

    private fun withDatabase(name: String, block: (Connection) -> Unit) {
        val database = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        ).firstOrNull(File::isFile)
        assumeTrue("$name not built", database != null)
        DriverManager.getConnection("jdbc:sqlite:${database!!.absolutePath}").use(block)
    }
}
