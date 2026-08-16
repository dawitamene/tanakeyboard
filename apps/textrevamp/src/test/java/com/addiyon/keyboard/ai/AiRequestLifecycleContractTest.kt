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
        val selection = feature.substringAfter("private fun startToneRequest(tab: AiToneTab?, custom: CustomTone?)")
            .substringBefore("fun onReplaceVariant")

        assertTrue(selection.contains("cancelRequest()"))
        assertTrue(selection.contains("val input = controller.captureInput()"))
        assertTrue(selection.contains("isLoading = true"))
        assertTrue(selection.contains("val loadResultsInPlace = uiState.hasResult || uiState.isResultLoading"))
        assertTrue(selection.contains("isResultLoading = loadResultsInPlace"))
        assertTrue(selection.contains("isLoading = false"))
        assertTrue(selection.contains("isResultLoading = false"))
        assertTrue(selection.contains("controller.revampVariants(input, checkNotNull(tab))"))
        assertTrue(selection.contains("controller.revampCustomVariants(input, custom.instruction)"))
        assertTrue(selection.contains("controller.loadQuota().getOrNull()"))
        assertTrue(selection.contains("firstError.copy(remaining = refreshedQuota.remaining)"))
        assertFalse(selection.contains("consumeRequest"))
        assertFalse(selection.contains("if (uiState.isQuotaLoading) return"))
        assertFalse(selection.contains("val input = uiState.input"))
    }

    @Test
    fun `custom tone chip opens the AI screen and runs the saved instruction`() {
        val feature = featureSource()

        assertTrue(feature.contains("customTones = customTones"))
        assertTrue(feature.contains("selectedCustomToneId = uiState.selectedCustomToneId"))
        assertTrue(feature.contains("onCustomToneSelected = ::onKeyboardCustomToneSelected"))
        assertTrue(feature.contains("onAddCustomTone = ::onAddCustomTone"))
        assertTrue(feature.contains("fun onAddCustomTone()"))
        assertTrue(feature.contains("openCustomTone()"))
        assertTrue(feature.contains("private fun onKeyboardCustomToneSelected"))
        assertTrue(feature.contains("store.customTones()"))
        assertTrue(feature.contains("store.registerCustomToneChangeListener"))
        assertTrue(feature.contains("store.unregisterCustomToneChangeListener"))
    }

    @Test
    fun `toolbar opens account access instead of an AI panel`() {
        val feature = featureSource()
        val action = feature.substringAfter("fun onToolbarAction()")
            .substringBefore("fun dismissResponse()")

        assertTrue(action.contains("openAuth()"))
        assertTrue(action.contains("openDashboard()"))
        assertFalse(action.contains("AiUiState("))
    }

    @Test
    fun `every request loading shows the result skeleton and back control`() {
        val feature = featureSource()
        val keyboardScreen = projectRoot()
            .resolve("keyboard/ui/src/main/java/com/addiyon/keyboard/ui/KeyboardScreen.kt")
            .readText()

        assertTrue(feature.contains("contentOverlayVisible = uiState.isLoading"))
        assertFalse(feature.contains("contentDimmed ="))
        assertFalse(feature.contains("contentLoadingVisible ="))
        assertFalse(feature.contains("contentLoadingIndicator ="))
        assertFalse(feature.contains("AiLoadingIndicator"))
        assertTrue(feature.contains("AiResultsOverlay("))
        assertTrue(feature.contains("uiState.isLoading ||"))
        assertFalse(feature.contains("AiPanel("))
        assertFalse(feature.contains("panelVisible ="))
        assertTrue(keyboardScreen.contains("if (optionalUi.contentOverlayVisible)"))
        assertTrue(keyboardScreen.contains("optionalUi.contentOverlay()"))
        assertTrue(keyboardScreen.contains("return@keyboardContent"))
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
    fun `phrase completion is unreachable and editor text controls tone actions`() {
        val feature = featureSource()
        val service = serviceSource()

        assertTrue(feature.contains("AiToneRow("))
        assertTrue(feature.contains("enabled = toneActionsEnabled"))
        assertTrue(feature.contains("private fun onKeyboardToneSelected"))
        assertTrue(feature.contains("private fun refreshToneActionsEnabled"))
        assertTrue(feature.contains("controller.captureInput().text.isNotBlank()"))
        assertFalse(feature.contains("AiCompletionController"))
        assertFalse(feature.contains("AiCompletionBar"))
        assertFalse(feature.contains("createCompletion("))
        assertTrue(feature.contains("store.setPhraseCompletionsEnabled(false)"))
        assertFalse(service.contains("AiCompletionFieldPolicy"))
        assertFalse(service.contains("onOptionalFeatureTextCommitted"))
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
