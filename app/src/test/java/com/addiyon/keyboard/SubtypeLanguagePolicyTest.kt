package com.addiyon.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks in the system-subtype -> active-language mapping that
 * AddiyonKeyboardService.onCurrentInputMethodSubtypeChanged applies when the
 * user picks a language from the system switcher.
 *
 * The load-bearing case is [unrecognizedSubtypeLeavesLanguageAlone]: an
 * unknown or absent subtype must return null so the service leaves isAmharic
 * untouched. Returning false there would flip an Amharic user to English on a
 * spurious callback, and because Amharic composition is discard-on-exit, the
 * composer commit on the way through would take their uncommitted word with
 * it.
 */
class SubtypeLanguagePolicyTest {

    @Test
    fun amharicLanguageTagSelectsAmharic() {
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("am-ET", null))
    }

    @Test
    fun englishLanguageTagSelectsEnglish() {
        assertEquals(false, SubtypeLanguagePolicy.selectsAmharic("en-US", null))
    }

    @Test
    fun bareLanguageSubtagResolvesWithoutRegion() {
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("am", null))
        assertEquals(false, SubtypeLanguagePolicy.selectsAmharic("en", null))
    }

    @Test
    fun amharicOutsideEthiopiaIsStillAmharic() {
        // Region is deliberately not part of the match -- an "am-US" diaspora
        // subtype types the same script.
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("am-US", null))
    }

    @Test
    fun legacyUnderscoreLocaleIsReadWhenTagIsAbsent() {
        // languageTag is empty on subtypes created the pre-API-24 way; the
        // platform still populates the deprecated locale field.
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic(null, "am_ET"))
        assertEquals(false, SubtypeLanguagePolicy.selectsAmharic("", "en_US"))
    }

    @Test
    fun languageTagWinsOverLegacyLocale() {
        assertEquals(false, SubtypeLanguagePolicy.selectsAmharic("en-US", "am_ET"))
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("am-ET", "en_US"))
    }

    @Test
    fun unrecognizedSubtypeLeavesLanguageAlone() {
        assertNull(SubtypeLanguagePolicy.selectsAmharic(null, null))
        assertNull(SubtypeLanguagePolicy.selectsAmharic("", ""))
        assertNull(SubtypeLanguagePolicy.selectsAmharic("   ", null))
        assertNull(SubtypeLanguagePolicy.selectsAmharic("fr-FR", null))
        assertNull(SubtypeLanguagePolicy.selectsAmharic("ti-ET", null))
    }

    @Test
    fun unrecognizedTagFallsThroughToLegacyLocale() {
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("zz-ZZ", "am_ET"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertEquals(true, SubtypeLanguagePolicy.selectsAmharic("AM-ET", null))
        assertEquals(false, SubtypeLanguagePolicy.selectsAmharic("EN", null))
    }
}
