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
        readBoolean(context, KEY_VIBRATE, false)

    fun soundOnKeypress(context: Context): Boolean =
        readBoolean(context, KEY_SOUND, false)

    fun setVibrateOnKeypress(context: Context, value: Boolean) =
        writeSafely(context) { putBoolean(KEY_VIBRATE, value) }

    fun setSoundOnKeypress(context: Context, value: Boolean) =
        writeSafely(context) { putBoolean(KEY_SOUND, value) }

    fun palette(context: Context): KeyboardPalette =
        readPalette(context)

    fun setPalette(context: Context, value: KeyboardPalette) =
        writeSafely(context) { putString(KEY_PALETTE, value.id) }

    fun numberRow(context: Context): Boolean =
        readBoolean(context, KEY_NUMBER_ROW, false)

    fun setNumberRow(context: Context, value: Boolean) =
        writeSafely(context) { putBoolean(KEY_NUMBER_ROW, value) }

    // The user's "Keyboard height" multiplier (1.0 = the auto-derived default).
    // Coerced to the supported range on both read and write so a stray stored
    // value can never blow up the key sizing. Observed by the service (see
    // [com.addiyon.keyboard.AddiyonKeyboardService.keyboardHeightScale]) so the
    // hosted keyboard resizes live when the slider changes it.
    fun keyboardHeightScale(context: Context): Float =
        readFloat(
            context,
            KEY_KEYBOARD_HEIGHT_SCALE,
            KEYBOARD_HEIGHT_SCALE_DEFAULT,
            KEYBOARD_HEIGHT_SCALE_MIN,
            KEYBOARD_HEIGHT_SCALE_MAX
        )

    fun setKeyboardHeightScale(context: Context, value: Float) =
        writeSafely(context) {
            putFloat(
                KEY_KEYBOARD_HEIGHT_SCALE,
                PreferenceValueSanitizer.float(
                    value,
                    KEYBOARD_HEIGHT_SCALE_DEFAULT,
                    KEYBOARD_HEIGHT_SCALE_MIN,
                    KEYBOARD_HEIGHT_SCALE_MAX
                )
            )
        }

    // The keyboard's active language (Amharic by default), persisted so the
    // user's choice survives the IME service being destroyed -- e.g. after
    // switching to another keyboard and back.
    fun amharicMode(context: Context): Boolean =
        readBoolean(context, KEY_AMHARIC_MODE, true)

    fun setAmharicMode(context: Context, value: Boolean) =
        writeSafely(context) { putBoolean(KEY_AMHARIC_MODE, value) }

    // The two emoji values are opaque encoded strings; their codecs live in
    // the pure-Kotlin stores (RecentEmojiStore / SkinToneStore), which take
    // these as load/save lambdas so they stay JVM-testable.
    fun recentEmojis(context: Context): String? =
        readString(context, KEY_RECENT_EMOJIS, null, MAX_OPAQUE_PREF_LENGTH)

    fun setRecentEmojis(context: Context, value: String) =
        writeSafely(context) {
            putString(KEY_RECENT_EMOJIS, value.take(MAX_OPAQUE_PREF_LENGTH))
        }

    fun emojiSkinTones(context: Context): String? =
        readString(context, KEY_EMOJI_SKIN_TONES, null, MAX_OPAQUE_PREF_LENGTH)

    fun setEmojiSkinTones(context: Context, value: String) =
        writeSafely(context) {
            putString(KEY_EMOJI_SKIN_TONES, value.take(MAX_OPAQUE_PREF_LENGTH))
        }

    // Whether the first-run feature tour (the pages after the setup steps in
    // OnboardingScreen) has already been shown; it only ever appears once.
    fun featureTourSeen(context: Context): Boolean =
        readBoolean(context, KEY_FEATURE_TOUR_SEEN, false)

    fun setFeatureTourSeen(context: Context) =
        writeSafely(context) { putBoolean(KEY_FEATURE_TOUR_SEEN, true) }

    // Count of keyboard input sessions, used by ReviewPromptPolicy to decide
    // when the user is engaged enough for the one-time in-app review prompt.
    // Counting stops at the policy's cap so this pref isn't rewritten forever.
    fun usageSessions(context: Context): Int =
        readInt(context, KEY_USAGE_SESSIONS, 0, 0, ReviewPromptPolicy.COUNT_CAP)

    fun recordUsageSession(context: Context) {
        val sessions = usageSessions(context)
        if (ReviewPromptPolicy.shouldCount(sessions)) {
            writeSafely(context) { putInt(KEY_USAGE_SESSIONS, sessions + 1) }
        }
    }

    fun reviewPrompted(context: Context): Boolean =
        readBoolean(context, KEY_REVIEW_PROMPTED, false)

    fun setReviewPrompted(context: Context) =
        writeSafely(context) { putBoolean(KEY_REVIEW_PROMPTED, true) }

    private fun readBoolean(context: Context, key: String, default: Boolean): Boolean {
        val raw = rawValue(context, key)
        val sanitized = PreferenceValueSanitizer.boolean(raw, default)
        if (raw != null && raw !is Boolean) {
            repair(context) { putBoolean(key, sanitized) }
        }
        return sanitized
    }

    private fun readFloat(
        context: Context,
        key: String,
        default: Float,
        minimum: Float,
        maximum: Float
    ): Float {
        val raw = rawValue(context, key)
        val sanitized = PreferenceValueSanitizer.float(raw, default, minimum, maximum)
        if (raw != null && (raw !is Float || !raw.isFinite() || raw != sanitized)) {
            repair(context) { putFloat(key, sanitized) }
        }
        return sanitized
    }

    private fun readInt(
        context: Context,
        key: String,
        default: Int,
        minimum: Int,
        maximum: Int
    ): Int {
        val raw = rawValue(context, key)
        val sanitized = PreferenceValueSanitizer.int(raw, default, minimum, maximum)
        if (raw != null && (raw !is Int || raw != sanitized)) {
            repair(context) { putInt(key, sanitized) }
        }
        return sanitized
    }

    private fun readString(
        context: Context,
        key: String,
        default: String?,
        maximumLength: Int
    ): String? {
        val raw = rawValue(context, key)
        val sanitized = PreferenceValueSanitizer.string(raw, default, maximumLength)
        if (raw != null && (raw !is String || raw != sanitized)) {
            repair(context) {
                if (sanitized == null) remove(key) else putString(key, sanitized)
            }
        }
        return sanitized
    }

    private fun readPalette(context: Context): KeyboardPalette {
        val id = readString(context, KEY_PALETTE, KeyboardPalette.CLASSIC.id, 64)
        val palette = KeyboardPalette.fromId(id)
        if (id != palette.id) {
            repair(context) { putString(KEY_PALETTE, palette.id) }
        }
        return palette
    }

    private fun rawValue(context: Context, key: String): Any? = try {
        prefs(context).all[key]
    } catch (_: Throwable) {
        null
    }

    private fun repair(
        context: Context,
        update: SharedPreferences.Editor.() -> SharedPreferences.Editor
    ) {
        try {
            update(prefs(context).edit()).apply()
        } catch (_: Throwable) {
        }
    }

    private fun writeSafely(
        context: Context,
        update: SharedPreferences.Editor.() -> Unit
    ) {
        try {
            prefs(context).edit().apply(update).apply()
        } catch (_: Throwable) {
        }
    }

    private const val MAX_OPAQUE_PREF_LENGTH = 65_536
}
