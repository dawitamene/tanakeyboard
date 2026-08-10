package com.addiyon.keyboard.language

object SavedLanguageResolver {
    fun resolve(
        savedId: String?,
        legacyAmharicMode: Boolean?,
        installedIds: Set<String>,
        defaultId: String
    ): String {
        require(defaultId in installedIds)
        val explicit = savedId
            ?.let { runCatching { LanguageId.of(it).value }.getOrNull() }
            ?.takeIf(installedIds::contains)
        if (explicit != null) return explicit
        val migrated = legacyAmharicMode?.let { if (it) "am-ET" else "en-US" }
        return migrated?.takeIf(installedIds::contains) ?: defaultId
    }
}
