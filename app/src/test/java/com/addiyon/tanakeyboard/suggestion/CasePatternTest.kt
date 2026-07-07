package com.addiyon.tanakeyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class CasePatternTest {

    @Test
    fun lowercasePrefixLeavesWordLowercase() {
        assertEquals("that", matchCase("th", "that"))
    }

    @Test
    fun capitalizedPrefixTitleCasesWord() {
        assertEquals("That", matchCase("Th", "that"))
    }

    @Test
    fun singleUppercaseLetterMeansTitleCaseNotAllCaps() {
        assertEquals("That", matchCase("T", "that"))
    }

    @Test
    fun multiLetterAllCapsPrefixUppercasesWord() {
        assertEquals("THAT", matchCase("TH", "that"))
    }

    @Test
    fun apostropheDoesNotBreakAllCapsDetection() {
        assertEquals("DON'T", matchCase("DON'", "don't"))
    }

    @Test
    fun apostropheAloneWithSingleCapitalIsTitleCase() {
        // "I'" is one uppercase letter plus punctuation -- still the
        // "shifted first letter" case, not an all-caps request.
        assertEquals("I'm", matchCase("I'", "i'm"))
    }

    @Test
    fun emptyPrefixLeavesWordUntouched() {
        assertEquals("that", matchCase("", "that"))
    }

    @Test
    fun lowercasePrefixKeepsCanonicalProperNounCasing() {
        // The dictionary word already carries proper-noun casing; a lowercase
        // prefix must not lowercase it back down.
        assertEquals("England", matchCase("engl", "England"))
    }

    @Test
    fun allCapsPrefixOverridesCanonicalCasing() {
        assertEquals("ENGLAND", matchCase("ENG", "England"))
    }

    @Test
    fun titleCasePrefixLeavesAlreadyCapitalizedWordUnchanged() {
        assertEquals("England", matchCase("Eng", "England"))
    }
}
