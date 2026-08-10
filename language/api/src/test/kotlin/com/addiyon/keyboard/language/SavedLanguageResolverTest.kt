package com.addiyon.keyboard.language

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedLanguageResolverTest {
    private val installed = setOf("am-ET", "en-US")

    @Test
    fun explicitInstalledIdWinsOverLegacyBoolean() {
        assertEquals("en-US", SavedLanguageResolver.resolve("en-US", true, installed, "am-ET"))
    }

    @Test
    fun legacyBooleanMigratesBothStates() {
        assertEquals("am-ET", SavedLanguageResolver.resolve(null, true, installed, "en-US"))
        assertEquals("en-US", SavedLanguageResolver.resolve(null, false, installed, "am-ET"))
    }

    @Test
    fun unknownOrMalformedIdFallsBackSafely() {
        assertEquals("am-ET", SavedLanguageResolver.resolve("om-ET", null, installed, "am-ET"))
        assertEquals("am-ET", SavedLanguageResolver.resolve("bad id", null, installed, "am-ET"))
    }
}
