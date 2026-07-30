package com.addiyon.keyboard.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizedNonFatalTest {
    @Test
    fun mapsEveryThrowableFamilyToACoarseContentFreeClass() {
        val cases = listOf(
            IllegalArgumentException("secret") to CoarseThrowableClass.ILLEGAL_ARGUMENT,
            IllegalStateException("secret") to CoarseThrowableClass.ILLEGAL_STATE,
            SecurityException("secret") to CoarseThrowableClass.SECURITY,
            java.io.IOException("secret") to CoarseThrowableClass.IO,
            RuntimeException("secret") to CoarseThrowableClass.RUNTIME,
            AssertionError("secret") to CoarseThrowableClass.ERROR,
            Exception("secret") to CoarseThrowableClass.OTHER
        )

        cases.forEach { (throwable, expected) ->
            throwable.stackTrace = emptyArray()
            val report = requireNotNull(
                NonFatalSanitizer.sanitize(NonFatalCategory.EDITOR, throwable)
            )

            assertEquals(expected, report.throwableClass)
            assertNull(report.exception().message)
        }
    }

    @Test
    fun stripsMessagesCausesSuppressedAndForeignFrames() {
        val source = IllegalStateException(
            "typed secret",
            IllegalArgumentException("typed cause")
        )
        source.addSuppressed(RuntimeException("typed suppressed"))
        source.stackTrace = arrayOf(
            StackTraceElement(
                "com.addiyon.keyboard.suggestion.SQLiteDictionary",
                "frequencyOf",
                "SQLiteDictionary.kt",
                42
            ),
            StackTraceElement(
                "third.party.Editor",
                "typedSecretMethod",
                "Editor.kt",
                12
            )
        )

        val report = requireNotNull(
            NonFatalSanitizer.sanitize(NonFatalCategory.DATABASE, source)
        )
        val sanitized = report.exception()

        assertNull(sanitized.message)
        assertNull(sanitized.cause)
        assertTrue(sanitized.suppressed.isEmpty())
        assertEquals(CoarseThrowableClass.ILLEGAL_STATE, report.throwableClass)
        assertEquals(1, sanitized.stackTrace.size)
        assertEquals(
            "com.addiyon.keyboard.suggestion.SQLiteDictionary",
            sanitized.stackTrace.single().className
        )
    }

    @Test
    fun outOfMemoryErrorsAreNeverReported() {
        assertNull(
            NonFatalSanitizer.sanitize(
                NonFatalCategory.APPLICATION_OPERATION,
                OutOfMemoryError("typed secret")
            )
        )
    }

    @Test
    fun keepsOnlyBoundedAllowlistedWellFormedFrames() {
        val source = RuntimeException("secret")
        val valid = listOf(
            StackTraceElement("com.addiyon.keyboard.Core", "call", null, 1),
            StackTraceElement("android.view.View", "draw", "View.java", 2),
            StackTraceElement("androidx.core.Core", "run", "Core.kt", 3),
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 4),
            StackTraceElement("kotlin.collections.ListsKt", "map", "Lists.kt", 5),
            StackTraceElement("dalvik.system.BaseDex", "find", "BaseDex.java", 6)
        )
        val invalid = listOf(
            StackTraceElement(
                "com.addiyon.keyboard." + "A".repeat(181),
                "call",
                "Core.kt",
                1
            ),
            StackTraceElement("com.addiyon.keyboard.Bad Name", "call", "Core.kt", 1),
            StackTraceElement(
                "com.addiyon.keyboard.Core",
                "m".repeat(121),
                "Core.kt",
                1
            ),
            StackTraceElement("com.addiyon.keyboard.Core", "bad method", "Core.kt", 1),
            StackTraceElement(
                "com.addiyon.keyboard.Core",
                "call",
                "F".repeat(121),
                1
            ),
            StackTraceElement("com.addiyon.keyboard.Core", "call", "bad file.kt", 1)
        )
        source.stackTrace = (invalid + valid + List(30) {
            StackTraceElement(
                "com.addiyon.keyboard.Generated$it",
                "call",
                "Generated.kt",
                it
            )
        }).toTypedArray()

        val report = requireNotNull(
            NonFatalSanitizer.sanitize(NonFatalCategory.APPLICATION_OPERATION, source)
        )

        assertEquals(24, report.frames.size)
        assertEquals(valid.map { it.className }, report.frames.take(valid.size).map { it.className })
        assertTrue(report.frames.none { frame -> invalid.any { it == frame } })
    }

    @Test
    fun emptySanitizedStackUsesOneFixedFallbackFrame() {
        val source = RuntimeException("secret").apply {
            stackTrace = arrayOf(
                StackTraceElement("third.party.Editor", "secret", "Editor.kt", 1)
            )
        }

        val report = requireNotNull(
            NonFatalSanitizer.sanitize(NonFatalCategory.EDITOR, source)
        )

        assertEquals(1, report.frames.size)
        assertEquals(
            "com.addiyon.keyboard.telemetry.Telemetry",
            report.frames.single().className
        )
    }

    @Test
    fun mapsOnlyReviewedCoarseThrowableClasses() {
        val cases = listOf(
            IllegalArgumentException() to CoarseThrowableClass.ILLEGAL_ARGUMENT,
            IllegalStateException() to CoarseThrowableClass.ILLEGAL_STATE,
            SecurityException() to CoarseThrowableClass.SECURITY,
            java.io.IOException() to CoarseThrowableClass.IO,
            RuntimeException() to CoarseThrowableClass.RUNTIME,
            AssertionError() to CoarseThrowableClass.ERROR,
            Throwable() to CoarseThrowableClass.OTHER
        )

        cases.forEach { (source, expected) ->
            source.stackTrace = emptyArray()
            val report = requireNotNull(
                NonFatalSanitizer.sanitize(NonFatalCategory.APPLICATION_OPERATION, source)
            )
            assertEquals(expected, report.throwableClass)
            assertEquals(
                "com.addiyon.keyboard.telemetry.Telemetry",
                report.frames.single().className
            )
        }
    }

    @Test
    fun acceptsNullFileNamesAndRejectsUnsafeFrameMetadata() {
        val source = RuntimeException().apply {
            stackTrace = arrayOf(
                StackTraceElement(
                    "com.addiyon.keyboard.EditorGateway",
                    "commit",
                    null,
                    5
                ),
                StackTraceElement(
                    "com.addiyon.keyboard.EditorGateway",
                    "typed secret",
                    "EditorGateway.kt",
                    6
                ),
                StackTraceElement(
                    "com.addiyon.keyboard.EditorGateway",
                    "commit",
                    "typed secret.kt",
                    7
                )
            )
        }

        val report = requireNotNull(
            NonFatalSanitizer.sanitize(NonFatalCategory.EDITOR, source)
        )

        assertEquals(1, report.frames.size)
        assertNull(report.frames.single().fileName)
    }

    @Test
    fun hostileStackTraceAccessFallsBackToFixedFrame() {
        val source = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement> {
                throw IllegalStateException()
            }
        }

        val report = requireNotNull(
            NonFatalSanitizer.sanitize(NonFatalCategory.APPLICATION_OPERATION, source)
        )

        assertEquals(1, report.frames.size)
        assertEquals(
            "com.addiyon.keyboard.telemetry.Telemetry",
            report.frames.single().className
        )
    }

    @Test
    fun categoryRateLimitDeduplicatesUntilIntervalExpires() {
        val limiter = NonFatalRateLimiter(intervalMillis = 100)

        assertTrue(limiter.shouldReport(NonFatalCategory.DATABASE, 1_000))
        assertTrue(!limiter.shouldReport(NonFatalCategory.DATABASE, 1_099))
        assertTrue(limiter.shouldReport(NonFatalCategory.VOICE, 1_099))
        assertTrue(limiter.shouldReport(NonFatalCategory.DATABASE, 1_100))
    }
}
