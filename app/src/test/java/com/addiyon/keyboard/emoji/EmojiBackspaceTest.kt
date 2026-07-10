package com.addiyon.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiBackspaceTest {

    private fun lastCluster(text: String) = EmojiBackspace.lastClusterLength(text)

    @Test
    fun `empty text is zero`() {
        assertEquals(0, lastCluster(""))
    }

    @Test
    fun `plain BMP letter deletes one char`() {
        assertEquals(1, lastCluster("abc"))
        assertEquals(1, lastCluster("ሰላም")) // Ge'ez, BMP
    }

    @Test
    fun `combining accent deletes just the mark`() {
        assertEquals(1, lastCluster("é")) // e + combining acute
    }

    @Test
    fun `basic non-BMP emoji deletes the surrogate pair`() {
        assertEquals(2, lastCluster("hi😀"))
    }

    @Test
    fun `skin-toned emoji deletes base plus modifier`() {
        assertEquals("👋🏾".length, lastCluster("a👋🏾"))
    }

    @Test
    fun `VS16 emoji deletes base plus selector`() {
        assertEquals("❤️".length, lastCluster("x❤️"))
    }

    @Test
    fun `ZWJ family deletes the whole sequence`() {
        val family = "👨‍👩‍👧‍👦"
        assertEquals(family.length, lastCluster("go$family"))
    }

    @Test
    fun `multi-person toned ZWJ combo deletes the whole sequence`() {
        // handshake: light skin tone, dark skin tone
        val handshake = "🫱🏻‍🫲🏿"
        assertEquals(handshake.length, lastCluster(handshake))
    }

    @Test
    fun `ZWJ with VS16 inside deletes the whole sequence`() {
        val eyeBubble = "👁️‍🗨️" // eye in speech bubble
        assertEquals(eyeBubble.length, lastCluster("a$eyeBubble"))
    }

    @Test
    fun `flag deletes both regional indicators`() {
        assertEquals("🇪🇹".length, lastCluster("from🇪🇹"))
    }

    @Test
    fun `adjacent flags delete one flag not both`() {
        assertEquals("🇪🇹".length, lastCluster("🇺🇸🇪🇹"))
    }

    @Test
    fun `odd regional indicator run deletes the dangling one`() {
        val ri = "🇪" // single regional indicator, 2 chars
        assertEquals(ri.length, lastCluster("🇺🇸$ri"))
    }

    @Test
    fun `keycap deletes digit plus selector plus combiner`() {
        val keycap = "1️⃣"
        assertEquals(keycap.length, lastCluster("a$keycap"))
    }

    @Test
    fun `emoji at start of text is fully consumed`() {
        assertEquals("😀".length, lastCluster("😀"))
        assertEquals("👋🏾".length, lastCluster("👋🏾"))
    }

    @Test
    fun `lone ZWJ deletes just itself`() {
        assertEquals(1, lastCluster("a‍"))
    }
}
