package com.addiyon.keyboard.ui.settings

import android.content.Context
import com.addiyon.keyboard.ui.i18n.AppLanguage

object LanguagePrefs {
    private const val PREFS = "addiyon_language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun language(context: Context): AppLanguage {
        val preferences = preferences(context)
        val raw = try {
            preferences.all[KEY_LANGUAGE]
        } catch (_: Throwable) {
            null
        }
        val code = PreferenceValueSanitizer.string(raw, null, 16)
        val language = AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.ENGLISH
        if (raw != null && (raw !is String || raw != language.code)) {
            try {
                preferences.edit().putString(KEY_LANGUAGE, language.code).apply()
            } catch (_: Throwable) {
            }
        }
        return language
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        try {
            preferences(context).edit().putString(KEY_LANGUAGE, language.code).apply()
        } catch (_: Throwable) {
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
