package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.asAiUiStrings
import com.addiyon.keyboard.EnglishTextRevampStrings
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class AiUsagePresentationTest {
    @Test
    fun remainingPercentageUsesRemainingQuota() {
        assertEquals(86, aiRemainingPercentage(AiQuota(7_000, 50_000, 43_000, "2026-08-09")))
        assertEquals(33, aiRemainingPercentage(AiQuota(20_000, 30_000, 10_000, "2026-08-09")))
        assertEquals(0, aiRemainingPercentage(AiQuota(0, 0, 0, "2026-08-09")))
    }

    @Test
    fun resetDescriptionUsesTomorrowAndLocalTimeInAddisAbaba() {
        val label = formatAiUsageReset(
            nowMillis = Instant.parse("2026-08-09T10:00:00Z").toEpochMilli(),
            timeZone = TimeZone.getTimeZone("Africa/Addis_Ababa"),
            locale = Locale.US,
            strings = EnglishTextRevampStrings.asAiUiStrings()
        )

        assertEquals("Resets tomorrow at 3:00 AM", label)
    }

    @Test
    fun resetDescriptionUsesTodayWhenUtcMidnightFallsOnTheSameLocalDate() {
        val label = formatAiUsageReset(
            nowMillis = Instant.parse("2026-08-09T17:00:00Z").toEpochMilli(),
            timeZone = TimeZone.getTimeZone("America/Los_Angeles"),
            locale = Locale.US,
            strings = EnglishTextRevampStrings.asAiUiStrings()
        )

        assertEquals("Resets today at 5:00 PM", label)
    }
}
