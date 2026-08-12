package com.addiyon.keyboard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularKeyboardProductsContractTest {
    private val root = generateSequence(
        File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
    ) {
        it.parentFile
    }.first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun requiredModulesAreIncluded() {
        val settings = File(root, "settings.gradle.kts").readText()
        listOf(
            ":apps:addiyon",
            ":apps:textrevamp",
            ":keyboard:contracts",
            ":keyboard:core",
            ":keyboard:runtime",
            ":keyboard:ui",
            ":language:api",
            ":language:android-api",
            ":language:english",
            ":language:amharic",
            ":language:oromo",
            ":features:ai"
        ).forEach { assertTrue(it, settings.contains("include(\"$it\")")) }
        assertFalse(settings.contains("include(\":app\")"))
        assertFalse(settings.contains("include(\":apps:english\")"))
        assertFalse(settings.contains("include(\":apps:oromo\")"))
    }

    @Test
    fun addiyonIncludesAmharicAndEnglishWhileOromoRemainsDisabled() {
        val build = File(root, "apps/addiyon/build.gradle.kts").readText()
        listOf(":language:amharic", ":language:english").forEach {
            assertTrue(it, build.contains("project(\"$it\")"))
        }
        assertFalse(build.contains("implementation(project(\":language:oromo\"))"))
        assertFalse(build.contains("project(\":features:ai\")"))
        val product = File(
            root,
            "apps/addiyon/src/main/java/com/addiyon/keyboard/AddiyonKeyboardProduct.kt"
        ).readText()
        assertTrue(product.contains("AmharicLanguagePackProvider"))
        assertTrue(product.contains("EnglishLanguagePackProvider"))
        assertFalse(product.contains("OromoLanguagePackProvider"))
        val service = File(
            root,
            "apps/addiyon/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt"
        ).readText()
        assertTrue(service.contains("configuredLanguagePackProviders"))
        assertFalse(service.contains("AiServiceFactory"))
        assertFalse(service.contains("onAiAction"))
    }

    @Test
    fun textRevampIsEnglishOnlyAndOwnsAiIntegration() {
        val build = File(root, "apps/textrevamp/build.gradle.kts").readText()
        assertTrue(build.contains("implementation(project(\":language:english\"))"))
        assertTrue(build.contains("implementation(project(\":features:ai\"))"))
        assertFalse(build.contains("implementation(project(\":language:amharic\"))"))
        assertFalse(build.contains("implementation(project(\":language:oromo\"))"))
        val product = File(
            root,
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/TextRevampKeyboardProduct.kt"
        ).readText()
        assertTrue(product.contains("TextRevampLanguagePackProviders"))
        assertTrue(product.contains("EnglishLanguagePackProvider"))
        assertTrue(product.contains("STANDARD_KEYBOARD_FEATURE_IDS - TYPING_GUIDE_FEATURE_ID"))
        assertTrue(product.contains("+ \"ai\""))
    }

    @Test
    fun productAppsEnumerateProvidersWithoutConstructingLanguageImplementations() {
        val appSources = listOf("apps/addiyon", "apps/textrevamp").flatMap { module ->
            File(root, "$module/src/main/java").walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .toList()
        }
        val forbiddenConstruction = Regex(
            "\\b(?:[A-Za-z0-9_]*SuggestionEngine|EnglishLanguagePack|" +
                "AmharicLanguagePack|OromoLanguagePack)\\s*\\("
        )
        val violations = appSources.flatMap { source ->
            forbiddenConstruction.findAll(source.readText()).map { match ->
                "${source.relativeTo(root).invariantSeparatorsPath}: ${match.value}"
            }.toList()
        }
        assertTrue(
            "Product apps must enumerate language providers, not construct packs or engines: " +
                violations.joinToString(),
            violations.isEmpty()
        )

        val providers = listOf(
            "language/english/src/main/java/com/addiyon/keyboard/language/english/" +
                "EnglishLanguagePackProvider.kt" to "EnglishSuggestionEngine(",
            "language/amharic/src/main/java/com/addiyon/keyboard/language/amharic/" +
                "AmharicLanguagePackProvider.kt" to "AmharicSuggestionEngine(",
            "language/oromo/src/main/java/com/addiyon/keyboard/language/oromo/" +
                "OromoLanguagePackProvider.kt" to "OromoLanguagePack("
        )
        providers.forEach { (path, construction) ->
            val source = File(root, path)
            assertTrue("Missing provider $path", source.isFile)
            assertTrue("$path must own $construction", source.readText().contains(construction))
        }
    }

    @Test
    fun languagePacksOwnTheirAssetsAndComposingStaysOffsetFree() {
        val english = File(root, "language/english/src/dictionary")
        val amharic = File(root, "language/amharic/src/dictionary")
        listOf("english_words.dat", "english_ngrams.dat").forEach {
            assertTrue(it, File(english, it).isFile)
        }
        listOf("amharic_words.dat", "amharic_lexemes.dat", "amharic_ngrams.dat").forEach {
            assertTrue(it, File(amharic, it).isFile)
        }
        assertTrue(
            File(root, "language/amharic/hornmorpho/UPSTREAM.md").isFile
        )
        assertTrue(
            File(root, "language/amharic/hornmorpho/LICENSE.txt").isFile
        )
        assertTrue(
            File(root, "archive/legacy-amharic-dictionary/corpus_surface_words.dat").isFile
        )
        val composingSources = File(root, "keyboard/core/src/main").walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(Regex("\\.setComposingRegion\\s*\\(").containsMatchIn(composingSources))
    }
}
