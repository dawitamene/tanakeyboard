package com.addiyon.keyboard.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUiStateTest {
    @Test
    fun `new panel state has no selected tone`() {
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
}
