package com.addiyon.keyboard

/**
 * Maps an InputMethodSubtype's declared language onto the service's
 * [AddiyonKeyboardService.isAmharic] flag, so the system language switcher and
 * the in-keyboard globe key can't disagree about which language is active.
 *
 * Pure functions over the subtype's *string* language identifiers rather than
 * over an actual InputMethodSubtype, so the mapping is JVM-testable without an
 * Android framework object (same rationale as [InputTypePolicy]). The caller
 * reads `languageTag` (API 24+) and the legacy `locale` field off the subtype
 * and hands both here.
 *
 * The two subtypes declared in `res/xml/method.xml` are the only ones this
 * keyboard ships, but a null/blank/unrecognized value is treated as "no
 * opinion" (null) rather than as English: an unknown subtype must leave the
 * user's chosen language alone, not silently flip them out of Amharic.
 */
internal object SubtypeLanguagePolicy {

    /**
     * True to switch to Amharic, false to switch to English, null to leave the
     * current language untouched.
     *
     * [languageTag] is a BCP-47 tag ("am", "am-ET", "en-US"); [locale] is the
     * older underscore-separated form ("am_ET"). The tag wins when both are
     * present, matching the platform's own precedence.
     */
    fun selectsAmharic(languageTag: String?, locale: String?): Boolean? =
        fromLanguageCode(languageTag) ?: fromLanguageCode(locale)

    /**
     * Reads the primary language subtag off either identifier form and matches
     * it against the two languages this keyboard types. Amharic is matched by
     * language alone (not region), so "am", "am-ET" and "am_ET" all resolve --
     * a subtype for Amharic outside Ethiopia is still Amharic.
     */
    private fun fromLanguageCode(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        val language = value.trim()
            .substringBefore('-')
            .substringBefore('_')
            .lowercase()
        return when (language) {
            "am" -> true
            "en" -> false
            else -> null
        }
    }
}
