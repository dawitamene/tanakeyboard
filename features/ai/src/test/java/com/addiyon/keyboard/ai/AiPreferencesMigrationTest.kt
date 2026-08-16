package com.addiyon.keyboard.ai

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreferencesMigrationTest {
    @Test
    fun migratesAiValuesAndLeavesSharedKeyboardValuesIsolated() {
        val destination = FakeSharedPreferences()
        val legacy = FakeSharedPreferences(
            mapOf(
                KEY_JWT to "jwt",
                KEY_EMAIL to "person@example.com",
                KEY_ANONYMOUS_ID to "anonymous",
                KEY_USED_TODAY to 7_000,
                KEY_REMAINING_TODAY to 18_000,
                KEY_QUOTA_DAY to "2026-08-10",
                KEY_DAILY_LIMIT to 50_000,
                "palette" to "MIDNIGHT",
                "vibrate_on_keypress" to true
            )
        )

        migrateLegacyAiPreferences(destination, legacy)

        assertEquals("jwt", destination.value(KEY_JWT))
        assertEquals("person@example.com", destination.value(KEY_EMAIL))
        assertEquals("anonymous", destination.value(KEY_ANONYMOUS_ID))
        assertEquals(7_000, destination.value(KEY_USED_TODAY))
        assertEquals(18_000, destination.value(KEY_REMAINING_TODAY))
        assertEquals("2026-08-10", destination.value(KEY_QUOTA_DAY))
        assertEquals(50_000, destination.value(KEY_DAILY_LIMIT))
        assertEquals(true, destination.value(KEY_LEGACY_MIGRATION_COMPLETE))
        assertNull(destination.value("palette"))
        assertNull(destination.value("vibrate_on_keypress"))
        LEGACY_AI_PREFERENCE_KEYS.forEach { assertFalse(legacy.contains(it)) }
        assertEquals("MIDNIGHT", legacy.value("palette"))
        assertEquals(true, legacy.value("vibrate_on_keypress"))
        assertTrue(AI_PREFERENCES_FILE != LEGACY_KEYBOARD_PREFERENCES_FILE)
    }

    @Test
    fun existingFeatureValuesWinOverLegacyValues() {
        val destination = FakeSharedPreferences(mapOf(KEY_JWT to "feature-jwt"))
        val legacy = FakeSharedPreferences(
            mapOf(
                KEY_JWT to "legacy-jwt",
                KEY_EMAIL to "legacy@example.com"
            )
        )

        migrateLegacyAiPreferences(destination, legacy)

        assertEquals("feature-jwt", destination.value(KEY_JWT))
        assertEquals("legacy@example.com", destination.value(KEY_EMAIL))
        LEGACY_AI_PREFERENCE_KEYS.forEach { assertFalse(legacy.contains(it)) }
    }

    @Test
    fun failedDestinationCommitLeavesLegacyValuesUntouched() {
        val destination = FakeSharedPreferences().apply { commitsToFail = 1 }
        val legacy = FakeSharedPreferences(mapOf(KEY_JWT to "legacy-jwt"))

        migrateLegacyAiPreferences(destination, legacy)

        assertNull(destination.value(KEY_JWT))
        assertNull(destination.value(KEY_LEGACY_MIGRATION_COMPLETE))
        assertEquals("legacy-jwt", legacy.value(KEY_JWT))
    }

    @Test
    fun failedLegacyCleanupRetriesWithoutOverwritingFeatureValues() {
        val destination = FakeSharedPreferences()
        val legacy = FakeSharedPreferences(mapOf(KEY_JWT to "legacy-jwt")).apply {
            commitsToFail = 1
        }

        migrateLegacyAiPreferences(destination, legacy)

        assertEquals("legacy-jwt", destination.value(KEY_JWT))
        assertEquals(true, destination.value(KEY_LEGACY_MIGRATION_COMPLETE))
        assertEquals("legacy-jwt", legacy.value(KEY_JWT))
        destination.set(KEY_JWT, "current-jwt")

        migrateLegacyAiPreferences(destination, legacy)

        assertEquals("current-jwt", destination.value(KEY_JWT))
        assertFalse(legacy.contains(KEY_JWT))
    }

    @Test
    fun restoresTheExactServerRemainingQuota() {
        assertEquals(25_500, restoredQuotaRemaining(50_000, 7_000, 25_500))
        assertEquals(43_000, restoredQuotaRemaining(50_000, 7_000, null))
        assertEquals(43_000, restoredQuotaRemaining(50_000, 7_000, 49_000))
    }
}

private class FakeSharedPreferences(
    initialValues: Map<String, Any> = emptyMap()
) : SharedPreferences {
    private val values = initialValues.toMutableMap()
    var commitsToFail: Int = 0

    fun value(key: String): Any? = values[key]

    fun set(key: String, value: Any) {
        values[key] = value
    }

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue

    override fun getStringSet(key: String, defaultValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        ((values[key] as? Set<String>)?.toSet() ?: defaultValues)

    override fun getInt(key: String, defaultValue: Int): Int =
        values[key] as? Int ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        values[key] as? Long ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        values[key] as? Float ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = also {
            updates[key] = value
            removals.remove(key)
        }

        override fun putStringSet(
            key: String,
            values: Set<String>?
        ): SharedPreferences.Editor = also {
            updates[key] = values?.toSet()
            removals.remove(key)
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = also {
            updates[key] = value
            removals.remove(key)
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = also {
            updates[key] = value
            removals.remove(key)
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = also {
            updates[key] = value
            removals.remove(key)
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = also {
            updates[key] = value
            removals.remove(key)
        }

        override fun remove(key: String): SharedPreferences.Editor = also {
            removals += key
            updates.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = also {
            clearRequested = true
            updates.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            if (commitsToFail > 0) {
                commitsToFail -= 1
                return false
            }
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
