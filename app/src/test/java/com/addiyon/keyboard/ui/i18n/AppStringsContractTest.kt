package com.addiyon.keyboard.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStringsContractTest {
    @Test
    fun everyLocalizedStringIsNonBlankInBothLanguages() {
        val english = stringValues(EnglishStrings)
        val amharic = stringValues(AmharicStrings)

        assertEquals(english.keys, amharic.keys)
        english.forEach { (name, value) ->
            assertTrue("English $name must not be blank", value.isNotBlank())
        }
        amharic.forEach { (name, value) ->
            assertTrue("Amharic $name must not be blank", value.isNotBlank())
        }
    }

    @Test
    fun localizedTemplatesKeepTheSamePlaceholderContract() {
        val english = stringValues(EnglishStrings)
        val amharic = stringValues(AmharicStrings)

        english.forEach { (name, value) ->
            assertEquals(
                "$name placeholder mismatch",
                placeholders(value),
                placeholders(amharic.getValue(name))
            )
        }
        assertEquals(listOf("%s"), placeholders(english.getValue("versionFormat")))
        assertEquals(listOf("%s"), placeholders(english.getValue("shareTextFormat")))
        assertEquals(listOf("%d"), placeholders(english.getValue("stepFormat")))
    }

    @Test
    fun languageCodesAndLabelsAreStableAndUnique() {
        assertEquals(listOf("en", "am"), AppLanguage.entries.map(AppLanguage::code))
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map(AppLanguage::code).toSet().size)
        AppLanguage.entries.forEach {
            assertTrue(it.code.isNotBlank())
            assertTrue(it.label.isNotBlank())
        }
    }

    @Test
    fun amharicTableContainsEthiopicTextBeyondSharedTechnicalExamples() {
        val sharedTechnicalValues = setOf(
            EnglishStrings.tourTypingExample,
            EnglishStrings.telegram
        )
        stringValues(AmharicStrings)
            .filterValues { it !in sharedTechnicalValues }
            .forEach { (name, value) ->
                assertTrue(
                    "Amharic $name must contain Ethiopic script",
                    value.any { it in '\u1200'..'\u137F' }
                )
            }
        assertFalse(AmharicStrings.aboutDescription == EnglishStrings.aboutDescription)
    }

    private fun stringValues(strings: AppStrings): Map<String, String> =
        AppStrings::class.java.declaredMethods
            .asSequence()
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == String::class.java &&
                    it.name.startsWith("get")
            }
            .associate { method ->
                method.name.removePrefix("get").replaceFirstChar(Char::lowercase) to
                    method.invoke(strings) as String
            }
            .toSortedMap()

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map(MatchResult::value).sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("%(?:\\d+\\$)?[sd]")
    }
}
