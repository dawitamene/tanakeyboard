package com.addiyon.keyboard.ui.design

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
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
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt",
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt",
            "apps/textrevamp/src/test/java/com/addiyon/keyboard/ui/design/DesignSystemContractTest.kt",
            "keyboard.svg",
            "keyboard.png",
            "play_store_icon_512.png",
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_icon.xml",
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_vector.xml",
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_adaptive_vector.xml"
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
    fun logoAssetsPreserveTheCurrentBrandContract() {
        val root = projectRoot()
        val sourceSvg = root.resolve("keyboard.svg").readText()
        val source = ImageIO.read(root.resolve("keyboard.png"))
        val foreground = root.resolve(
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_vector.xml"
        ).readText()
        val adaptiveForeground = root.resolve(
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_adaptive_vector.xml"
        ).readText()
        val appIcon = root.resolve(
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_icon.xml"
        ).readText()
        val playStoreIcon = ImageIO.read(root.resolve("play_store_icon_512.png"))

        assertTrue(sourceSvg.contains("viewBox=\"0 0 320 401\""))
        assertTrue(sourceSvg.contains("fill=\"#009099\""))
        assertEquals(1536, source.width)
        assertEquals(1536, source.height)
        assertTrue("The logo source must retain transparency.", source.colorModel.hasAlpha())
        assertTrue("The standalone mark must use 65% sizing.", foreground.contains("scaleX=\"0.83\""))
        assertTrue(
            "The adaptive mark must compensate for launcher mask zoom.",
            adaptiveForeground.contains("scaleX=\"0.5533\"")
        )
        assertTrue("The in-app icon must be Compose-compatible.", appIcon.contains("<vector"))
        assertTrue(
            "The in-app icon must use the canonical brand resource.",
            appIcon.contains("@color/addiyon_brand_primary")
        )
        assertEquals(512, playStoreIcon.width)
        assertEquals(512, playStoreIcon.height)
        assertEquals("The Play icon background must be white.", -1, playStoreIcon.getRGB(0, 0))

        val background = root.resolve(
            "apps/textrevamp/src/main/res/drawable/ic_addiyon_background.xml"
        ).readText()
        assertTrue("The adaptive icon background must be white.", background.contains("@color/white"))
    }

    @Test
    fun tokensComponentsAndThemesExposeTheCanonicalWiring() {
        val root = projectRoot()
        val tokens = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt"
        ).readText()
        val components = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt"
        ).readText()
        val theme = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/theme/Theme.kt"
        ).readText()
        listOf(
            "object AddiyonSpacing",
            "object AddiyonRadii",
            "object AddiyonSizes",
            "val formControl = 56.dp",
            "object AddiyonBorders",
            "val selectedTone = 3.dp",
            "object AddiyonMotion",
            "data class AddiyonBrand",
            "fun rememberAddiyonBrand",
            "colorResource(R.color.addiyon_brand_primary)",
            "data class AddiyonColors",
            "data class AddiyonAiToneColors",
            "brandPrimary",
            "onBrandPrimary",
            "aiToneIcons",
            "resultSurface",
            "onResultSurface",
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
        listOf(
            "AddiyonContentSection",
            "AddiyonDropdownMenu",
            "AddiyonInputField",
            "AddiyonScreenColumn"
        ).forEach {
            assertTrue(
                "AddiyonComponents.kt must expose $it. Reuse the canonical primitive instead of adding a one-off wrapper.",
                components.contains("fun $it")
            )
        }
        listOf(
            "val AddiyonTypography",
            "typography = AddiyonTypography",
            "rememberAddiyonBrand()",
            "LocalAddiyonColors provides"
        ).forEach { required ->
            assertTrue(
                "Theme.kt is missing $required. Both themes must use the shared design-system wiring.",
                theme.contains(required)
            )
        }
    }

    @Test
    fun aiToneIconAccentsAreDistinctInLightAndDarkAppearances() {
        val tokens = projectRoot().resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt"
        ).readText()
        val properties = listOf(
            "humanize",
            "professional",
            "casual",
            "formal",
            "friendly",
            "fixGrammar",
            "shorten",
            "summarize"
        )
        val appearances = listOf(
            tokens.substringAfter("fun addiyonLightColors").substringBefore("fun addiyonDarkColors"),
            tokens.substringAfter("fun addiyonDarkColors").substringBefore("val LocalAddiyonColors")
        )

        appearances.forEach { appearance ->
            val assignments = properties.map { property ->
                Regex("""\b$property = ([^,\n]+)""")
                    .find(appearance)
                    ?.groupValues
                    ?.get(1)
            }
            assertTrue(
                "Each appearance must define every AI tone icon accent.",
                assignments.none { it == null }
            )
            assertEquals(
                "Each AI tone icon must have a visually distinct accent within an appearance.",
                properties.size,
                assignments.toSet().size
            )
        }
    }

    @Test
    fun brandColorIsDeclaredOnceAndThemesUseDerivedRoles() {
        val root = projectRoot()
        val tokens = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt"
        ).readText()
        val theme = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/theme/Theme.kt"
        ).readText()
        val colors = root.resolve("keyboard/ui/src/main/res/values/colors.xml").readText()
        val brandLiteral = "#FF009099"

        assertEquals(1, brandLiteral.toRegex().findAll(tokens + theme + colors).count())
        assertTrue(tokens.contains("private fun Color.mix"))
        assertTrue(theme.contains("primary = brand.primary"))
        assertTrue(theme.contains("primary = brand.primaryLight"))
    }

    @Test
    fun brandedPrimaryContentBackgroundAndAuthControlsFollowTheVisualContract() {
        val root = projectRoot()
        val tokens = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonDesignTokens.kt"
        ).readText()
        val auth = root.resolve(
            "features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiAuthBottomSheet.kt"
        ).readText()
        val components = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt"
        ).readText()
        val settings = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSettingsShell.kt"
        ).readText()
        val appShellTheme = root.resolve(
            "features/app-shell/src/main/res/values/themes.xml"
        ).readText()

        assertTrue(tokens.contains("val onPrimary = Color.White"))
        assertTrue(tokens.contains("val onPrimaryDark = Color.White"))
        assertTrue(tokens.contains("val paper = Color.White.mix(Color.Black, 0.04f)"))
        assertTrue(tokens.contains("val surfaceVariant = Color.White.mix(Color.Black, 0.08f)"))
        assertTrue(auth.contains("RoundedCornerShape(AddiyonRadii.pill)"))
        assertTrue(components.contains("focusedBorderColor = Color.Transparent"))
        assertTrue(components.contains("focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant"))
        assertTrue(auth.contains("Modifier.padding(top = AddiyonSpacing.xs)"))
        assertTrue(settings.contains("RoundedCornerShape(AddiyonRadii.pill)"))
        assertTrue(appShellTheme.contains("<item name=\"colorOnPrimary\">@color/white</item>"))
    }

    @Test
    fun brandedInputsAndSectionsUseTheSharedComponents() {
        val root = projectRoot()
        val inputConsumers = listOf(
            "features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiAuthBottomSheet.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSetupActivity.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSettingsComponents.kt"
        )
        inputConsumers.forEach { relative ->
            val source = root.resolve(relative).readText()
            assertTrue("$relative must use AddiyonInputField.", source.contains("AddiyonInputField("))
            assertTrue("$relative must not own an OutlinedTextField.", !source.contains("OutlinedTextField("))
        }

        val sectionConsumers = listOf(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardFeedback.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSetupActivity.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSettingsComponents.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardHeightScreen.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardPersonalDictionaryScreen.kt",
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/KeyboardSettingsShell.kt"
        )
        sectionConsumers.forEach { relative ->
            assertTrue(
                "$relative must render section-like content with AddiyonContentSection.",
                root.resolve(relative).readText().contains("AddiyonContentSection(")
            )
        }
        val header = root.resolve(
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/TextRevampAppShell.kt"
        ).readText()
        val components = root.resolve(
            "keyboard/ui/src/main/java/com/addiyon/keyboard/ui/design/AddiyonComponents.kt"
        ).readText()
        val dashboard = root.resolve(
            "features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiDashboardContent.kt"
        ).readText()
        assertTrue(!header.contains("AddiyonDropdownMenu("))
        assertTrue(header.contains("AiAccountActivity.MODE_DASHBOARD"))
        assertTrue(dashboard.contains("shape = RoundedCornerShape(AddiyonRadii.small)"))
        assertTrue(components.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(components.contains("shape = RoundedCornerShape(AddiyonRadii.group)"))
        assertTrue(components.contains("tonalElevation = AddiyonElevation.none"))
        listOf(
            "features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiAuthBottomSheet.kt",
            "features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiDashboardContent.kt"
        ).forEach { relative ->
            assertTrue(
                "$relative must keep the AI screen flat without AddiyonContentSection.",
                !root.resolve(relative).readText().contains("AddiyonContentSection(")
            )
        }
        assertTrue(
            "The old settings-only GroupCard wrapper must stay removed.",
            !root.resolve(
                "apps/textrevamp/src/main/java/com/addiyon/keyboard/ui/settings/SettingsComponents.kt"
            ).exists()
        )
    }

    @Test
    fun productionUiDoesNotIntroduceRawScreenColors() {
        val root = projectRoot()
        val violations = mutableListOf<String>()
        listOf(
            root.resolve("apps/textrevamp/src/main/java/com/addiyon/keyboard/ui").toPath(),
            root.resolve("features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell").toPath(),
            root.resolve("features/ai/src/main/java/com/addiyon/keyboard/ui").toPath(),
            root.resolve("keyboard/ui/src/main/java/com/addiyon/keyboard/ui").toPath()
        ).forEach { uiRoot ->
            assertTrue("Missing production UI source directory: $uiRoot", Files.isDirectory(uiRoot))
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
                File(it, "apps/textrevamp/src/main/java").isDirectory &&
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
