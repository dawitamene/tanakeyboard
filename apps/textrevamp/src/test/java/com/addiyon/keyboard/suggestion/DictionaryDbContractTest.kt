package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.File
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Asset<->DB contract guard. The Gradle task `generateDictionaryDbs`
 * (`build-logic`'s language-dictionary convention) reads the `.dat` assets, applies
 * [EthiopicNormalizer.normalize] for Amharic / per-char lowercase for
 * English, and writes the folded form to a `key` column. The runtime
 * (SQLiteDictionary, SQLiteNgramModel) folds the lookup word the same way
 * and does `WHERE key = ?` / `WHERE key >= ? AND key < ?`. If those two
 * folds ever drift apart, the runtime silently misses the dictionary
 * without an exception -- so this test pins them.
 *
 * Skipped when the .db files haven't been built (a JVM test that doesn't
 * have Gradle having run `generateDictionaryDbs` first).
 */
class DictionaryDbContractTest {

    private fun dbFile(name: String): File? =
        listOf(
            "language/amharic/build/generated/dictionaryAssets/$name",
            "language/english/build/generated/dictionaryAssets/$name",
            "../language/amharic/build/generated/dictionaryAssets/$name",
            "../language/english/build/generated/dictionaryAssets/$name"
        )
            .map { File(it) }
            .firstOrNull { it.exists() }

    @Test
    fun amharicWordsKeyMatchesEthiopicNormalizer() {
        val db = dbFile("amharic.db")
        assumeTrue("amharic.db not built (run ./gradlew generateDictionaryDbs)", db != null)
        DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
            val expected = EthiopicNormalizer.normalize("ሃገር")
            val actual: String? = conn.prepareStatement(
                "SELECT key FROM words WHERE COALESCE(display, key) = ?"
            ).use { ps ->
                ps.setString(1, "ሃገር")
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            assertEquals(expected, actual)
            // Spot-check a known canonical word
            val nawKey: String? = conn.prepareStatement(
                "SELECT key FROM words WHERE COALESCE(display, key) = ?"
            ).use { ps ->
                ps.setString(1, "ነው")
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            assertEquals(EthiopicNormalizer.normalize("ነው"), nawKey)
            // Tokenizer junk from the source dump must have been filtered
            assertNull(loadKey(conn, "።"))
            assertNull(loadKey(conn, "፣"))
            assertNull(loadKey(conn, "0.002"))
        }
    }

    @Test
    fun englishWordsKeyIsLowercasedPerChar() {
        val db = dbFile("english.db")
        assumeTrue("english.db not built (run ./gradlew generateDictionaryDbs)", db != null)
        DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
            val englandKey = loadKey(conn, "England")
            assertEquals("england", englandKey)
            val theKey = loadKey(conn, "the")
            assertEquals("the", theKey)
            // Proper-noun casing must survive in the `word` column even
            // though the key is the lowercase form.
            val englandWord: String? = conn.prepareStatement(
                "SELECT COALESCE(display, key) FROM words WHERE key = 'england'"
            ).executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            assertNotNull(englandWord)
            assertTrue("expected England proper noun to be present, got $englandWord",
                englandWord == "England")
        }
    }

    @Test
    fun ngramVocabKeyMatchesFolds() {
        val db = dbFile("amharic.db")
        assumeTrue("amharic.db not built", db != null)
        DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
            assertEquals(0, scalar(conn, "SELECT count(*) FROM words WHERE ngram_id IS NOT NULL"))
        }
        val en = dbFile("english.db")
        assumeTrue("english.db not built", en != null)
        DriverManager.getConnection("jdbc:sqlite:${en!!.absolutePath}").use { conn ->
            val unitedKey = loadVocabKey(conn, "united")
            assertEquals("united", unitedKey)
        }
    }

    @Test
    fun topFrequentWordsFallbackIsFreqDescending() {
        listOf("amharic.db", "english.db").forEach { name ->
            val db = dbFile(name)
            assumeTrue("$name not built (run ./gradlew generateDictionaryDbs)", db != null)
            DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
                // Exact query SQLiteNgramModel.topFrequentWords runs for its
                // next-word fallback; the strip can request all 15 entries.
                val words = conn.createStatement().use { st ->
                        st.executeQuery(
                            "SELECT word, freq FROM prefix_top " +
                                "WHERE prefix = '' ORDER BY rank LIMIT 15"
                        )
                        .use { rs ->
                            buildList {
                                while (rs.next()) add(rs.getString(1) to rs.getLong(2))
                            }
                        }
                }
                assertEquals(15, words.size)
                assertTrue(
                    "expected strictly non-increasing freq, got ${words.map { it.second }}",
                    words.zipWithNext().all { (a, b) -> a.second >= b.second }
                )
                assertTrue(words.all { it.first.isNotEmpty() })
            }
        }
    }

    @Test
    fun compactSchemaKeepsEveryNgramReferenceResolvable() {
        listOf("amharic.db", "english.db").forEach { name ->
            val db = dbFile(name)
            assumeTrue("$name not built", db != null)
            DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
                val tables = conn.createStatement().executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                ).use { result ->
                    buildSet {
                        while (result.next()) add(result.getString(1))
                    }
                }
                assertEquals(
                    setOf(
                        "words",
                        "morph_lexemes",
                        "prefix_top",
                        "fuzzy_top",
                        "bigrams",
                        "trigrams"
                    ),
                    tables
                )
                assertFalse("vocab table should be folded into words", "vocab" in tables)
                assertEquals(0, scalar(conn, """
                    SELECT count(*)
                    FROM bigrams n
                    LEFT JOIN words context ON context.ngram_id = n.ctx
                    LEFT JOIN words successor ON successor.ngram_id = n.succ
                    WHERE context.key IS NULL OR successor.key IS NULL
                """.trimIndent()))
                assertEquals(0, scalar(conn, """
                    SELECT count(*)
                    FROM trigrams n
                    LEFT JOIN words previous2 ON previous2.ngram_id = (n.ctx >> 32)
                    LEFT JOIN words previous1 ON previous1.ngram_id = (n.ctx & 4294967295)
                    LEFT JOIN words successor ON successor.ngram_id = n.succ
                    WHERE previous2.key IS NULL OR previous1.key IS NULL OR successor.key IS NULL
                """.trimIndent()))
                assertEquals(15, scalar(
                    conn,
                    "SELECT count(*) FROM prefix_top WHERE prefix = ''"
                ))
            }
        }
    }

    @Test
    fun amharicStartsFromHornMorphoLexemesOnly() {
        val db = dbFile("amharic.db")
        assumeTrue("amharic.db not built", db != null)
        DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
            assertEquals(18_867, scalar(conn, "SELECT count(*) FROM morph_lexemes"))
            assertEquals(1_832, scalar(conn, "SELECT count(*) FROM morph_lexemes WHERE kind = 4"))
            assertEquals(18_251, scalar(conn, "SELECT count(*) FROM words"))
            assertEquals(
                17_035,
                scalar(conn, "SELECT count(*) FROM morph_lexemes WHERE key IS NOT NULL")
            )
            assertNotNull(loadKey(conn, "ሰው"))
            assertNull(loadKey(conn, "የሰው"))
            assertNull(loadKey(conn, "ሰውን"))
            assertEquals(0, scalar(conn, "SELECT count(*) FROM bigrams"))
            assertEquals(0, scalar(conn, "SELECT count(*) FROM trigrams"))
        }
    }

    private fun loadKey(conn: java.sql.Connection, word: String): String? =
        conn.prepareStatement(
            "SELECT key FROM words WHERE COALESCE(display, key) = ?"
        ).use { ps ->
            ps.setString(1, word)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun loadVocabKey(conn: java.sql.Connection, word: String): String? =
        conn.prepareStatement(
            "SELECT key FROM words " +
                "WHERE COALESCE(ngram_display, display, key) = ? AND ngram_id IS NOT NULL"
        ).use { ps ->
            ps.setString(1, word)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun scalar(conn: java.sql.Connection, sql: String): Int =
        conn.createStatement().executeQuery(sql).use { result ->
            assertTrue(result.next())
            result.getInt(1)
        }
}
