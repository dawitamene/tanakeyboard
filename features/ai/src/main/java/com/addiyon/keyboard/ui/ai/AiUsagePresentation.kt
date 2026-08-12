package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiQuota
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

fun aiRemainingPercentage(quota: AiQuota): Int {
    if (quota.limit <= 0) return 0
    val remaining = quota.remaining.coerceIn(0, quota.limit)
    return (remaining.toDouble() * 100 / quota.limit).roundToInt()
}

fun formatAiUsageReset(
    nowMillis: Long,
    timeZone: TimeZone,
    locale: Locale,
    strings: AiUiStrings
): String {
    val resetMillis = nextUtcDailyResetMillis(nowMillis)
    val now = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
    val reset = Calendar.getInstance(timeZone, locale).apply { timeInMillis = resetMillis }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
        this.timeZone = timeZone
    }.format(Date(resetMillis))
        .replace('\u202f', ' ')
        .replace('\u00a0', ' ')

    if (now.isSameLocalDay(reset)) {
        return strings.aiUsageResetTodayFormat.format(time)
    }

    val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    if (tomorrow.isSameLocalDay(reset)) {
        return strings.aiUsageResetTomorrowFormat.format(time)
    }

    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
        this.timeZone = timeZone
    }.format(Date(resetMillis))
    return strings.aiUsageResetDateFormat.format(date, time)
}

private fun nextUtcDailyResetMillis(nowMillis: Long): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

private fun Calendar.isSameLocalDay(other: Calendar): Boolean =
    get(Calendar.ERA) == other.get(Calendar.ERA) &&
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
