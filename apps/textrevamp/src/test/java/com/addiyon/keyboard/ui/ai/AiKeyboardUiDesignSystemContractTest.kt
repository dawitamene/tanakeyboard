package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.CustomToneIcon
import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiKeyboardUiDesignSystemContractTest {
    @Test
    fun tonesUseKeyboardTokensSemanticRolesAndLocalizedCopy() {
        val ui = uiSource()

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
            assertTrue("AI keyboard UI must use $required.", ui.contains(required))
        }
        assertFalse(RAW_DP_LITERAL.containsMatchIn(ui))
        assertFalse(RAW_SP_LITERAL.containsMatchIn(ui))
        assertFalse(LITERAL_TEXT_CALL.containsMatchIn(ui))
        assertFalse(LITERAL_CONTENT_DESCRIPTION.containsMatchIn(ui))
        assertTrue(ui.contains("style = MaterialTheme.typography.labelSmall"))
        assertTrue(ui.contains(".size(AddiyonSizes.iconSmall)"))
        assertFalse(ui.contains("TONE_LABEL_SCALE"))
        assertFalse(ui.contains("TONE_ICON_SCALE"))
        assertTrue(ui.contains("AddiyonSizes.compact + AddiyonSpacing.xs * 2"))
        assertTrue(ui.contains("PaddingValues(horizontal = AddiyonSpacing.xs, vertical = AddiyonSpacing.xs)"))
        assertTrue(ui.contains(".padding(horizontal = AddiyonSpacing.xs)"))
        assertTrue(ui.contains("horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)"))
        assertTrue(ui.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(ui.contains("onBack: (() -> Unit)? = null"))
        assertTrue(ui.contains("testTag = AI_RESULTS_DISMISS_TAG"))
        assertTrue(ui.contains("Spacer(Modifier.width(AddiyonSizes.compact))"))
        assertTrue(ui.contains("Brush.verticalGradient("))
        assertTrue(ui.contains("glassSurface.copy(alpha = BACK_GLASS_TOP_ALPHA)"))
        assertTrue(ui.contains("glassSurface.copy(alpha = BACK_GLASS_BOTTOM_ALPHA)"))
        assertTrue(ui.contains("BACK_GLASS_TOP_ALPHA = 0.96f"))
        assertTrue(ui.contains("BACK_GLASS_BOTTOM_ALPHA = 0.86f"))
        assertFalse(ui.contains("BACK_GLASS_BORDER_ALPHA"))
        assertTrue(ui.contains("containerColor = Color.Transparent"))
        assertTrue(ui.contains("enabled = enabled"))
        assertTrue(ui.contains("DISABLED_TONE_ALPHA"))
        assertTrue(ui.contains("toneLabel"))
        assertTrue(AiToneTab.entries.all { tab ->
            ui.contains("AiToneTab.${tab.name} -> Icons.")
        })
    }

    @Test
    fun selectedToneGlowsAndAnimatesOnlyWhileLoading() {
        val ui = uiSource()
        val glow = ui.substringAfter("private fun toneGlowVisuals")
            .substringBefore("private fun animatedToneGradient")

        assertTrue(ui.contains("val glowModifier = if (selected && enabled)"))
        assertTrue(ui.contains("border = if (selected && enabled) toneGlowVisuals.border else null"))
        assertTrue(glow.contains("MaterialTheme.addiyonColors.aiToneGlow"))
        assertTrue(glow.contains("AddiyonBorders.selectedTone"))
        assertTrue(glow.contains("radius = AddiyonElevation.overlay"))
        assertTrue(glow.contains("if (isLoading)"))
        assertTrue(glow.contains("rememberInfiniteTransition"))
        assertTrue(glow.contains("Brush.sweepGradient("))
        assertTrue(glow.contains("Brush.horizontalGradient("))
        assertTrue(ui.contains("SELECTED_TONE_GLOW_ALPHA = 0.32f"))
        assertTrue(ui.contains("LOADING_TONE_GLOW_MIN_ALPHA = 0.46f"))
        assertTrue(ui.contains("LOADING_TONE_GLOW_MAX_ALPHA = 0.68f"))
    }

    @Test
    fun resultsReplaceTheSuggestionAndKeyboardRegionsDuringLoadingAndAfterward() {
        val ui = uiSource()
        val feature = featureSource()
        val screen = projectRoot()
            .resolve("keyboard/ui/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt")
            .readText()

        assertFalse(feature.contains("AiPanel("))
        assertFalse(feature.contains("panelVisible ="))
        assertFalse(feature.contains("panelHeightScale"))
        assertTrue(feature.contains("contextualRowVisible = true"))
        assertTrue(feature.contains("selectedTab = uiState.selectedTab"))
        assertTrue(feature.contains("isLoading = uiState.isLoading"))
        assertFalse(feature.contains("contentDimmed ="))
        assertFalse(feature.contains("contentLoadingVisible ="))
        assertFalse(feature.contains("contentLoadingIndicator ="))
        assertTrue(feature.contains("contentOverlayVisible = uiState.isLoading"))
        assertTrue(feature.contains("AiResultsOverlay("))
        assertTrue(screen.contains("if (optionalUi.contentOverlayVisible)"))
        assertTrue(screen.contains("optionalUi.contentOverlay()"))
        assertTrue(screen.contains("OPTIONAL_CONTENT_DIMMED_ALPHA = 0.15f"))
        assertTrue(screen.contains(".alpha(optionalContentAlpha)"))
        assertTrue(screen.contains("if (optionalUi.contentLoadingVisible)"))
        assertTrue(screen.contains("optionalUi.contentLoadingIndicator()"))
        assertTrue(screen.contains("val contentHeight = 40.dp + keyboardRowsHeight("))
        assertTrue(screen.contains("return@keyboardContent"))
        assertTrue(ui.contains("fun AiResultsOverlay("))
        assertTrue(ui.contains(".fillMaxSize()"))
        assertTrue(ui.contains("verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)"))
        assertTrue(ui.contains(".weight(1f)"))
        assertTrue(ui.contains("onClick = { onReplaceVariant(strength) }"))
        val resultText = ui.substringAfter("text = result.text")
            .substringBefore("}\n                            }")
        assertFalse(resultText.contains("maxLines"))
        assertFalse(resultText.contains("TextOverflow.Ellipsis"))
        assertTrue(ui.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(ui.contains("if (state.isLoading)"))
        assertTrue(ui.contains("AiResultsSkeleton()"))
        assertTrue(ui.contains("testTag(AI_RESULTS_SKELETON_TAG)"))
        assertTrue(ui.contains("repeat(RESULT_SKELETON_CARD_COUNT)"))
        assertTrue(ui.contains("label = \"aiResultSkeletonPulse\""))
        val resultsOverlay = ui.substringAfter("fun AiResultsOverlay(")
            .substringBefore("private data class ToneGlowVisuals")
        assertFalse(resultsOverlay.contains("SuggestionChevronLeftButton("))
        assertFalse(resultsOverlay.contains("toneLabel("))
        assertFalse(ui.contains("fun AiLoadingIndicator("))
        assertFalse(ui.contains("AI_LOADING_INDICATOR_TAG"))
        assertFalse(ui.contains("LOADING_DOT_COUNT"))
        assertTrue(ui.contains("rememberInfiniteTransition"))
        assertFalse(ui.contains("AI_RESPONSE_LOADING_TAG"))
        assertFalse(ui.contains("AiResponseLoading"))
    }

    @Test
    fun accountUsageMeterIsContinuousAndOmitsTokenCounts() {
        val dashboard = projectRoot()
            .resolve("features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiDashboardContent.kt")
            .readText()

        assertTrue(dashboard.contains("Arrangement.spacedBy(AddiyonSpacing.xs)"))
        assertTrue(dashboard.contains("gapSize = 0.dp"))
        assertTrue(dashboard.contains("drawStopIndicator = {}"))
        assertFalse(dashboard.contains("strings.aiUsageTokensRemainingFormat"))
    }

    @Test
    fun fixGrammarAndCasualLeadTheToneOrder() {
        val models = projectRoot()
            .resolve("features/ai/src/main/java/com/addiyon/keyboard/ai/AiModels.kt")
            .readText()

        assertTrue(
            models.contains(
                "val DefaultTabs = listOf(FixGrammar, Casual, Humanize, Professional, Shorten)"
            )
        )
    }

    @Test
    fun customToneAndAddChipsFollowTheToneRowContract() {
        val ui = uiSource()
        val feature = featureSource()

        assertTrue(ui.contains("customTones.forEach { custom ->"))
        assertTrue(ui.contains("aiCustomToneChipTag(custom)"))
        assertTrue(ui.contains("onCustomToneSelected(custom)"))
        assertTrue(ui.contains("selectedCustomToneId == custom.id"))
        assertTrue(ui.contains("icon = customToneIcon(custom.icon)"))
        assertTrue(ui.contains("customToneColor(custom.color)"))
        assertTrue(ui.contains("Icons.Outlined.Add"))
        assertTrue(ui.contains("AI_TONE_ADD_TAG"))
        assertTrue(ui.contains("strings.aiAddCustomTone"))
        assertTrue(ui.contains("onAddCustomTone: (() -> Unit)?"))
        assertTrue(ui.contains("onCustomToneSelected: (CustomTone) -> Unit"))
        assertTrue(feature.contains("openCustomTone"))
    }

    @Test
    fun customToneScreenUsesTheIconGridWithAGrayishDefaultAndColorPopup() {
        val content = projectRoot()
            .resolve("features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiCustomToneContent.kt")
            .readText()

        assertTrue(content.contains("CustomToneIcon.All.chunked(CUSTOM_TONE_ICON_COLUMNS)"))
        assertTrue(content.contains("CUSTOM_TONE_ICON_ROWS"))
        assertTrue(content.contains("aiCustomToneIconTag(iconId)"))
        assertTrue(content.contains("AI_CUSTOM_TONE_ICON_GRID_TAG"))
        assertTrue(content.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(content.contains("fun CustomToneColorPopup"))
        assertTrue(content.contains("Popup("))
        assertTrue(content.contains("PopupProperties(focusable = false)"))
        assertTrue(content.contains("customToneColor(selectedColor)"))
    }

    @Test
    fun everyCustomToneIconIdMapsToAnIcon() {
        val visuals = projectRoot()
            .resolve("features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiCustomToneVisuals.kt")
            .readText()

        CustomToneIcon.All.forEach { id ->
            assertTrue(
                "Missing icon mapping for $id.",
                visuals.contains("CustomToneIcon.${id.uppercase()} -> Icons.")
            )
        }
    }

    private fun uiSource(): String = projectRoot()
        .resolve("features/ai/src/main/java/com/addiyon/keyboard/ui/ai/AiPanel.kt")
        .readText()

    private fun featureSource(): String = projectRoot()
        .resolve("features/ai/src/main/java/com/addiyon/keyboard/ai/AiKeyboardFeature.kt")
        .readText()

    private fun projectRoot(): File {
        val start = Paths.get(System.getProperty("user.dir")).toFile().canonicalFile
        return generateSequence(start) { it.parentFile }
            .first { File(it, "apps/textrevamp/src/main/java").isDirectory }
    }

    private companion object {
        val RAW_DP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.dp\b""")
        val RAW_SP_LITERAL = Regex("""\b\d+(?:\.\d+)?\.sp\b""")
        val LITERAL_TEXT_CALL = Regex("""\bText\s*\(\s*\"""")
        val LITERAL_CONTENT_DESCRIPTION = Regex("""contentDescription\s*=\s*\"""")
    }
}
