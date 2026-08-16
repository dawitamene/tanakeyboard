package com.addiyon.keyboard.product

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductArchitectureBoundaryContractTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir"))).canonicalFile) {
        it.parentFile
    }.first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `app modules contain composition roots instead of keyboard implementations`() {
        val forbiddenDirectories = setOf("ui", "layout", "suggestion", "composing", "runtime")
        val forbiddenRootNames = Regex(
            "(?:.*KeyboardView|KeyboardStatus|KeyboardLayout|KeyboardScreen|.*Suggestion.*|TypingController|EditorGateway)\\.kt"
        )
        val violations = listOf("apps/addiyon", "apps/textrevamp").flatMap { module ->
            val sourceRoot = File(root, "$module/src/main/java")
            sourceRoot.walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .mapNotNull { source ->
                    val relative = source.relativeTo(sourceRoot).invariantSeparatorsPath
                    val segments = relative.split('/')
                    val allowedCompositionRoot = source.name.endsWith("KeyboardService.kt") ||
                        source.name.endsWith("KeyboardProduct.kt") ||
                        source.name.endsWith("Product.kt")
                    // Addiyon owns its product-specific manual/guide screens.
                    val allowedProductDestination = segments.contains("ui") &&
                        segments.contains("manual")
                    val ownsKeyboardImplementation = segments.dropLast(1).any {
                        it in forbiddenDirectories
                    } || forbiddenRootNames.matches(source.name)
                    relative.takeIf {
                        ownsKeyboardImplementation && !allowedCompositionRoot && !allowedProductDestination
                    }?.let { "$module/src/main/java/$it" }
                }
                .toList()
        }

        assertTrue(
            "Product modules must contain only thin service/product composition roots, not " +
                "keyboard UI, layout, suggestion, composing, or runtime implementations. " +
                "Move these files into shared modules:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `Addiyon and shared keyboard modules contain no AI dependency import or symbol`() {
        val targets = listOf(
            "apps/addiyon",
            "keyboard/runtime",
            "keyboard/ui",
            "keyboard/preferences",
            "features/app-shell"
        )
        val dependencyPattern = Regex(
            "\\b(?:api|implementation|compileOnly|runtimeOnly)\\s*\\(\\s*project\\s*\\(\\s*\":features:ai\""
        )
        val importPattern = Regex("^\\s*import\\s+com\\.addiyon\\.keyboard\\.ai(?:\\.|$)")
        val symbolPattern = Regex("\\b(?:Ai[A-Z]\\w*|ai[A-Z]\\w*|onAi[A-Z]\\w*|AI_[A-Z0-9_]+)\\b")
        // The documented design-system exception: the AI tone icon accents and
        // neon glow tokens live in AddiyonDesignTokens.kt (see docs/DESIGN_SYSTEM.md).
        val documentedToneTokens = setOf("aiToneIcons", "aiToneGlow")
        val violations = mutableListOf<String>()

        targets.forEach { target ->
            val buildFile = File(root, "$target/build.gradle.kts")
            if (buildFile.isFile) {
                buildFile.readLines().forEachIndexed { index, line ->
                    if (dependencyPattern.containsMatchIn(line)) {
                        violations += "$target/build.gradle.kts:${index + 1}: ${line.trim()}"
                    }
                }
            }
            val sourceRoot = File(root, "$target/src/main")
            if (sourceRoot.isDirectory) {
                sourceRoot.walkTopDown()
                    .filter(File::isFile)
                    .filter { it.extension == "kt" }
                    .forEach { source ->
                        source.readLines().forEachIndexed { index, line ->
                            val matchedSymbols = symbolPattern.findAll(line)
                                .map { it.value }
                                .filterNot { it in documentedToneTokens }
                            if (importPattern.containsMatchIn(line) || matchedSymbols.any()) {
                                violations += "${source.relativeTo(root).invariantSeparatorsPath}:" +
                                    "${index + 1}: ${line.trim()}"
                            }
                        }
                    }
            }
        }

        assertTrue(
            "AI must remain a TextRevamp-only leaf feature. Remove AI dependencies, imports, " +
                "and symbols from Addiyon, preferences, app shell, and shared keyboard modules:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `both product services extend the same shared product service`() {
        val addiyonService = File(
            root,
            "apps/addiyon/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt"
        )
        val textRevampService = File(
            root,
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt"
        )
        val addiyonBase = directServiceBase(addiyonService)
        val textRevampBase = directServiceBase(textRevampService)
        val runtimeSources = File(root, "keyboard/runtime/src/main").walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(
            "Product services must extend a product-capable shared runtime, not the platform-only " +
                "BaseKeyboardService.",
            addiyonBase != "BaseKeyboardService" && textRevampBase != "BaseKeyboardService"
        )
        assertEquals(
            "Addiyon and TextRevamp must be thin configurations of the same shared keyboard " +
                "service. Addiyon extends $addiyonBase while TextRevamp extends $textRevampBase.",
            addiyonBase,
            textRevampBase
        )
        assertTrue(
            "$addiyonBase must be declared by keyboard:runtime.",
            Regex("\\b(?:abstract\\s+)?class\\s+${Regex.escape(addiyonBase)}\\b")
                .containsMatchIn(runtimeSources)
        )
    }

    @Test
    fun `Android language providers keep construction out of products and shared runtime`() {
        val settings = File(root, "settings.gradle.kts").readText()
        assertTrue(settings.contains("include(\":language:android-api\")"))

        val runtimeBuild = File(root, "keyboard/runtime/build.gradle.kts").readText()
        assertTrue(runtimeBuild.contains("api(project(\":language:android-api\"))"))
        val runtimeSource = File(
            root,
            "keyboard/runtime/src/main/java/com/addiyon/keyboard/PackKeyboardService.kt"
        ).readText()
        assertTrue(runtimeSource.contains("AndroidLanguagePackEnvironment("))
        assertTrue(runtimeSource.contains("configuredLanguagePackProviders"))
        assertFalse(
            Regex(
                "import com\\.addiyon\\.keyboard\\.language\\.(?:amharic|english|oromo)"
            ).containsMatchIn(runtimeSource)
        )

        listOf("english", "amharic", "oromo").forEach { language ->
            val build = File(root, "language/$language/build.gradle.kts").readText()
            assertTrue(
                "language:$language must expose its neutral Android provider contract",
                build.contains("api(project(\":language:android-api\"))")
            )
            val providerFiles = File(root, "language/$language/src/main").walkTopDown()
                .filter(File::isFile)
                .filter { it.name.endsWith("LanguagePackProvider.kt") }
                .toList()
            assertEquals("language:$language must expose one provider", 1, providerFiles.size)
            assertTrue(providerFiles.single().readText().contains("AndroidLanguagePackProvider"))
        }

        val appViolations = listOf("apps/addiyon", "apps/textrevamp").flatMap { module ->
            File(root, "$module/src/main/java").walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .filter {
                    Regex("\\b[A-Za-z0-9_]*SuggestionEngine\\s*\\(")
                        .containsMatchIn(it.readText())
                }
                .map { it.relativeTo(root).invariantSeparatorsPath }
                .toList()
        }
        assertTrue(
            "Product apps must never construct suggestion engines: $appViolations",
            appViolations.isEmpty()
        )
    }

    @Test
    fun `shared runtime owns the common emoji and voice features`() {
        val runtimeBuild = File(root, "keyboard/runtime/build.gradle.kts").readText()
        listOf(":features:emoji", ":features:voice").forEach { feature ->
            val dependency = Regex(
                "\\b(?:api|implementation)\\s*\\(\\s*project\\(\"" +
                    Regex.escape(feature) +
                    "\"\\)\\s*\\)"
            )
            assertTrue(
                "keyboard:runtime must own common feature $feature.",
                dependency.containsMatchIn(runtimeBuild)
            )
            listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
                val appBuild = File(root, "$module/build.gradle.kts").readText()
                assertFalse(
                    "$module must receive common feature $feature through keyboard:runtime.",
                    dependency.containsMatchIn(appBuild)
                )
            }
        }
    }

    @Test
    fun `shared modules own standard component and application manifest wiring`() {
        val runtimeManifest = File(root, "keyboard/runtime/src/main/AndroidManifest.xml")
            .readText()
        assertTrue(runtimeManifest.contains("android.permission.VIBRATE"))
        assertTrue(runtimeManifest.contains("com.addiyon.keyboard.AddiyonKeyboardService"))
        assertTrue(runtimeManifest.contains("android.permission.BIND_INPUT_METHOD"))
        assertTrue(runtimeManifest.contains("@xml/method"))

        val shellManifest = File(root, "features/app-shell/src/main/AndroidManifest.xml")
            .readText()
        assertTrue(shellManifest.contains("com.addiyon.keyboard.MainActivity"))
        assertTrue(shellManifest.contains("@xml/backup_rules"))
        assertTrue(shellManifest.contains("@xml/data_extraction_rules"))
        assertTrue(shellManifest.contains("Theme.KeyboardAppShell.Splash"))

        listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
            val productManifest = File(root, "$module/src/main/AndroidManifest.xml").readText()
            listOf(
                "android.permission.VIBRATE",
                "AddiyonKeyboardService",
                "MainActivity",
                "android:allowBackup",
                "android:dataExtractionRules",
                "android:fullBackupContent",
                "android:supportsRtl"
            ).forEach { repeated ->
                assertFalse(
                    "$module must inherit shared manifest wiring instead of repeating $repeated",
                    productManifest.contains(repeated)
                )
            }
        }
    }

    private fun directServiceBase(source: File): String {
        val match = Regex(
            "class\\s+AddiyonKeyboardService\\s*:\\s*([A-Za-z0-9_.]+)\\s*\\(",
            RegexOption.DOT_MATCHES_ALL
        ).find(source.readText())
        return checkNotNull(match?.groupValues?.get(1)) {
            "Could not identify the direct service base in ${source.relativeTo(root)}"
        }.substringAfterLast('.')
    }
}
