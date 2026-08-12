package com.addiyon.keyboard.language.oromo

import com.addiyon.keyboard.model.KeyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OromoLanguagePackTest {
    private val pack = OromoLanguagePack()

    @Test fun `pack exposes Oromo identity and shared Latin layout`() {
        assertEquals("om-ET", pack.id.value)
        assertEquals("Afaan Oromoo", pack.displayName)
        assertEquals(4, pack.letterLayout.rows.size)
        assertTrue(pack.letterLayout.rows.flatten().filterIsInstance<KeyData.Character>().any { it.latin == "Q" })
    }

    @Test fun `apostrophes remain inside Oromo compositions`() {
        assertTrue(pack.typingProfile.isWordCharacter("'"))
        assertTrue(pack.typingProfile.isWordCharacter("’"))
        assertFalse(pack.typingProfile.isWordCharacter("-"))
    }

    @Test fun `unlicensed dictionary source publishes no fabricated suggestions`() {
        assertFalse(pack.capabilities.providesSuggestions)
        assertTrue(pack.suggestionEngine.complete(com.addiyon.keyboard.suggestion.CompletionQuery("bar")).isEmpty())
    }
}
