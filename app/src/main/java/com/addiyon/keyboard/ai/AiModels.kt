package com.addiyon.keyboard.ai

enum class AiToneTab(
    val label: String,
    val tone: String,
    val instruction: String? = null
) {
    Humanize("Humanize", "Rephrase", "Humanize the text: remove AI-like phrasing, vary sentence rhythm, keep meaning exactly"),
    Professional("Professional", "Professional"),
    Casual("Casual", "Casual"),
    Formal("Formal", "Formal"),
    Friendly("Friendly", "Friendly"),
    FixGrammar("Fix Grammar", "Fix Spelling and Grammar"),
    Shorten("Shorten", "Shorten"),
    Summarize("Summarize", "Summarize");

    companion object {
        val DefaultTabs = listOf(Humanize, Professional, Casual, FixGrammar, Shorten)
        val AllTabs = entries.toList()
    }
}

enum class AiStrength(val label: String) {
    Subtle("subtle"),
    Balanced("balanced"),
    Strong("strong")
}

data class AiInput(
    val text: String,
    val wordCount: Int,
    val source: AiSource,
    val snapshot: AiSnapshot?
)

enum class AiSource { Selection, Sentence, Empty }

data class AiSnapshot(
    val replacementStart: Int,
    val replacementEnd: Int,
    val tokenGeneration: Long,
    val tokenSelectionGeneration: Long
)

data class AiResult(
    val text: String,
    val tone: String,
    val strength: String,
    val truncated: Boolean = false
)

sealed interface AiError {
    data object NeedsAuth : AiError
    data class QuotaExceeded(val remaining: Int = 0) : AiError
    data object NoText : AiError
    data object PrivateField : AiError
    data object Offline : AiError
    data class Server(val message: String) : AiError
    data class RateLimited(val retryAfter: Int? = null, val message: String? = null) : AiError
    data object Unknown : AiError
}

data class AiQuota(
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val day: String
)

fun countWords(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    return trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }
}

fun todayIso(): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()

fun extractSentence(textBefore: String, maxChars: Int = 400): String {
    if (textBefore.isEmpty()) return ""
    val window = textBefore.takeLast(maxChars)
    val lastBoundary = window.indexOfLast { it == '.' || it == '!' || it == '?' || it == '\n' }
    val sentence = if (lastBoundary >= 0) window.substring(lastBoundary + 1) else window
    return sentence.trim()
}
