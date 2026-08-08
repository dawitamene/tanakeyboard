package com.addiyon.keyboard.ui.design

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemContractTest {
    @Test
    fun requiredDesignSystemFilesAndInstructionsArePresent() {
        val root = projectRoot()
        val required = listOf(
            "docs/DESIGN_SYSTEM.md",
            "AGENTS.md",
            "CLAUDE.md",
            "CONTRIBUTING.md",
            ".github/pull_request_template.md",
            "app/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt",
            "app/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt",
            "app/src/test/java/com/addiyon/keyboard/ui/design/DesignSystemContractTest.kt"
        )
        required.forEach { relative ->
            assertTrue(
                "Missing design-system file $relative. Add it before changing UI.",
                root.resolve(relative).isFile
            )
        }
        listOf("AGENTS.md", "CLAUDE.md", "CONTRIBUTING.md").forEach { relative ->
            val content = root.resolve(relative).readText()
            assertTrue(
                "$relative must link to docs/DESIGN_SYSTEM.md. Add the mandatory design-system gate.",
                content.contains("docs/DESIGN_SYSTEM.md")
            )
        }
        val pullRequestTemplate = root.resolve(".github/pull_request_template.md").readText()
        assertTrue(
            ".github/pull_request_template.md must link to docs/DESIGN_SYSTEM.md.",
            pullRequestTemplate.contains("docs/DESIGN_SYSTEM.md")
        )
    }

    @Test
    fun tokensComponentsAndThemesExposeTheCanonicalWiring() {
        val root = projectRoot()
        val tokens = root.resolve(
            "app/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt"
        ).readText()
        val components = root.resolve(
            "app/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt"
        ).readText()
        val theme = root.resolve(
            "app/src/main/java/com/addiyon/keyboard/ui/theme/Theme.kt"
        ).readText()
        val settings = root.resolve(
            "app/src/main/java/com/addiyon/keyboard/ui/settings/SettingsComponents.kt"
        ).readText()
        listOf(
            "object AddiyonSpacing",
            "object AddiyonRadii",
            "object AddiyonSizes",
            "object AddiyonMotion",
            "data class AddiyonColors",
            "brandPrimary",
            "onBrandPrimary",
            "aiResultSurface",
            "onAiResultSurface",
            "successContainer",
            "onSuccessContainer",
            "LocalAddiyonColors",
            "MaterialTheme.addiyonColors"
        ).forEach { required ->
            assertTrue(
                "AddiyonDesignTokens.kt is missing $required. Use the documented public token or semantic role.",
                tokens.contains(required)
            )
        }
        listOf("AddiyonGroupSurface", "AddiyonSectionCard", "AddiyonScreenColumn").forEach {
            assertTrue(
                "AddiyonComponents.kt must expose $it. Reuse the canonical primitive instead of adding a one-off wrapper.",
                components.contains("fun $it")
            )
        }
        listOf(
            "val AddiyonTypography",
            "typography = AddiyonTypography",
            "LocalAddiyonColors provides"
        ).forEach { required ->
            assertTrue(
                "Theme.kt is missing $required. Both themes must use the shared design-system wiring.",
                theme.contains(required)
            )
        }
        assertTrue(
            "SettingsComponents.kt must delegate GroupCard to AddiyonGroupSurface.",
            settings.contains("AddiyonGroupSurface")
        )
    }

    @Test
    fun productionUiDoesNotIntroduceRawScreenColors() {
        val root = projectRoot()
        val uiRoot = root.resolve("app/src/main/java/com/addiyon/keyboard/ui").toPath()
        assertTrue("Missing production UI source directory: $uiRoot", Files.isDirectory(uiRoot))
        val violations = mutableListOf<String>()
        Files.walk(uiRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { path ->
                    if (isApprovedImplementation(path, uiRoot)) return@forEach
                    val content = Files.readString(path)
                    RAW_COLOR_LITERAL.findAll(content).forEach { match ->
                        violations += "${root.toPath().relativize(path)}: ${match.value}"
                    }
                    DIRECT_COLOR_REFERENCE.findAll(content).forEach { match ->
                        violations += "${root.toPath().relativize(path)}: ${match.value}"
                    }
                }
        }
        assertTrue(
            "Design-system color contract failed. Replace raw screen colors with " +
                "MaterialTheme.colorScheme roles or MaterialTheme.addiyonColors, then rerun " +
                "DesignSystemContractTest. Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun isApprovedImplementation(path: Path, uiRoot: Path): Boolean {
        val relative = uiRoot.relativize(path).toString().replace(File.separatorChar, '/')
        return relative.startsWith("theme/") ||
            relative.startsWith("design/") ||
            relative == "icons/ShiftIcon.kt"
    }

    private fun projectRoot(): File {
        val start = Paths.get(System.getProperty("user.dir")).toFile().canonicalFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull {
                File(it, "app/src/main/java").isDirectory &&
                    (File(it, "settings.gradle.kts").isFile || File(it, "settings.gradle").isFile)
            }
            ?: error(
                "Could not locate Addiyon project root from ${start.path}. " +
                    "Run the contract test from the repository or app directory."
            )
    }

    private companion object {
        val RAW_COLOR_LITERAL = Regex("""\b(?:androidx\.compose\.ui\.graphics\.)?Color\s*\(\s*0x[0-9A-Fa-f]+""")
        val DIRECT_COLOR_REFERENCE = Regex(
            """(?:androidx\.compose\.ui\.graphics\.)?Color\.(Black|DarkGray|Gray|LightGray|White|Red|Green|Blue|Yellow|Cyan|Magenta)\b"""
        )
    }
}
