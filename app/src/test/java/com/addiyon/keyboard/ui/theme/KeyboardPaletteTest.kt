package com.addiyon.keyboard.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPaletteTest {

    @Test
    fun navigationIconsFollowActualTrayBrightness() {
        assertTrue(KeyboardPalette.CLASSIC.usesDarkNavigationIcons(isDark = false))
        assertFalse(KeyboardPalette.CLASSIC.usesDarkNavigationIcons(isDark = true))
        assertTrue(KeyboardPalette.BUBBLEGUM.usesDarkNavigationIcons(isDark = true))
        assertFalse(KeyboardPalette.MIDNIGHT.usesDarkNavigationIcons(isDark = false))
    }
}
