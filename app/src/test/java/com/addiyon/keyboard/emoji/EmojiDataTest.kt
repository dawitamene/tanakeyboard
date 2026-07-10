package com.addiyon.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {

    private val fixture = """
        V	1	16.0
        G	Smileys & Emotion
        E	😀	grinning face	face,grin,happy${"\t"}
        E	😺	grinning cat	cat,face,grin${"\t"}
        G	People & Body
        E	👋	waving hand	hand,wave	👋🏻 👋🏼 👋🏽 👋🏾 👋🏿
        G	Flags
        E	🏁	chequered flag	checkered,finish,race${"\t"}
    """.trimIndent()

    private fun parse(
        text: String = fixture,
        isRenderable: (String) -> Boolean = { true }
    ) = EmojiData.parse(text.lineSequence(), isRenderable)

    @Test
    fun `parses groups in order with their emoji`() {
        val data = parse()
        assertEquals(
            listOf("Smileys & Emotion", "People & Body", "Flags"),
            data.groups.map { it.name }
        )
        assertEquals(listOf("😀", "😺"), data.groups[0].emoji.map { it.base })
        assertEquals("grinning face", data.groups[0].emoji[0].name)
    }

    @Test
    fun `empty variants field yields no variants`() {
        val data = parse()
        assertTrue(data.groups[0].emoji[0].variants.isEmpty())
    }

    @Test
    fun `variants parse in order`() {
        val data = parse()
        val wave = data.groups[1].emoji.single()
        assertEquals(listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"), wave.variants)
    }

    @Test
    fun `unrenderable base is dropped`() {
        val data = parse(isRenderable = { it != "😺" })
        assertEquals(listOf("😀"), data.groups[0].emoji.map { it.base })
    }

    @Test
    fun `unrenderable variant is dropped but base kept`() {
        val data = parse(isRenderable = { it != "👋🏿" })
        val wave = data.groups[1].emoji.single()
        assertEquals("👋", wave.base)
        assertEquals(listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾"), wave.variants)
    }

    @Test
    fun `group with all emoji unrenderable is omitted`() {
        val data = parse(isRenderable = { it != "🏁" })
        assertEquals(listOf("Smileys & Emotion", "People & Body"), data.groups.map { it.name })
    }

    @Test
    fun `malformed lines are skipped without failing`() {
        val text = """
            V	1	16.0
            G	Smileys & Emotion
            E	😀	grinning face	face,grin,happy${"\t"}
            E	broken line with too few fields
            garbage that matches no record type
            E	😺	grinning cat	cat,face,grin${"\t"}
        """.trimIndent()
        val data = parse(text)
        assertEquals(listOf("😀", "😺"), data.groups.single().emoji.map { it.base })
    }

    @Test
    fun `emoji before any group header are ignored`() {
        val text = """
            E	😀	grinning face	face${"\t"}
            G	Smileys & Emotion
            E	😺	grinning cat	cat${"\t"}
        """.trimIndent()
        val data = parse(text)
        assertEquals(listOf("😺"), data.groups.single().emoji.map { it.base })
    }

    @Test
    fun `allEmoji flattens groups in display order`() {
        assertEquals(listOf("😀", "😺", "👋", "🏁"), parse().allEmoji.map { it.base })
    }
}
