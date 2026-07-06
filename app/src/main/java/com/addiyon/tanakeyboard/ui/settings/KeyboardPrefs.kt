package com.addiyon.tanakeyboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.addiyon.tanakeyboard.ui.theme.KeyboardPalette

/**
 * Persisted user preferences for the keyboard, backed by SharedPreferences
 * so both the settings UI and [com.addiyon.tanakeyboard.TanaKeyboardService]
 * can read the same values. Keys are stable strings, not enum ordinals.
 */
object KeyboardPrefs {
    private const val FILE = "tana_keyboard_prefs"
    const val KEY_VIBRATE = "vibrate_on_keypress"
    const val KEY_SOUND = "sound_on_keypress"
    const val KEY_PALETTE = "palette"

    /** Exposed so the service can register an OnSharedPreferenceChangeListener. */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun vibrateOnKeypress(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE, true)

    fun soundOnKeypress(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, false)

    fun setVibrateOnKeypress(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_VIBRATE, value).apply()

    fun setSoundOnKeypress(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SOUND, value).apply()

    fun palette(context: Context): KeyboardPalette =
        KeyboardPalette.fromId(prefs(context).getString(KEY_PALETTE, KeyboardPalette.CLASSIC.id))

    fun setPalette(context: Context, value: KeyboardPalette) =
        prefs(context).edit().putString(KEY_PALETTE, value.id).apply()
}
