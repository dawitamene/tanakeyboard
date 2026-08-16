package com.addiyon.keyboard.ai

import androidx.compose.ui.graphics.Color
import com.addiyon.keyboard.ui.design.AddiyonBrand
import com.addiyon.keyboard.ui.design.addiyonDarkColors
import com.addiyon.keyboard.ui.design.addiyonLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomToneAppearanceTest {
    private val light = addiyonLightColors(AddiyonBrand(Color.Black))
    private val dark = addiyonDarkColors(AddiyonBrand(Color.Black))

    @Test
    fun `every custom tone color id has a light and dark token`() {
        CustomToneColor.All.forEach { id ->
            assertTrue("missing light token for $id", id in light.aiCustomToneColors)
            assertTrue("missing dark token for $id", id in dark.aiCustomToneColors)
        }
    }

    @Test
    fun `custom tone colors are distinct within each appearance`() {
        assertEquals(CustomToneColor.All.size, light.aiCustomToneColors.values.toSet().size)
        assertEquals(CustomToneColor.All.size, dark.aiCustomToneColors.values.toSet().size)
    }

    @Test
    fun `custom tone colors differ between light and dark appearances`() {
        assertNotEquals(light.aiCustomToneColors, dark.aiCustomToneColors)
    }
}
