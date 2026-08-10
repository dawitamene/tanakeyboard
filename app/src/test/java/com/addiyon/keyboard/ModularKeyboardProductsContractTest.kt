package com.addiyon.keyboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularKeyboardProductsContractTest {
    private val root = if (File("settings.gradle.kts").isFile) File(".") else File("..")

    @Test
    fun requiredModulesAreIncluded() {
        val settings = File(root, "settings.gradle.kts").readText()
        listOf(
            ":keyboard:contracts",
            ":keyboard:core",
            ":language:api",
            ":language:english",
            ":language:amharic",
            ":suggestions:api",
            ":suggestions:core",
            ":suggestions:sqlite"
        ).forEach { assertTrue(it, settings.contains("include(\"$it\")")) }
    }

    @Test
    fun appOwnsNoLanguageCorporaOrSuggestionEngineBranches() {
        val appAssets = File(root, "app/src/main/assets")
        listOf(
            "amharic.db",
            "amharic_words.dat",
            "amharic_ngrams.dat",
            "english.db",
            "english_words.dat",
            "english_ngrams.dat",
            "dictionary_manifest.properties"
        ).forEach { assertFalse(it, File(appAssets, it).exists()) }

        val service = File(
            root,
            "app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt"
        ).readText()
        assertFalse(service.contains("private fun englishSuggestions("))
        assertFalse(service.contains("private fun amharicSuggestions("))
        assertFalse(service.contains("SQLiteLanguageStore("))
        val composingSources = File(root, "keyboard/core/src/main").walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(Regex("\\.setComposingRegion\\s*\\(").containsMatchIn(composingSources))
    }

    @Test
    fun eachPackOwnsItsAssetsAndImplementation() {
        val english = File(root, "language/english/src/main/assets")
        val amharic = File(root, "language/amharic/src/main/assets")
        listOf("english.db", "english_words.dat", "english_ngrams.dat").forEach {
            assertTrue(it, File(english, it).isFile)
        }
        listOf("amharic.db", "amharic_words.dat", "amharic_ngrams.dat").forEach {
            assertTrue(it, File(amharic, it).isFile)
        }
        assertTrue(
            File(
                root,
                "language/amharic/src/main/java/com/addiyon/keyboard/transliteration/Transliterator.kt"
            ).isFile
        )
    }
}
