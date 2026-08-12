package com.addiyon.keyboard.language

import com.addiyon.keyboard.composing.TypingProfile
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.suggestion.CompletionQuery
import com.addiyon.keyboard.suggestion.EngineSuggestion
import com.addiyon.keyboard.suggestion.LanguageSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRegistryTest {
    @Test
    fun unknownSavedIdFallsBackToTheConfiguredDefault() {
        val registry = LanguageRegistry(listOf(pack("am-ET"), pack("en-US")), LanguageId.of("am-ET"))
        assertEquals(LanguageId.of("am-ET"), registry.restore("om-ET"))
    }

    @Test
    fun onePackRegistryDoesNotPretendToSwitch() {
        val registry = LanguageRegistry(listOf(pack("en-US")), LanguageId.of("en-US"))
        assertFalse(registry.activateNext())
        assertEquals(LanguageId.of("en-US"), registry.activeLanguageId)
    }

    @Test
    fun multiPackSwitchRunsLifecycleCallbacksInOrder() {
        val registry = LanguageRegistry(listOf(pack("am-ET"), pack("en-US")), LanguageId.of("am-ET"))
        val events = mutableListOf<String>()
        assertTrue(registry.activateNext(
            beforeChange = { events += "out:${it.id}" },
            afterChange = { events += "in:${it.id}" }
        ))
        assertEquals(listOf("out:am-ET", "in:en-US"), events)
    }

    @Test
    fun subtypeLocaleTagsResolveExactUnderscoreAndPrimaryLanguageMatches() {
        val registry = LanguageRegistry(
            listOf(pack("am-ET"), pack("en-US"), pack("om-ET")),
            LanguageId.of("am-ET")
        )

        assertEquals(LanguageId.of("om-ET"), registry.findByLocaleTag("om-ET"))
        assertEquals(LanguageId.of("en-US"), registry.findByLocaleTag("en_US"))
        assertEquals(LanguageId.of("am-ET"), registry.findByLocaleTag("am"))
        assertEquals(null, registry.findByLocaleTag("fr-FR"))
    }

    private fun pack(value: String): LanguagePack = object : LanguagePack {
        override val id = LanguageId.of(value)
        override val localeTag = value
        override val displayName = value
        override val letterLayout = KeyboardLayout(emptyList())
        override val typingProfile = TypingProfile(isWordCharacter = { true })
        override val capabilities = LanguageCapabilities(true)
        override val voiceLocaleTag: String? = null
        override val presentationCategory = LanguagePresentationCategory.OTHER
        override val suggestionEngine = object : LanguageSuggestionEngine {
            override val languageId = value
            override val isReady = true
            override val isLoading = false
            override fun loadAsync(onReady: () -> Unit) = onReady()
            override fun complete(query: CompletionQuery) = emptyList<String>()
            override fun predict(prev2: String?, prev1: String, limit: Int) = emptyList<EngineSuggestion>()
            override fun topFrequentWords(limit: Int) = emptyList<EngineSuggestion>()
            override fun normalize(word: String) = word
            override fun clearCaches() = Unit
            override fun release() = Unit
        }
        override val contextReader: (CharSequence?) -> LanguageContext = {
            LanguageContext(null, null)
        }
    }
}
