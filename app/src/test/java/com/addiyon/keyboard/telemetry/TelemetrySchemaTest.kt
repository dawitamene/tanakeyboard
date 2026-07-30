package com.addiyon.keyboard.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySchemaTest {
    @Test
    fun eventAndParameterNamesAreFixedAndLegal() {
        val legalName = Regex("[a-z][a-z0-9_]{0,39}")
        val reservedPrefixes = listOf("firebase_", "google_", "ga_")

        assertTrue(TelemetrySchema.events.isNotEmpty())
        TelemetrySchema.events.forEach { (event, parameters) ->
            assertTrue(event, legalName.matches(event))
            assertFalse(event, reservedPrefixes.any(event::startsWith))
            assertTrue(event, parameters.size <= 25)
            parameters.forEach { parameter ->
                assertTrue(parameter, legalName.matches(parameter))
                assertFalse(parameter, reservedPrefixes.any(parameter::startsWith))
            }
        }
    }

    @Test
    fun schemaContainsOnlyTheReviewedEvents() {
        assertTrue(
            TelemetrySchema.events.keys == setOf(
                "analytics_first_enable",
                "ime_session_start",
                "language_switch",
                "layout_open",
                "suggestion_accept",
                "voice_start",
                "voice_finish",
                "onboarding_complete",
                "setting_change"
            )
        )
    }

    @Test
    fun typedEventsAndEnumWireValuesCoverTheReviewedSurface() {
        assertEquals(listOf("amharic", "english"), TelemetryLanguage.entries.map { it.wireName })
        assertEquals(
            listOf("letters", "numbers", "symbols", "keypad", "emoji"),
            TelemetryLayout.entries.map { it.wireName }
        )
        assertEquals(
            listOf("completion", "prediction"),
            TelemetrySuggestionKind.entries.map { it.wireName }
        )
        assertEquals(
            listOf("completed", "cancelled", "error"),
            TelemetryVoiceResult.entries.map { it.wireName }
        )
        assertEquals(
            listOf("permission", "unavailable", "network", "silence", "busy", "other"),
            TelemetryVoiceError.entries.map { it.wireName }
        )
        assertEquals(
            listOf("vibration", "sound", "number_row"),
            TelemetrySetting.entries.map { it.wireName }
        )

        val session = TelemetryEvent.ImeSessionStart(TelemetryLanguage.AMHARIC)
        val language = TelemetryEvent.LanguageSwitch(TelemetryLanguage.ENGLISH)
        val layout = TelemetryEvent.LayoutOpen(TelemetryLayout.KEYPAD)
        val suggestion = TelemetryEvent.SuggestionAccept(TelemetrySuggestionKind.PREDICTION)
        val voiceStart = TelemetryEvent.VoiceStart(TelemetryLanguage.ENGLISH)
        val voiceFinish = TelemetryEvent.VoiceFinish(
            TelemetryVoiceResult.ERROR,
            TelemetryVoiceError.NETWORK
        )
        val setting = TelemetryEvent.SettingChange(TelemetrySetting.SOUND, enabled = true)

        assertEquals(TelemetryLanguage.AMHARIC, session.language)
        assertEquals(TelemetryLanguage.ENGLISH, language.destination)
        assertEquals(TelemetryLayout.KEYPAD, layout.layout)
        assertEquals(TelemetrySuggestionKind.PREDICTION, suggestion.kind)
        assertEquals(TelemetryLanguage.ENGLISH, voiceStart.language)
        assertEquals(TelemetryVoiceResult.ERROR, voiceFinish.result)
        assertEquals(TelemetryVoiceError.NETWORK, voiceFinish.error)
        assertEquals(TelemetrySetting.SOUND, setting.setting)
        assertTrue(setting.enabled)
        assertEquals(TelemetryEvent.AnalyticsFirstEnable, TelemetryEvent.AnalyticsFirstEnable)
        assertEquals(TelemetryEvent.OnboardingComplete, TelemetryEvent.OnboardingComplete)
    }

    @Test
    fun noOpBackendAcceptsEveryTypedOperation() {
        val report = SanitizedNonFatal(
            category = NonFatalCategory.EDITOR,
            throwableClass = CoarseThrowableClass.ILLEGAL_STATE,
            frames = emptyList()
        )

        assertFalse(NoOpTelemetryBackend.available)
        NoOpTelemetryBackend.setAnalyticsCollectionEnabled(true)
        NoOpTelemetryBackend.resetAnalyticsData()
        NoOpTelemetryBackend.setCrashlyticsCollectionEnabled(true)
        NoOpTelemetryBackend.deleteUnsentReports()
        NoOpTelemetryBackend.log(TelemetryEvent.OnboardingComplete)
        NoOpTelemetryBackend.record(report)
    }
}
