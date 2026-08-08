package com.addiyon.keyboard.ai

import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestLifecycleContractTest {
    @Test
    fun `tone selection captures current editor text and starts a fresh request`() {
        val service = serviceSource()
        val selection = service.substringAfter("fun onAiTabSelected(tab: AiToneTab)")
            .substringBefore("fun onAiStrengthSelected")

        assertTrue(selection.contains("aiRequestJob?.cancel()"))
        assertTrue(selection.contains("val input = aiController.captureInput()"))
        assertTrue(selection.contains("isLoading = true"))
        assertTrue(selection.contains("aiController.revampAll(input, tab)"))
        assertFalse(selection.contains("val input = aiUiState.input"))
    }

    @Test
    fun `opening the panel clears the selected tone`() {
        val service = serviceSource()
        val open = service.substringAfter("fun onAiAction()")
            .substringBefore("fun openAiDashboard()")

        assertTrue(open.contains("selectedTab = null"))
    }

    private fun serviceSource(): String = projectRoot()
        .resolve("app/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt")
        .readText()

    private fun projectRoot(): File {
        val start = Paths.get(System.getProperty("user.dir")).toFile().canonicalFile
        return generateSequence(start) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
