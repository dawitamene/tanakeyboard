package com.addiyon.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSearchTest {

    private fun emoji(base: String, name: String, keywords: String) =
        "E\t$base\t$name\t$keywords\t"

    private val data = EmojiData.parse(
        listOf(
            "G\tSmileys & Emotion",
            emoji("😀", "grinning face", "face,grin,happy"),
            emoji("😿", "crying cat", "cat,cry,sad,tear"),
            "G\tAnimals & Nature",
            emoji("🐱", "cat face", "cat,face,pet"),
            emoji("🐈", "cat", "cat,pet"),
            emoji("🐶", "dog face", "dog,face,pet")
        ).asSequence()
    )

    @Test
    fun `name prefix matches rank before keyword matches`() {
        // "cat face" and "cat" start with the query; "crying cat" only
        // matches via keyword/substring.
        assertEquals(listOf("🐱", "🐈", "😿"), data.search("cat").map { it.base })
    }

    @Test
    fun `search is case-insensitive`() {
        assertEquals(data.search("cat"), data.search("CaT"))
    }

    @Test
    fun `keyword-only matches are found`() {
        assertEquals(listOf("😀"), data.search("happy").map { it.base })
    }

    @Test
    fun `empty and blank queries match nothing`() {
        assertTrue(data.search("").isEmpty())
        assertTrue(data.search("   ").isEmpty())
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(data.search("zebra").isEmpty())
    }

    @Test
    fun `query is trimmed`() {
        assertEquals(data.search("cat"), data.search(" cat "))
    }

    @Test
    fun `results are capped at limit`() {
        assertEquals(1, data.search("cat", limit = 1).size)
        // The single result must still be the best-ranked one.
        assertEquals("🐱", data.search("cat", limit = 1).single().base)
    }

    @Test
    fun `query cannot straddle name and keywords`() {
        // Haystack is "cryng cat<US>cat,cry,..." -- "cat cat" must not match
        // across the separator.
        assertTrue(data.search("cat cat").isEmpty())
    }
}
