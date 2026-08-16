package com.addiyon.keyboard.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUiStateTest {
    @Test
    fun `new keyboard AI state has no selected tone`() {
        val state = AiUiState(
            input = AiInput("Text to rewrite", 3, AiSource.Sentence, null)
        )

        assertNull(state.selectedTab)
        assertFalse(state.canRevamp)
    }

    @Test
    fun `valid selected tone enables request`() {
        val state = AiUiState(
            selectedTab = AiToneTab.Professional,
            input = AiInput("Text to rewrite", 3, AiSource.Sentence, null)
        )

        assertTrue(state.canRevamp)
    }

    @Test
    fun `selected custom tone enables request without a built-in tab`() {
        val state = AiUiState(
            selectedCustomToneId = "custom-1",
            input = AiInput("Text to rewrite", 3, AiSource.Sentence, null)
        )

        assertTrue(state.canRevamp)
        assertNull(state.selectedTab)
    }
}
