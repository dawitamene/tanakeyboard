package com.addiyon.keyboard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptPolicyTest {

    private fun shouldPrompt(
        updateAvailable: Boolean = true,
        flexibleAllowed: Boolean = true,
        alreadyPrompted: Boolean = false
    ) = UpdatePromptPolicy.shouldPrompt(
        updateAvailable = updateAvailable,
        flexibleAllowed = flexibleAllowed,
        alreadyPrompted = alreadyPrompted
    )

    @Test
    fun `every available update prompts`() {
        // No minor-update tier: availability alone is enough.
        assertTrue(shouldPrompt())
    }

    @Test
    fun `no prompt without an available update`() {
        assertFalse(shouldPrompt(updateAvailable = false))
    }

    @Test
    fun `no prompt when Play disallows the flexible flow`() {
        // Skips rather than escalating to the forced immediate flow, so the
        // prompt the user gets is always one they can close.
        assertFalse(shouldPrompt(flexibleAllowed = false))
    }

    @Test
    fun `only one prompt per app open`() {
        // The loop guard: Play's sheet returning control triggers onResume,
        // which must not relaunch the flow the user just closed.
        assertFalse(shouldPrompt(alreadyPrompted = true))
    }

    @Test
    fun `closing the sheet does not suppress the next app open`() {
        // Nothing is persisted, so the next open starts un-prompted again and
        // asks once more -- the "every time they open the app" requirement.
        assertTrue(shouldPrompt(alreadyPrompted = false))
    }
}
