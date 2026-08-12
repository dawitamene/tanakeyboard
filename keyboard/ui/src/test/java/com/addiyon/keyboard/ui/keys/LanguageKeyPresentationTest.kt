package com.addiyon.keyboard.ui.keys

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageKeyPresentationTest {
    @Test fun `three-pack presentation shows active language and next destination`() {
        val labels = listOf("ሀለ", "AB", "AO")

        assertEquals(
            LanguageKeyPresentation("ሀለ", "AB"),
            LanguageKeyPresentation.cycling(labels, 0)
        )
        assertEquals(
            LanguageKeyPresentation("AB", "AO"),
            LanguageKeyPresentation.cycling(labels, 1)
        )
        assertEquals(
            LanguageKeyPresentation("AO", "ሀለ"),
            LanguageKeyPresentation.cycling(labels, 2)
        )
    }

    @Test fun `next-IME behavior uses the system switcher presentation`() {
        assertEquals(
            LanguageKeyPresentation("", "", switchesInputMethod = true),
            LanguageKeyPresentation.systemSwitcher()
        )
    }
}
