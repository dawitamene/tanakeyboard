package com.addiyon.keyboard.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        val DefaultTabs = listOf(FixGrammar, Casual, Humanize, Professional, Shorten)
        val AllTabs = entries.toList()
    }
}

enum class AiStrength(val label: String) {
    Subtle("subtle"),
    Balanced("balanced"),
    Strong("strong");

    companion object {
        fun fromLabel(label: String?): AiStrength? =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

/**
 * A user-created tone. [title] is the short label shown on the keyboard tone
 * chip; [instruction] is sent verbatim to the server as the rewrite prompt;
 * [icon] and [color] select the chip's identity from the predefined
 * [CustomToneIcon] and [CustomToneColor] sets.
 */
data class CustomTone(
    val id: String,
    val title: String,
    val instruction: String,
    val icon: String = CustomToneIcon.Default,
    val color: String = CustomToneColor.Default
) {
    val label: String get() = title
}

object CustomToneIcon {
    const val AUTO_AWESOME = "auto_awesome"
    const val FACE = "face"
    const val FAVORITE = "favorite"
    const val STAR = "star"
    const val BOLT = "bolt"
    const val PALETTE = "palette"
    const val MUSIC_NOTE = "music_note"
    const val EMOJI_EMOTIONS = "emoji_emotions"
    const val SENTIMENT_SATISFIED = "sentiment_satisfied"
    const val THUMB_UP = "thumb_up"
    const val WB_SUNNY = "wb_sunny"
    const val LOCAL_FIRE_DEPARTMENT = "local_fire_department"
    const val WATER_DROP = "water_drop"
    const val ECO = "eco"
    const val PETS = "pets"
    const val SCHOOL = "school"
    const val WORK_OUTLINE = "work_outline"
    const val ACCOUNT_BALANCE = "account_balance"
    const val SPELLCHECK = "spellcheck"
    const val SHORTEN = "shorten"
    const val SUMMARIZE = "summarize"
    const val VERIFIED = "verified"
    const val DIAMOND = "diamond"
    const val ROCKET = "rocket"
    const val CASTLE = "castle"
    const val TERRAIN = "terrain"
    const val FLIGHT = "flight"
    const val COFFEE = "coffee"
    const val ICECREAM = "icecream"
    const val NIGHTLIGHT = "nightlight"
    const val QUESTION_ANSWER = "question_answer"
    const val AUTO_STORIES = "auto_stories"
    const val Default = AUTO_AWESOME

    val All = listOf(
        AUTO_AWESOME,
        FACE,
        FAVORITE,
        STAR,
        BOLT,
        PALETTE,
        MUSIC_NOTE,
        EMOJI_EMOTIONS,
        SENTIMENT_SATISFIED,
        THUMB_UP,
        WB_SUNNY,
        LOCAL_FIRE_DEPARTMENT,
        WATER_DROP,
        ECO,
        PETS,
        SCHOOL,
        WORK_OUTLINE,
        ACCOUNT_BALANCE,
        SPELLCHECK,
        SHORTEN,
        SUMMARIZE,
        VERIFIED,
        DIAMOND,
        ROCKET,
        CASTLE,
        TERRAIN,
        FLIGHT,
        COFFEE,
        ICECREAM,
        NIGHTLIGHT,
        QUESTION_ANSWER,
        AUTO_STORIES
    )
}

object CustomToneColor {
    const val TEAL = "teal"
    const val INDIGO = "indigo"
    const val ORANGE = "orange"
    const val PURPLE = "purple"
    const val GREEN = "green"
    const val ROSE = "rose"
    const val BLUE = "blue"
    const val AMBER = "amber"
    const val Default = TEAL

    val All = listOf(
        TEAL,
        INDIGO,
        ORANGE,
        PURPLE,
        GREEN,
        ROSE,
        BLUE,
        AMBER
    )
}

const val CUSTOM_TONE_DEFAULT_TONE = "Rephrase"

data class AiInput(
    val text: String,
    val wordCount: Int,
    val source: AiSource,
    val snapshot: AiSnapshot?
)

enum class AiSource { Selection, Field, Sentence, Empty }

@JvmInline
value class AiSnapshot(val captureId: Long)

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

internal fun quotaRemainingFromError(message: String): Int? =
    Regex("(?:remaining[=:]\\s*|\\\"remaining\\\"\\s*:\\s*)(\\d+)")
        .find(message)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

fun countWords(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    return trimmed.split(Regex("\\s+")).count { it.isNotEmpty() }
}

fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

fun extractSentence(textBefore: String, maxChars: Int = 400): String {
    if (textBefore.isEmpty()) return ""
    val window = textBefore.takeLast(maxChars)
    val lastBoundary = window.indexOfLast { it == '.' || it == '!' || it == '?' || it == '\n' }
    val sentence = if (lastBoundary >= 0) window.substring(lastBoundary + 1) else window
    return sentence.trim()
}
