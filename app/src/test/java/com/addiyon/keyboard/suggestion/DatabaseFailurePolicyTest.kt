package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseFailurePolicyTest {

    @Test
    fun recognizesAndroidCorruptionAndOpenFailureTypes() {
        assertTrue(
            DatabaseFailurePolicy.matches(
                "android.database.sqlite.SQLiteDatabaseCorruptException",
                null
            )
        )
        assertTrue(
            DatabaseFailurePolicy.matches(
                "android.database.sqlite.SQLiteCantOpenDatabaseException",
                null
            )
        )
        assertTrue(
            DatabaseFailurePolicy.matches(
                "android.database.sqlite.SQLiteDiskIOException",
                null
            )
        )
    }

    @Test
    fun recognizesWrappedNativeSQLiteFailureMessages() {
        assertTrue(
            DatabaseFailurePolicy.shouldRecover(
                RuntimeException(
                    "wrapper",
                    IllegalStateException("database disk image is malformed")
                )
            )
        )
        assertTrue(
            DatabaseFailurePolicy.shouldRecover(
                IllegalArgumentException("file is not a database")
            )
        )
        assertTrue(
            DatabaseFailurePolicy.shouldRecover(
                IllegalStateException("unable to open database file")
            )
        )
    }

    @Test
    fun programmingAndTransientQueryFailuresDoNotTriggerReinstall() {
        assertFalse(DatabaseFailurePolicy.shouldRecover(IllegalArgumentException("bad SQL")))
        assertFalse(DatabaseFailurePolicy.shouldRecover(IllegalStateException("database is locked")))
        assertFalse(DatabaseFailurePolicy.shouldRecover(RuntimeException("cursor closed")))
    }
}
