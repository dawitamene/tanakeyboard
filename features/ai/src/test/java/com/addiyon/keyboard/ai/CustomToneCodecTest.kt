package com.addiyon.keyboard.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomToneCodecTest {
    @Test
    fun `encode and decode round trips custom tones in order`() {
        val tones = listOf(
            CustomTone("id-1", "Poetic", "Make it more poetic", CustomToneIcon.PALETTE, CustomToneColor.PURPLE),
            CustomTone("id-2", "Pirate", "Sound like a pirate", CustomToneIcon.ROCKET, CustomToneColor.ORANGE)
        )

        val raw = encodeCustomTones(tones)

        assertEquals(tones, decodeCustomTones(raw))
    }

    @Test
    fun `encode uses the default icon and color when omitted`() {
        val tones = listOf(CustomTone("id-1", "Poetic", "Make it more poetic"))

        val decoded = decodeCustomTones(encodeCustomTones(tones))

        assertEquals(CustomToneIcon.Default, decoded.single().icon)
        assertEquals(CustomToneColor.Default, decoded.single().color)
    }

    @Test
    fun `decode tolerates blank and malformed records`() {
        assertEquals(emptyList<CustomTone>(), decodeCustomTones(null))
        assertEquals(emptyList<CustomTone>(), decodeCustomTones(""))
        assertEquals(emptyList<CustomTone>(), decodeCustomTones("only-an-id"))
        assertEquals(
            listOf(CustomTone("a", "b", "c", CustomToneIcon.FACE, CustomToneColor.GREEN)),
            decodeCustomTones("a\u0001b\u0001c\u0001face\u0001green\u001Fbad-record")
        )
    }

    @Test
    fun `decode falls back for legacy records without icon and color`() {
        assertEquals(
            listOf(CustomTone("a", "Poetic", "Make it poetic")),
            decodeCustomTones("a\u0001Poetic\u0001Make it poetic")
        )
        assertEquals(
            listOf(CustomTone("a", "Make it poetic", "Make it poetic")),
            decodeCustomTones("a\u0001Make it poetic")
        )
    }

    @Test
    fun `decode sanitizes unknown icons to the default`() {
        val decoded = decodeCustomTones("a\u0001Title\u0001Instruction\u0001nope\u0001nope")

        assertEquals(CustomToneIcon.Default, decoded.single().icon)
        assertEquals(CustomToneColor.Default, decoded.single().color)
    }

    @Test
    fun `decode maps interim emoji glyph icons to the default`() {
        val decoded = decodeCustomTones("a\u0001Title\u0001Instruction\u0001\uD83D\uDE00\u0001teal")

        assertEquals(CustomToneIcon.Default, decoded.single().icon)
        assertEquals(CustomToneColor.TEAL, decoded.single().color)
    }

    @Test
    fun `encode of an empty list round trips to an empty list`() {
        assertEquals("", encodeCustomTones(emptyList()))
        assertEquals(emptyList<CustomTone>(), decodeCustomTones(encodeCustomTones(emptyList())))
    }
}
