package com.addiyon.keyboard.ai

import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestLifecycleContractTest {
    @Test
    fun `tone selection captures current editor text and starts a fresh request`() {
        val feature = featureSource()
        val selection = feature.substringAfter("fun onTabSelected(tab: AiToneTab)")
            .substringBefore("fun onCopyVariant")

        assertTrue(selection.contains("cancelRequest()"))
        assertTrue(selection.contains("val input = controller.captureInput()"))
        assertTrue(selection.contains("isLoading = true"))
        assertTrue(selection.contains("controller.revampVariants(input, tab)"))
        assertTrue(selection.contains("controller.loadQuota().getOrNull()"))
        assertFalse(selection.contains("consumeRequest"))
        assertFalse(selection.contains("if (uiState.isQuotaLoading) return"))
        assertFalse(selection.contains("val input = uiState.input"))
    }

    @Test
    fun `opening the panel clears the selected tone`() {
        val feature = featureSource()
        val open = feature.substringAfter("fun onToolbarAction()")
            .substringBefore("fun dismissPanel()")

        assertTrue(open.contains("selectedTab = null"))
    }

    @Test
    fun `optional AI panel is forty percent taller than the keyboard`() {
        val feature = featureSource()
        val keyboardScreen = projectRoot()
            .resolve("keyboard/ui/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt")
            .readText()

        assertTrue(feature.contains("AI_PANEL_HEIGHT_SCALE = 1.4f"))
        assertTrue(feature.contains("panelHeightScale = AI_PANEL_HEIGHT_SCALE"))
        assertTrue(keyboardScreen.contains("scaledKeyboardPanelHeight("))
        assertTrue(keyboardScreen.contains("scale = optionalUi.panelHeightScale"))
    }

    @Test
    fun `product service delegates optional AI without owning controller state`() {
        val service = serviceSource()

        assertTrue(service.contains("AiKeyboardFeature.create("))
        assertTrue(service.contains("override fun optionalKeyboardUi()"))
        assertTrue(service.contains("aiFeature.optionalKeyboardUi()"))
        assertTrue(service.contains("aiFeature.onFinishInput()"))
        assertFalse(service.contains("AiUiState"))
        assertFalse(service.contains("AiRepository"))
        assertFalse(service.contains("CoroutineScope"))
        assertFalse(service.contains("requestJob"))
    }

    @Test
    fun `AI replacement is cursor relative and never sets an absolute composing region`() {
        val adapter = projectRoot()
            .resolve("apps/textrevamp/src/main/java/com/addiyon/keyboard/AiEditorAdapter.kt")
            .readText()
        val feature = featureSource()

        assertTrue(adapter.contains("deleteSurroundingText("))
        assertTrue(adapter.contains("commitText(replacement, 1)"))
        assertFalse(adapter.contains("setComposingRegion"))
        assertFalse(feature.contains("replacementStart"))
        assertFalse(feature.contains("replacementEnd"))
        assertFalse(feature.contains("setComposingRegion"))
    }

    private fun serviceSource(): String = projectRoot()
        .resolve("apps/textrevamp/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt")
        .readText()

    private fun featureSource(): String = projectRoot()
        .resolve("features/ai/src/main/java/com/addiyon/keyboard/ai/AiKeyboardFeature.kt")
        .readText()

    private fun projectRoot(): File {
        val start = Paths.get(System.getProperty("user.dir")).toFile().canonicalFile
        return generateSequence(start) { it.parentFile }
            .first { File(it, "apps/textrevamp/src/main/java").isDirectory }
    }
}
