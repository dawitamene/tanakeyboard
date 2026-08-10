package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryAssetMetadataTest {
    @Test
    fun parsesGeneratedManifest() {
        val metadata = DictionaryAssetMetadata.read(
            """
            schemaVersion=3
            applicationId=1094992985
            amharic.db.length=42
            amharic.db.sha256=abc
            english.db.length=21
            english.db.sha256=def
            """.trimIndent().byteInputStream()
        )

        assertEquals(3, metadata.schemaVersion)
        assertEquals(1094992985, metadata.applicationId)
        assertEquals(42L, metadata.asset("amharic.db").length)
        assertEquals("def", metadata.asset("english.db").sha256)
    }
}
