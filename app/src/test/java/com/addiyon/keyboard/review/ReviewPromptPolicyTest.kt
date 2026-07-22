package com.addiyon.keyboard.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {

    @Test
    fun `no prompt before enough sessions`() {
        assertFalse(ReviewPromptPolicy.shouldPrompt(sessions = 0, alreadyPrompted = false))
        assertFalse(
            ReviewPromptPolicy.shouldPrompt(
                sessions = ReviewPromptPolicy.MIN_SESSIONS - 1, alreadyPrompted = false
            )
        )
    }

    @Test
    fun `prompts once threshold reached`() {
        assertTrue(
            ReviewPromptPolicy.shouldPrompt(
                sessions = ReviewPromptPolicy.MIN_SESSIONS, alreadyPrompted = false
            )
        )
        assertTrue(ReviewPromptPolicy.shouldPrompt(sessions = 999, alreadyPrompted = false))
    }

    @Test
    fun `never prompts twice`() {
        assertFalse(
            ReviewPromptPolicy.shouldPrompt(
                sessions = ReviewPromptPolicy.MIN_SESSIONS, alreadyPrompted = true
            )
        )
    }

    @Test
    fun `counting stops at the cap`() {
        assertTrue(ReviewPromptPolicy.shouldCount(0))
        assertTrue(ReviewPromptPolicy.shouldCount(ReviewPromptPolicy.COUNT_CAP - 1))
        assertFalse(ReviewPromptPolicy.shouldCount(ReviewPromptPolicy.COUNT_CAP))
    }

    @Test
    fun `cap still allows the prompt to fire`() {
        // The counter can always reach MIN_SESSIONS before counting stops.
        assertTrue(ReviewPromptPolicy.COUNT_CAP >= ReviewPromptPolicy.MIN_SESSIONS)
    }
}
