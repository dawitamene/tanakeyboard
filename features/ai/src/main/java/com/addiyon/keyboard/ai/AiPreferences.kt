package com.addiyon.keyboard.ai

import android.content.Context
import android.content.SharedPreferences
import com.addiyon.keyboard.ui.ai.AiAccountStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class AiPreferences(context: Context) : AiAccountStore {
    private val preferences: SharedPreferences

    init {
        val applicationContext = context.applicationContext
        preferences = applicationContext.getSharedPreferences(
            AI_PREFERENCES_FILE,
            Context.MODE_PRIVATE
        )
        migrateLegacyAiPreferences(
            destination = preferences,
            legacy = applicationContext.getSharedPreferences(
                LEGACY_KEYBOARD_PREFERENCES_FILE,
                Context.MODE_PRIVATE
            )
        )
    }

    override fun jwt(): String? = string(KEY_JWT, MAX_JWT_LENGTH)

    override fun setJwt(value: String?) {
        preferences.edit().apply {
            if (value == null) remove(KEY_JWT) else putString(KEY_JWT, value.take(MAX_JWT_LENGTH))
        }.commit()
    }

    override fun email(): String? = string(KEY_EMAIL, MAX_EMAIL_LENGTH)

    override fun setEmail(value: String?) {
        edit {
            if (value == null) remove(KEY_EMAIL) else putString(KEY_EMAIL, value.take(MAX_EMAIL_LENGTH))
        }
    }

    override fun anonymousId(): String {
        val existing = string(KEY_ANONYMOUS_ID, MAX_ANONYMOUS_ID_LENGTH)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        edit { putString(KEY_ANONYMOUS_ID, generated) }
        return generated
    }

    override fun quota(): AiQuota {
        val today = todayIso()
        val limit = int(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT, 1, MAX_QUOTA)
        val storedDay = string(KEY_QUOTA_DAY, MAX_DAY_LENGTH)
        if (storedDay != today) {
            val quota = AiQuota(0, limit, limit, today)
            saveQuota(quota)
            return quota
        }
        val used = int(KEY_USED_TODAY, 0, 0, limit)
        val storedRemaining = if (preferences.contains(KEY_REMAINING_TODAY)) {
            int(KEY_REMAINING_TODAY, limit - used, 0, limit)
        } else {
            null
        }
        return AiQuota(used, limit, restoredQuotaRemaining(limit, used, storedRemaining), today)
    }

    override fun saveQuota(quota: AiQuota) {
        val limit = quota.limit.coerceIn(1, MAX_QUOTA)
        val used = quota.used.coerceIn(0, limit)
        val remaining = quota.remaining.coerceIn(0, limit - used)
        edit {
            putString(KEY_QUOTA_DAY, quota.day.take(MAX_DAY_LENGTH))
            putInt(KEY_DAILY_LIMIT, limit)
            putInt(KEY_USED_TODAY, used)
            putInt(KEY_REMAINING_TODAY, remaining)
        }
    }

    override fun clearJwt() = setJwt(null)

    override fun phraseCompletionsEnabled(): Boolean = runCatching {
        preferences.getBoolean(KEY_PHRASE_COMPLETIONS_ENABLED, false)
    }.getOrDefault(false)

    override fun setPhraseCompletionsEnabled(enabled: Boolean) {
        edit { putBoolean(KEY_PHRASE_COMPLETIONS_ENABLED, enabled) }
    }

    override fun phraseCompletionConsentVersion(): Int = int(
        KEY_PHRASE_COMPLETION_CONSENT_VERSION,
        0,
        0,
        CURRENT_PHRASE_COMPLETION_CONSENT_VERSION
    )

    override fun setPhraseCompletionConsentVersion(version: Int) {
        edit {
            putInt(
                KEY_PHRASE_COMPLETION_CONSENT_VERSION,
                version.coerceIn(0, CURRENT_PHRASE_COMPLETION_CONSENT_VERSION)
            )
        }
    }

    override fun customTones(): List<CustomTone> =
        decodeCustomTones(string(KEY_CUSTOM_TONES, MAX_CUSTOM_TONES_STORAGE))

    override fun addCustomTone(
        title: String,
        instruction: String,
        icon: String,
        color: String
    ): CustomTone? {
        val cleanTitle = cleanCustomToneText(title, MAX_CUSTOM_TONE_TITLE_LENGTH)
        val cleanInstruction = cleanCustomToneText(instruction, MAX_CUSTOM_TONE_INSTRUCTION_LENGTH)
        if (cleanTitle.isEmpty() || cleanInstruction.isEmpty()) return null
        val tone = CustomTone(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            instruction = cleanInstruction,
            icon = sanitizeStoredIcon(icon),
            color = sanitizeColor(color)
        )
        saveCustomTones((customTones() + tone).take(MAX_CUSTOM_TONES))
        return tone
    }

    override fun updateCustomTone(
        id: String,
        title: String,
        instruction: String,
        icon: String,
        color: String
    ): CustomTone? {
        val cleanTitle = cleanCustomToneText(title, MAX_CUSTOM_TONE_TITLE_LENGTH)
        val cleanInstruction = cleanCustomToneText(instruction, MAX_CUSTOM_TONE_INSTRUCTION_LENGTH)
        if (cleanTitle.isEmpty() || cleanInstruction.isEmpty()) return null
        val updated = CustomTone(
            id = id,
            title = cleanTitle,
            instruction = cleanInstruction,
            icon = sanitizeStoredIcon(icon),
            color = sanitizeColor(color)
        )
        val tones = customTones()
        if (tones.none { it.id == id }) return null
        saveCustomTones(tones.map { if (it.id == id) updated else it })
        return updated
    }

    override fun removeCustomTone(id: String) {
        saveCustomTones(customTones().filterNot { it.id == id })
    }

    override fun registerCustomToneChangeListener(listener: () -> Unit) {
        if (customToneListeners.isEmpty()) {
            preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
        customToneListeners += listener
    }

    override fun unregisterCustomToneChangeListener(listener: () -> Unit) {
        customToneListeners -= listener
        if (customToneListeners.isEmpty()) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
    }

    private fun cleanCustomToneText(value: String, maximumLength: Int): String =
        value.trim().filterNot {
            it == CUSTOM_TONE_FIELD_SEPARATOR_CHAR || it == CUSTOM_TONE_RECORD_SEPARATOR_CHAR
        }.take(maximumLength)

    private fun sanitizeColor(color: String): String =
        if (color in CustomToneColor.All) color else CustomToneColor.Default

    private fun saveCustomTones(tones: List<CustomTone>) {
        val raw = encodeCustomTones(tones)
        edit {
            if (raw.isEmpty()) remove(KEY_CUSTOM_TONES) else putString(KEY_CUSTOM_TONES, raw)
        }
    }

    private fun string(key: String, maximumLength: Int): String? = runCatching {
        preferences.getString(key, null)?.take(maximumLength)
    }.getOrNull()

    private fun int(key: String, default: Int, minimum: Int, maximum: Int): Int = runCatching {
        preferences.getInt(key, default)
    }.getOrDefault(default).coerceIn(minimum, maximum)

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
    }

    private val customToneListeners = CopyOnWriteArrayList<() -> Unit>()
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CUSTOM_TONES) customToneListeners.forEach { it() }
        }

    private companion object {
        const val DEFAULT_DAILY_LIMIT = 50_000
        const val MAX_QUOTA = 10_000_000
        const val MAX_JWT_LENGTH = 4_096
        const val MAX_EMAIL_LENGTH = 320
        const val MAX_ANONYMOUS_ID_LENGTH = 64
        const val MAX_DAY_LENGTH = 20
        const val MAX_CUSTOM_TONES = 10
        const val MAX_CUSTOM_TONE_TITLE_LENGTH = 40
        const val MAX_CUSTOM_TONE_INSTRUCTION_LENGTH = 120
        const val MAX_CUSTOM_TONES_STORAGE = 2_048
    }
}

internal fun encodeCustomTones(tones: List<CustomTone>): String =
    tones.joinToString(CUSTOM_TONE_RECORD_SEPARATOR) { tone ->
        "${tone.id}$CUSTOM_TONE_FIELD_SEPARATOR${tone.title}" +
            "$CUSTOM_TONE_FIELD_SEPARATOR${tone.instruction}" +
            "$CUSTOM_TONE_FIELD_SEPARATOR${tone.icon}" +
            "$CUSTOM_TONE_FIELD_SEPARATOR${tone.color}"
    }

internal fun decodeCustomTones(raw: String?): List<CustomTone> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(CUSTOM_TONE_RECORD_SEPARATOR).mapNotNull { record ->
        val parts = record.split(CUSTOM_TONE_FIELD_SEPARATOR)
        when (parts.size) {
            5 -> {
                val id = parts[0].trim()
                val title = parts[1]
                val instruction = parts[2]
                val icon = parts[3]
                val color = parts[4]
                if (id.isEmpty() || title.isEmpty() || instruction.isEmpty()) {
                    null
                } else {
                    CustomTone(
                        id = id,
                        title = title,
                        instruction = instruction,
                        icon = sanitizeStoredIcon(icon),
                        color = if (color in CustomToneColor.All) color else CustomToneColor.Default
                    )
                }
            }
            // Records written before icons and colors existed.
            3 -> {
                val id = parts[0].trim()
                val title = parts[1]
                val instruction = parts[2]
                if (id.isEmpty() || title.isEmpty() || instruction.isEmpty()) {
                    null
                } else {
                    CustomTone(id, title, instruction)
                }
            }
            // Records written before titles existed fall back to the instruction
            // as the chip title.
            2 -> {
                val id = parts[0].trim()
                val instruction = parts[1]
                if (id.isEmpty() || instruction.isEmpty()) null else CustomTone(id, instruction, instruction)
            }
            else -> null
        }
    }
}

internal const val AI_PREFERENCES_FILE = "textrevamp_ai_prefs"
internal const val LEGACY_KEYBOARD_PREFERENCES_FILE = "addiyon_keyboard_prefs"
internal const val KEY_JWT = "ai_jwt"
internal const val KEY_EMAIL = "ai_email"
internal const val KEY_ANONYMOUS_ID = "ai_anon_id"
internal const val KEY_USED_TODAY = "ai_tokens_used_today"
internal const val KEY_REMAINING_TODAY = "ai_tokens_remaining_today"
internal const val KEY_QUOTA_DAY = "ai_quota_day"
internal const val KEY_DAILY_LIMIT = "ai_daily_token_limit"
internal const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_ai_preferences_migrated_v1"
internal const val KEY_PHRASE_COMPLETIONS_ENABLED = "ai_phrase_completions_enabled"
internal const val KEY_PHRASE_COMPLETION_CONSENT_VERSION =
    "ai_phrase_completion_consent_version"
internal const val KEY_CUSTOM_TONES = "ai_custom_tones"
internal const val CUSTOM_TONE_FIELD_SEPARATOR = "\u0001"
internal const val CUSTOM_TONE_RECORD_SEPARATOR = "\u001F"
private const val CUSTOM_TONE_FIELD_SEPARATOR_CHAR = '\u0001'
private const val CUSTOM_TONE_RECORD_SEPARATOR_CHAR = '\u001F'

/**
 * Cleans a stored custom-tone icon id: strips record separators and maps any
 * unknown id (including the emoji glyphs written by the interim format) to
 * the default icon.
 */
internal fun sanitizeStoredIcon(icon: String): String {
    val cleaned = icon.trim().filterNot {
        it == CUSTOM_TONE_FIELD_SEPARATOR_CHAR || it == CUSTOM_TONE_RECORD_SEPARATOR_CHAR
    }
    if (cleaned.isEmpty() || cleaned !in CustomToneIcon.All) return CustomToneIcon.Default
    return cleaned
}

const val CURRENT_PHRASE_COMPLETION_CONSENT_VERSION = 1

internal val AI_STRING_PREFERENCE_KEYS = setOf(
    KEY_JWT,
    KEY_EMAIL,
    KEY_ANONYMOUS_ID,
    KEY_QUOTA_DAY
)
internal val AI_INT_PREFERENCE_KEYS = setOf(
    KEY_USED_TODAY,
    KEY_REMAINING_TODAY,
    KEY_DAILY_LIMIT
)
internal val LEGACY_AI_PREFERENCE_KEYS =
    AI_STRING_PREFERENCE_KEYS + AI_INT_PREFERENCE_KEYS

internal fun migrateLegacyAiPreferences(
    destination: SharedPreferences,
    legacy: SharedPreferences
) {
    synchronized(AiPreferenceMigrationLock) {
        val destinationValues = runCatching { destination.all }.getOrElse { return }
        val legacyValues = runCatching { legacy.all }.getOrElse { return }
        val migrationComplete = destinationValues[KEY_LEGACY_MIGRATION_COMPLETE] == true
        if (!migrationComplete) {
            val valuesToCopy = legacyAiValuesToCopy(destinationValues, legacyValues)
            val committed = runCatching {
                val editor = destination.edit()
                valuesToCopy.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                    }
                }
                editor.putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true)
                editor.commit()
            }.getOrDefault(false)
            if (!committed) return
        }
        if (LEGACY_AI_PREFERENCE_KEYS.none(legacyValues::containsKey)) return
        runCatching {
            val editor = legacy.edit()
            LEGACY_AI_PREFERENCE_KEYS.forEach(editor::remove)
            editor.commit()
        }
    }
}

internal fun legacyAiValuesToCopy(
    destinationValues: Map<String, *>,
    legacyValues: Map<String, *>
): Map<String, Any> = buildMap {
    AI_STRING_PREFERENCE_KEYS.forEach { key ->
        if (destinationValues[key] !is String) {
            (legacyValues[key] as? String)?.let { put(key, it) }
        }
    }
    AI_INT_PREFERENCE_KEYS.forEach { key ->
        if (destinationValues[key] !is Int) {
            (legacyValues[key] as? Int)?.let { put(key, it) }
        }
    }
}

private object AiPreferenceMigrationLock

internal fun restoredQuotaRemaining(limit: Int, used: Int, storedRemaining: Int?): Int {
    val maximumRemaining = (limit - used).coerceAtLeast(0)
    return storedRemaining?.coerceIn(0, maximumRemaining) ?: maximumRemaining
}
