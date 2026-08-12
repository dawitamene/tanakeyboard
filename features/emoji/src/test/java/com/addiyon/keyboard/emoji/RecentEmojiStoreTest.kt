package com.addiyon.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEmojiStoreTest {

    private var persisted: String? = null

    private fun store(capacity: Int = RecentEmojiStore.DEFAULT_CAPACITY) =
        RecentEmojiStore(load = { persisted }, save = { persisted = it }, capacity = capacity)

    @Test
    fun `starts empty with no persisted value`() {
        assertTrue(store().snapshot().isEmpty())
    }

    @Test
    fun `records most recent first`() {
        val s = store()
        s.recordUse("😀")
        s.recordUse("🤣")
        s.recordUse("👋")
        assertEquals(listOf("👋", "🤣", "😀"), s.snapshot())
    }

    @Test
    fun `re-use moves an emoji to the front without duplicating`() {
        val s = store()
        s.recordUse("😀")
        s.recordUse("🤣")
        s.recordUse("😀")
        assertEquals(listOf("😀", "🤣"), s.snapshot())
    }

    @Test
    fun `evicts least recent beyond capacity`() {
        val s = store(capacity = 3)
        s.recordUse("a")
        s.recordUse("b")
        s.recordUse("c")
        s.recordUse("d")
        assertEquals(listOf("d", "c", "b"), s.snapshot())
    }

    @Test
    fun `round-trips through persistence`() {
        store().apply {
            recordUse("😀")
            recordUse("👋🏿")
        }
        // A fresh store instance over the same persisted value.
        assertEquals(listOf("👋🏿", "😀"), store().snapshot())
    }

    @Test
    fun `tolerates corrupt persisted value`() {
        persisted = "\u001F\u001F😀\u001F"
        assertEquals(listOf("😀"), store().snapshot())
    }

    @Test
    fun `caps decoded entries at capacity`() {
        persisted = listOf("a", "b", "c", "d", "e").joinToString("\u001F")
        assertEquals(listOf("a", "b", "c"), store(capacity = 3).snapshot())
    }

    @Test
    fun `ignores empty and separator-containing input`() {
        val s = store()
        s.recordUse("")
        s.recordUse("x\u001Fy")
        assertTrue(s.snapshot().isEmpty())
        assertEquals(null, persisted)
    }

    @Test
    fun `snapshot is a stable copy`() {
        val s = store()
        s.recordUse("😀")
        val snap = s.snapshot()
        s.recordUse("🤣")
        assertEquals(listOf("😀"), snap)
    }
}
