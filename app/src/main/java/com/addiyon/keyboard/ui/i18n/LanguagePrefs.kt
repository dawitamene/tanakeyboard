package com.addiyon.keyboard.ui.i18n

import android.content.Context

/**
 * Persists the app UI language across launches, in its own SharedPreferences
 * file (separate from the keyboard's [com.addiyon.keyboard.ui.settings.KeyboardPrefs]).
 * Read by every localized entry point on start so a language chosen from the
 * Settings toggle also applies to the Activities the keyboard toolbar opens.
 */
object LanguagePrefs {
    private const val PREFS = "addiyon_language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun language(context: Context): AppLanguage {
        val code = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.ENGLISH
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }
}
