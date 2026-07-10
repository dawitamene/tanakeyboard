package com.addiyon.keyboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.addiyon.keyboard.ui.theme.KeyboardPalette

/**
 * Persisted user preferences for the keyboard, backed by SharedPreferences
 * so both the settings UI and [com.addiyon.keyboard.AddiyonKeyboardService]
 * can read the same values. Keys are stable strings, not enum ordinals.
 */
object KeyboardPrefs {
    private const val FILE = "addiyon_keyboard_prefs"
    const val KEY_VIBRATE = "vibrate_on_keypress"
    const val KEY_SOUND = "sound_on_keypress"
    const val KEY_PALETTE = "palette"
    const val KEY_NUMBER_ROW = "number_row"
    const val KEY_RECENT_EMOJIS = "recent_emojis"
    const val KEY_EMOJI_SKIN_TONES = "emoji_skin_tones"

    /** Exposed so the service can register an OnSharedPreferenceChangeListener. */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun vibrateOnKeypress(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE, false)

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

    fun numberRow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NUMBER_ROW, false)

    fun setNumberRow(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_NUMBER_ROW, value).apply()

    // The two emoji values are opaque encoded strings; their codecs live in
    // the pure-Kotlin stores (RecentEmojiStore / SkinToneStore), which take
    // these as load/save lambdas so they stay JVM-testable.
    fun recentEmojis(context: Context): String? =
        prefs(context).getString(KEY_RECENT_EMOJIS, null)

    fun setRecentEmojis(context: Context, value: String) =
        prefs(context).edit().putString(KEY_RECENT_EMOJIS, value).apply()

    fun emojiSkinTones(context: Context): String? =
        prefs(context).getString(KEY_EMOJI_SKIN_TONES, null)

    fun setEmojiSkinTones(context: Context, value: String) =
        prefs(context).edit().putString(KEY_EMOJI_SKIN_TONES, value).apply()
}
