package com.addiyon.keyboard.ai

import android.content.Context
import android.content.SharedPreferences
import com.addiyon.keyboard.ui.ai.AiAccountStore
import java.util.UUID

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
        return AiQuota(used, limit, (limit - used).coerceAtLeast(0), today)
    }

    override fun saveQuota(quota: AiQuota) {
        val limit = quota.limit.coerceIn(1, MAX_QUOTA)
        val used = quota.used.coerceIn(0, limit)
        edit {
            putString(KEY_QUOTA_DAY, quota.day.take(MAX_DAY_LENGTH))
            putInt(KEY_DAILY_LIMIT, limit)
            putInt(KEY_USED_TODAY, used)
        }
    }

    override fun clearJwt() = setJwt(null)

    private fun string(key: String, maximumLength: Int): String? = runCatching {
        preferences.getString(key, null)?.take(maximumLength)
    }.getOrNull()

    private fun int(key: String, default: Int, minimum: Int, maximum: Int): Int = runCatching {
        preferences.getInt(key, default)
    }.getOrDefault(default).coerceIn(minimum, maximum)

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
    }

    private companion object {
        const val DEFAULT_DAILY_LIMIT = 50_000
        const val MAX_QUOTA = 10_000_000
        const val MAX_JWT_LENGTH = 4_096
        const val MAX_EMAIL_LENGTH = 320
        const val MAX_ANONYMOUS_ID_LENGTH = 64
        const val MAX_DAY_LENGTH = 20
    }
}

internal const val AI_PREFERENCES_FILE = "textrevamp_ai_prefs"
internal const val LEGACY_KEYBOARD_PREFERENCES_FILE = "addiyon_keyboard_prefs"
internal const val KEY_JWT = "ai_jwt"
internal const val KEY_EMAIL = "ai_email"
internal const val KEY_ANONYMOUS_ID = "ai_anon_id"
internal const val KEY_USED_TODAY = "ai_tokens_used_today"
internal const val KEY_QUOTA_DAY = "ai_quota_day"
internal const val KEY_DAILY_LIMIT = "ai_daily_token_limit"
internal const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_ai_preferences_migrated_v1"

internal val AI_STRING_PREFERENCE_KEYS = setOf(
    KEY_JWT,
    KEY_EMAIL,
    KEY_ANONYMOUS_ID,
    KEY_QUOTA_DAY
)
internal val AI_INT_PREFERENCE_KEYS = setOf(
    KEY_USED_TODAY,
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
