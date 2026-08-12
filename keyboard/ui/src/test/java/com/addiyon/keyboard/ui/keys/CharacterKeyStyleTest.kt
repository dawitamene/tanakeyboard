package com.addiyon.keyboard.ui.keys

import com.addiyon.keyboard.model.KeyData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterKeyStyleTest {

    @Test
    fun atSignUsesTheRegularSymbolBackground() {
        assertFalse(KeyData.Character("@").usesSpecialBackground())
        assertFalse(KeyData.Character("#").usesSpecialBackground())
    }

    @Test
    fun punctuationAndExplicitSpecialKeysKeepTheSpecialBackground() {
        assertTrue(KeyData.Character(",").usesSpecialBackground())
        assertTrue(KeyData.Character(".").usesSpecialBackground())
        assertTrue(KeyData.Character("@", isSpecial = true).usesSpecialBackground())
    }
}
