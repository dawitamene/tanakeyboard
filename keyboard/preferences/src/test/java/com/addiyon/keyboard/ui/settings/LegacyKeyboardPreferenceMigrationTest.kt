package com.addiyon.keyboard.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyKeyboardPreferenceMigrationTest {
    @Test fun `copies supported legacy values without replacing shared values`() {
        val copied = LegacyKeyboardPreferenceMigration.valuesToCopy(
            currentKeys = setOf(KeyboardPrefs.KEY_PALETTE),
            legacyValues = mapOf(
                KeyboardPrefs.KEY_PALETTE to "forest",
                KeyboardPrefs.KEY_VIBRATE to false,
                KeyboardPrefs.KEY_ACTIVE_LANGUAGE_ID to "om-ET",
                "unknown_key" to "ignored"
            )
        )

        assertFalse(KeyboardPrefs.KEY_PALETTE in copied)
        assertEquals(false, copied[KeyboardPrefs.KEY_VIBRATE])
        assertEquals("om-ET", copied[KeyboardPrefs.KEY_ACTIVE_LANGUAGE_ID])
        assertFalse("unknown_key" in copied)
    }
}
