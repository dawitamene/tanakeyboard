package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiToneTab
import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPanelDesignSystemContractTest {
    @Test
    fun panelUsesKeyboardTokensSemanticRolesAndLocalizedCopy() {
        val panel = panelSource()

        listOf(
            "AddiyonSpacing",
            "AddiyonRadii",
            "AddiyonSizes",
            "AddiyonBorders",
            "AddiyonElevation",
            "AiUiStrings",
            "MaterialTheme.colorScheme",
            "MaterialTheme.addiyonColors"
        ).forEach { required ->
            assertTrue(
                "AiPanel must use $required. Replace one-off values or copy with the public design-system API.",
                panel.contains(required)
            )
        }
        assertTrue(
            "Tone chips must use the compact IME control token.",
            panel.contains("AddiyonSizes.compact")
        )
        assertTrue(
            "Tone controls must keep one background and use an animated icon-color selection border.",
                panel.contains("horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)") &&
                panel.contains("horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs)") &&
                panel.contains("shape = RoundedCornerShape(AddiyonRadii.pill)") &&
                panel.contains("selected = selected") &&
                panel.contains("color = MaterialTheme.colorScheme.surface") &&
                panel.contains("border = toneSelectionBorder(iconColor, selected, isLoading)") &&
                panel.contains(".padding(horizontal = AddiyonSpacing.xs)") &&
                panel.contains("imageVector = toneIcon(tab)") &&
                panel.contains("tint = iconColor") &&
                panel.contains(".size(AddiyonSizes.iconSmall)") &&
                panel.contains(".testTag(aiPanelToneIconTag(tab))")
        )
        assertTrue(
            "The toolbar must use compact top and bottom spacing.",
            panel.contains("top = AddiyonSpacing.xs") &&
                panel.contains("bottom = AddiyonSpacing.sm")
        )
        assertFalse(
            "The AI toolbar must not render removed heading or tone-label copy.",
            panel.contains("strings.aiRephraseTitle") || panel.contains("strings.aiRephraseSubtitle") ||
                panel.contains("strings.aiToneLabel")
        )
        assertTrue(
            "The AI toolbar must use a minimum-touch white circular back action with a small arrow.",
            panel.contains("SuggestionChevronLeftButton(") &&
                panel.contains("contentDescription = strings.back") &&
                panel.contains("testTag = AI_PANEL_BACK_ACTION_TAG") &&
                panel.contains("buttonSize = AddiyonSizes.minimumTouchTarget") &&
                panel.contains("iconSize = AddiyonSizes.iconSmall") &&
                panel.contains("containerColor = MaterialTheme.addiyonColors.resultSurface") &&
                panel.contains("iconTint = MaterialTheme.addiyonColors.onResultSurface") &&
                !panel.contains("Icons.Filled.AutoAwesome") &&
                !panel.contains("Icons.Filled.Close")
        )
        val toolbar = panel.substringAfter("private fun AiPanelToolbar")
            .substringBefore("private fun AiToneRow")
        assertTrue(
            "Tone controls must be rendered directly beside the back action.",
            toolbar.indexOf("SuggestionChevronLeftButton(") in 0 until toolbar.indexOf("AiToneRow(") &&
                toolbar.contains("modifier = Modifier.weight(1f)")
        )
        assertFalse(
            "The compact toolbar must not render a title or usage control.",
            panel.contains("strings.aiPanelTitle") ||
                panel.contains("AI_PANEL_USAGE_ACTION_TAG") ||
                panel.contains("AI_PANEL_USAGE_PROGRESS_TAG") ||
                panel.contains("strings.aiUsageBadgeFormat") ||
                panel.contains("strings.aiUsageOpenDescriptionFormat") ||
                panel.contains("strings.aiQuotaFormat")
        )
        val selectionBorder = panel.substringAfter("private fun toneSelectionBorder")
            .substringBefore("private fun AiPanelAuthCard")
        assertTrue(
            "The selected tone border must be static when idle and animate a same-hue sweep only while loading.",
            selectionBorder.contains("AddiyonBorders.selectedTone") &&
                selectionBorder.contains("if (!isLoading)") &&
                selectionBorder.contains("color = color") &&
                selectionBorder.contains("Brush.sweepGradient(colors)") &&
                selectionBorder.contains("rememberInfiniteTransition") &&
                selectionBorder.contains("color.copy(alpha =")
        )
    }

    @Test
    fun panelHasNoRawDimensionsOrLiteralUserCopy() {
        val panel = panelSource()

        assertFalse(
            "AiPanel contains a raw dp literal. Use AddiyonSpacing, AddiyonRadii, AddiyonSizes, or AddiyonElevation.",
            RAW_DP_LITERAL.containsMatchIn(panel)
        )
        assertFalse(
            "AiPanel contains a raw sp literal. Use MaterialTheme.typography instead.",
            RAW_SP_LITERAL.containsMatchIn(panel)
        )
        assertFalse(
            "AiPanel contains literal Text copy. Add the string to AppStrings and read LocalAppStrings.",
            LITERAL_TEXT_CALL.containsMatchIn(panel)
        )
        assertFalse(
            "AiPanel contains a literal contentDescription. Add an accessible label to AppStrings.",
            LITERAL_CONTENT_DESCRIPTION.containsMatchIn(panel)
        )
        assertTrue(
            "AiPanel must map tone enums to localized AppStrings fields.",
            panel.contains("toneLabel")
        )
        assertTrue(
            "AiPanel must map every tone enum to a supporting icon.",
            panel.contains("toneIcon") && AiToneTab.entries.all { tab ->
                panel.contains("AiToneTab.${tab.name} -> Icons.")
            }
        )
        val iconColors = panel.substringAfter("private fun toneIconColor")
            .substringBefore("private fun toneLabel")
        assertTrue(
            "Tone icons must use dedicated theme accents in every selection state.",
            iconColors.contains("MaterialTheme.addiyonColors.aiToneIcons") &&
                AiToneTab.entries.all { tab ->
                    val property = tab.name.replaceFirstChar { it.lowercase() }
                    iconColors.contains("AiToneTab.${tab.name} -> colors.$property")
                }
        )
    }

    @Test
    fun panelKeepsFixedHeightShellAndStacksResultsInsideBoundedScroll() {
        val panel = panelSource()
        val skeleton = panel.substringAfter("private fun SkeletonResults()")
            .substringBefore("private fun ErrorCard")

        assertTrue(
            "The IME AI panel must fill its measured height; do not replace fillMaxHeight with a root scroll.",
            panel.contains("fillMaxHeight()")
        )
        val weightedContent = WEIGHTED_VERTICAL_SCROLL.find(panel)
        assertTrue(
            "The AI panel needs a weighted, bounded inner verticalScroll so the toolbar stays visible.",
            weightedContent != null
        )
        assertTrue(
            "All generated variants must render as vertically stacked, rounded flat surfaces.",
            panel.contains("orderedVariants.forEach") &&
                panel.contains(".testTag(aiPanelVariantTag(strength))") &&
                panel.contains("shape = RoundedCornerShape(AddiyonRadii.medium)") &&
                panel.contains("color = MaterialTheme.addiyonColors.resultSurface") &&
                panel.contains("style = MaterialTheme.typography.bodySmall") &&
                panel.contains("aiPanelCopyTag(strength)") &&
                panel.contains("aiPanelReplaceTag(strength)") &&
                panel.contains("onCopyVariant(strength)") &&
                panel.contains("onReplaceVariant(strength)")
        )
        assertFalse(
            "The compact AI panel must not duplicate input, strength selectors, or pinned global actions.",
            panel.contains("InputCard") || panel.contains("strengthLabel") ||
                panel.contains("PinnedAiActions") || panel.contains("AI_PANEL_COPY_TAG") ||
                panel.contains("AI_PANEL_REPLACE_TAG")
        )
        assertTrue(
            "Loading must use three animated skeleton results.",
            panel.contains("SkeletonResults()") &&
                panel.contains("repeat(AI_RESULT_VARIANT_COUNT)") &&
                panel.contains("AI_PANEL_SKELETON_TAG") &&
                skeleton.contains("rememberInfiniteTransition") &&
                skeleton.contains("Brush.linearGradient") &&
                skeleton.contains("AddiyonMotion.gentle")
        )
        assertFalse(
            "Loading must not show a spinner or status sentence.",
            skeleton.contains("CircularProgressIndicator") || skeleton.contains("strings.aiLoading")
        )
    }

    private fun panelSource(): String = projectRoot()
        .resolve("features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiPanel.kt")
        .readText()

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
        val RAW_DP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
        val RAW_SP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.sp\b""")
        val LITERAL_TEXT_CALL = Regex("""\bText\s*\(\s*\"""")
        val LITERAL_CONTENT_DESCRIPTION = Regex("""contentDescription\s*=\s*\"""")
        val WEIGHTED_VERTICAL_SCROLL = Regex(
            """weight\(1f,\s*fill\s*=\s*true\).*?verticalScroll\(rememberScrollState\(\)\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
    }
}
