package com.addiyon.keyboard.telemetry

internal enum class NonFatalCategory(internal val wireName: String) {
    APPLICATION_OPERATION("application_operation"),
    DATABASE("database"),
    VOICE("voice"),
    UPDATE("update"),
    REVIEW("review"),
    EDITOR("editor")
}

internal enum class CoarseThrowableClass(internal val wireName: String) {
    ILLEGAL_ARGUMENT("illegal_argument"),
    ILLEGAL_STATE("illegal_state"),
    SECURITY("security"),
    IO("io"),
    RUNTIME("runtime"),
    ERROR("error"),
    OTHER("other")
}

internal data class SanitizedNonFatal(
    val category: NonFatalCategory,
    val throwableClass: CoarseThrowableClass,
    val frames: List<StackTraceElement>
) {
    fun exception(): Throwable = SanitizedNonFatalException(this)
}

internal class SanitizedNonFatalException(report: SanitizedNonFatal) : RuntimeException() {
    init {
        stackTrace = report.frames.toTypedArray()
    }
}

internal object NonFatalSanitizer {
    private val safeClass = Regex("[A-Za-z0-9_.$]+")
    private val safeMethod = Regex("[A-Za-z0-9_$<>-]+")
    private val safeFile = Regex("[A-Za-z0-9_.$-]+")
    private val allowedPrefixes = listOf(
        "com.addiyon.keyboard.",
        "android.",
        "androidx.",
        "java.",
        "kotlin.",
        "dalvik."
    )

    fun sanitize(
        category: NonFatalCategory,
        throwable: Throwable
    ): SanitizedNonFatal? {
        if (throwable is OutOfMemoryError) return null
        val frames = try {
            throwable.stackTrace
                .asSequence()
                .filter(::isSafe)
                .take(MAX_FRAMES)
                .map(::copyFrame)
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
        val safeFrames = frames.ifEmpty {
            listOf(
                StackTraceElement(
                    "com.addiyon.keyboard.telemetry.Telemetry",
                    "recordNonFatal",
                    "Telemetry.kt",
                    -1
                )
            )
        }
        return SanitizedNonFatal(
            category = category,
            throwableClass = coarseClass(throwable),
            frames = safeFrames
        )
    }

    private fun isSafe(frame: StackTraceElement): Boolean {
        val fileName = frame.fileName
        return allowedPrefixes.any(frame.className::startsWith) &&
            frame.className.length <= MAX_CLASS_LENGTH &&
            safeClass.matches(frame.className) &&
            frame.methodName.length <= MAX_METHOD_LENGTH &&
            safeMethod.matches(frame.methodName) &&
            (
                fileName == null ||
                    (
                        fileName.length <= MAX_FILE_LENGTH &&
                            safeFile.matches(fileName)
                        )
                )
    }

    private fun copyFrame(frame: StackTraceElement): StackTraceElement =
        StackTraceElement(
            frame.className,
            frame.methodName,
            frame.fileName,
            frame.lineNumber
        )

    private fun coarseClass(throwable: Throwable): CoarseThrowableClass = when (throwable) {
        is IllegalArgumentException -> CoarseThrowableClass.ILLEGAL_ARGUMENT
        is IllegalStateException -> CoarseThrowableClass.ILLEGAL_STATE
        is SecurityException -> CoarseThrowableClass.SECURITY
        is java.io.IOException -> CoarseThrowableClass.IO
        is RuntimeException -> CoarseThrowableClass.RUNTIME
        is Error -> CoarseThrowableClass.ERROR
        else -> CoarseThrowableClass.OTHER
    }

    private const val MAX_FRAMES = 24
    private const val MAX_CLASS_LENGTH = 180
    private const val MAX_METHOD_LENGTH = 120
    private const val MAX_FILE_LENGTH = 120
}

internal class NonFatalRateLimiter(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    private val lastReports = mutableMapOf<NonFatalCategory, Long>()

    @Synchronized
    fun shouldReport(category: NonFatalCategory, nowMillis: Long): Boolean {
        val previous = lastReports[category]
        if (previous != null && nowMillis - previous < intervalMillis) return false
        lastReports[category] = nowMillis
        return true
    }

    internal companion object {
        const val DEFAULT_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
