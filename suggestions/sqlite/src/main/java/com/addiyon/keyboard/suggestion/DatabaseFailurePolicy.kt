package com.addiyon.keyboard.suggestion

internal object DatabaseFailurePolicy {
    private val failureTypes = setOf(
        "android.database.sqlite.SQLiteDatabaseCorruptException",
        "android.database.sqlite.SQLiteCantOpenDatabaseException",
        "android.database.sqlite.SQLiteDiskIOException",
        "android.database.sqlite.SQLiteReadOnlyDatabaseException"
    )

    private val failureMessages = listOf(
        "database disk image is malformed",
        "file is not a database",
        "database corruption",
        "database corrupt",
        "unable to open database",
        "cannot open database",
        "disk i/o error",
        "readonly database",
        "read-only database"
    )

    fun shouldRecover(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        repeat(MAX_CAUSE_DEPTH) {
            val error = current ?: return false
            if (matches(error.javaClass.name, error.message)) return true
            current = error.cause
        }
        return false
    }

    fun matches(typeName: String, message: String?): Boolean {
        if (typeName in failureTypes) return true
        val normalized = message?.lowercase() ?: return false
        return failureMessages.any(normalized::contains)
    }

    private const val MAX_CAUSE_DEPTH = 8
}
