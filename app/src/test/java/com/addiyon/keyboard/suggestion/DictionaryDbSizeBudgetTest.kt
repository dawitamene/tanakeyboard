package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sanity guard for the SQLite-backed runtime. The .db files are rebuilt by
 * the Gradle task `generateDictionaryDbs` (Track 1 of
 * `plans/sqlite-dictionaries.md`) and bundled into the APK. The in-RAM trie
 * approach we replaced lived at ~30 MB per language (out of ~80 MB total
 * resident on a 1 GB device); the SQLite approach keeps only the page-cache
 * footprint live. This test pins the on-disk size of the built DBs so a
 * regression that re-introduces eager loading or bloat is caught at
 * build time.
 *
 * The thresholds are loose -- they're checking that we didn't accidentally
 * ship 50 MB of dictionaries per language, not microbenchmarking the
 * SQLite build.
 */
class DictionaryDbSizeBudgetTest {

    @Test
    fun amharicDbIsReasonablyBounded() {
        val db = dbFile("amharic.db") ?: run {
            assumeTrue("amharic.db not built", false); return
        }
        val sizeKb = db.length() / 1024
        assertTrue(
            "amharic.db is unexpectedly large: ${sizeKb}KB",
            sizeKb < 40_000
        )
    }

    @Test
    fun englishDbIsReasonablyBounded() {
        val db = dbFile("english.db") ?: run {
            assumeTrue("english.db not built", false); return
        }
        val sizeKb = db.length() / 1024
        assertTrue(
            "english.db is unexpectedly large: ${sizeKb}KB",
            sizeKb < 25_000
        )
    }

    private fun dbFile(name: String): File? =
        listOf("src/main/assets/$name", "app/src/main/assets/$name")
            .map { File(it) }
            .firstOrNull { it.exists() }
}
