package com.addiyon.keyboard

import com.addiyon.keyboard.language.LanguageId
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.language.android.AndroidLanguagePackEnvironment
import com.addiyon.keyboard.language.android.AndroidLanguagePackProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanguagePackProviderResolutionTest {
    @Test
    fun providersFollowProductLanguageOrder() {
        val english = FakeProvider("en-US")
        val amharic = FakeProvider("am-ET")

        val result = resolveConfiguredLanguagePackProviders(
            orderedLanguageIds = listOf("am-ET", "en-US"),
            providers = listOf(english, amharic)
        )

        assertEquals(listOf(amharic, english), result)
    }

    @Test
    fun duplicateProviderIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveConfiguredLanguagePackProviders(
                orderedLanguageIds = listOf("en-US"),
                providers = listOf(FakeProvider("en-US"), FakeProvider("en-US"))
            )
        }
    }

    @Test
    fun missingOrExtraProvidersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveConfiguredLanguagePackProviders(
                orderedLanguageIds = listOf("en-US"),
                providers = listOf(FakeProvider("om-ET"))
            )
        }
    }

    private data class FakeProvider(private val id: String) : AndroidLanguagePackProvider {
        override val languageId: LanguageId = LanguageId.of(id)

        override fun create(environment: AndroidLanguagePackEnvironment): LanguagePack =
            error("Provider construction is not used by resolution tests")
    }
}
