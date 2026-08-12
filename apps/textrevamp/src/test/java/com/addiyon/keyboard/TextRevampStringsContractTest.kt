package com.addiyon.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRevampStringsContractTest {
    @Test
    fun everyEnglishStringIsNonBlank() {
        stringValues(EnglishTextRevampStrings).forEach { (name, value) ->
            assertTrue("English $name must not be blank", value.isNotBlank())
        }
    }

    @Test
    fun englishTemplatesKeepTheirPlaceholderContracts() {
        val english = stringValues(EnglishTextRevampStrings)

        assertEquals(listOf("%d", "%d"), placeholders(english.getValue("aiQuotaFormat")))
    }

    @Test
    fun tableContainsOnlyAiFeatureCopy() {
        stringValues(EnglishTextRevampStrings).keys.forEach { name ->
            assertTrue(
                "$name belongs in shared app-shell resources",
                name.startsWith("ai")
            )
        }
    }

    private fun stringValues(strings: TextRevampStrings): Map<String, String> =
        TextRevampStrings::class.java.declaredMethods
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
