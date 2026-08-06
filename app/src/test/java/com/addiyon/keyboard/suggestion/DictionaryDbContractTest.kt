package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * Asset<->DB contract guard. The Gradle task `generateDictionaryDbs`
 * (buildSrc/DictionaryDbGenerator.kt) reads the `.dat` assets, applies
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
        listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.exists() }

    @Test
    fun amharicWordsKeyMatchesEthiopicNormalizer() {
        val db = dbFile("amharic.db")
        assumeTrue("amharic.db not built (run ./gradlew generateDictionaryDbs)", db != null)
        DriverManager.getConnection("jdbc:sqlite:${db!!.absolutePath}").use { conn ->
            val expected = EthiopicNormalizer.normalize("ሀገር")
            val actual: String? = conn.prepareStatement(
                "SELECT key FROM words WHERE word = ?"
            ).use { ps ->
                ps.setString(1, "ሀገር")
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            assertEquals(expected, actual)
            // Spot-check a known canonical word
            val nawKey: String? = conn.prepareStatement(
                "SELECT key FROM words WHERE word = ?"
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
                "SELECT word FROM words WHERE key = 'england'"
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
            val hayelKey = loadVocabKey(conn, "ኃይል")
            assertEquals(EthiopicNormalizer.normalize("ኃይል"), hayelKey)
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
                // next-word fallback; the strip needs 10 (Amharic) at most.
                val words = conn.createStatement().use { st ->
                    st.executeQuery("SELECT word, freq FROM words ORDER BY freq DESC LIMIT 10")
                        .use { rs ->
                            buildList {
                                while (rs.next()) add(rs.getString(1) to rs.getLong(2))
                            }
                        }
                }
                assertEquals(10, words.size)
                assertTrue(
                    "expected strictly non-increasing freq, got ${words.map { it.second }}",
                    words.zipWithNext().all { (a, b) -> a.second >= b.second }
                )
                assertTrue(words.all { it.first.isNotEmpty() })
            }
        }
    }

    private fun loadKey(conn: java.sql.Connection, word: String): String? =
        conn.prepareStatement("SELECT key FROM words WHERE word = ?").use { ps ->
            ps.setString(1, word)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun loadVocabKey(conn: java.sql.Connection, word: String): String? =
        conn.prepareStatement("SELECT key FROM vocab WHERE text = ?").use { ps ->
            ps.setString(1, word)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
}
