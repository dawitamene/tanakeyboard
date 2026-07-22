package com.addiyon.keyboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.addiyon.keyboard.review.ReviewPromptPolicy
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MAX
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MIN
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
    const val KEY_KEYBOARD_HEIGHT_SCALE = "keyboard_height_scale"
    const val KEY_AMHARIC_MODE = "amharic_mode"
    const val KEY_RECENT_EMOJIS = "recent_emojis"
    const val KEY_EMOJI_SKIN_TONES = "emoji_skin_tones"
    const val KEY_FEATURE_TOUR_SEEN = "feature_tour_seen"
    const val KEY_USAGE_SESSIONS = "usage_sessions"
    const val KEY_REVIEW_PROMPTED = "review_prompted"

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

    // The user's "Keyboard height" multiplier (1.0 = the auto-derived default).
    // Coerced to the supported range on both read and write so a stray stored
    // value can never blow up the key sizing. Observed by the service (see
    // [com.addiyon.keyboard.AddiyonKeyboardService.keyboardHeightScale]) so the
    // hosted keyboard resizes live when the slider changes it.
    fun keyboardHeightScale(context: Context): Float =
        prefs(context).getFloat(KEY_KEYBOARD_HEIGHT_SCALE, KEYBOARD_HEIGHT_SCALE_DEFAULT)
            .coerceIn(KEYBOARD_HEIGHT_SCALE_MIN, KEYBOARD_HEIGHT_SCALE_MAX)

    fun setKeyboardHeightScale(context: Context, value: Float) =
        prefs(context).edit().putFloat(
            KEY_KEYBOARD_HEIGHT_SCALE,
            value.coerceIn(KEYBOARD_HEIGHT_SCALE_MIN, KEYBOARD_HEIGHT_SCALE_MAX)
        ).apply()

    // The keyboard's active language (Amharic by default), persisted so the
    // user's choice survives the IME service being destroyed -- e.g. after
    // switching to another keyboard and back.
    fun amharicMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AMHARIC_MODE, true)

    fun setAmharicMode(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AMHARIC_MODE, value).apply()

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

    // Whether the first-run feature tour (the pages after the setup steps in
    // OnboardingScreen) has already been shown; it only ever appears once.
    fun featureTourSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FEATURE_TOUR_SEEN, false)

    fun setFeatureTourSeen(context: Context) =
        prefs(context).edit().putBoolean(KEY_FEATURE_TOUR_SEEN, true).apply()

    // Count of keyboard input sessions, used by ReviewPromptPolicy to decide
    // when the user is engaged enough for the one-time in-app review prompt.
    // Counting stops at the policy's cap so this pref isn't rewritten forever.
    fun usageSessions(context: Context): Int =
        prefs(context).getInt(KEY_USAGE_SESSIONS, 0)

    fun recordUsageSession(context: Context) {
        val sessions = usageSessions(context)
        if (ReviewPromptPolicy.shouldCount(sessions)) {
            prefs(context).edit().putInt(KEY_USAGE_SESSIONS, sessions + 1).apply()
        }
    }

    fun reviewPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REVIEW_PROMPTED, false)

    fun setReviewPrompted(context: Context) =
        prefs(context).edit().putBoolean(KEY_REVIEW_PROMPTED, true).apply()
}
