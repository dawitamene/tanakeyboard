package com.addiyon.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinToneStoreTest {

    private var persisted: String? = null

    private fun store() = SkinToneStore(load = { persisted }, save = { persisted = it })

    @Test
    fun `starts empty with no persisted value`() {
        assertTrue(store().all().isEmpty())
    }

    @Test
    fun `remembers a tone per base`() {
        val s = store()
        s.set("👋", "👋🏿")
        s.set("👍", "👍🏽")
        assertEquals("👋🏿", s.all()["👋"])
        assertEquals("👍🏽", s.all()["👍"])
    }

    @Test
    fun `re-picking overwrites the previous tone`() {
        val s = store()
        s.set("👋", "👋🏿")
        s.set("👋", "👋🏻")
        assertEquals(mapOf("👋" to "👋🏻"), s.all())
    }

    @Test
    fun `picking the base clears the entry`() {
        val s = store()
        s.set("👋", "👋🏿")
        s.set("👋", "👋")
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `round-trips through persistence`() {
        store().apply {
            set("👋", "👋🏿")
            set("🤝", "🫱🏻‍🫲🏾")
        }
        val fresh = store().all()
        assertEquals("👋🏿", fresh["👋"])
        assertEquals("🫱🏻‍🫲🏾", fresh["🤝"])
    }

    @Test
    fun `tolerates corrupt persisted value`() {
        persisted = "notab\u001F\t\u001F👋\t👋🏿\u001F👍\t"
        assertEquals(mapOf("👋" to "👋🏿"), store().all())
    }

    @Test
    fun `rejects keys or values containing separators`() {
        val s = store()
        s.set("a\u001Fb", "x")
        s.set("👋", "x\ty")
        assertTrue(s.all().isEmpty())
        assertNull(persisted)
    }
}
