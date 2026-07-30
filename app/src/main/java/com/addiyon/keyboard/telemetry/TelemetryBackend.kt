package com.addiyon.keyboard.telemetry

enum class TelemetryLanguage(internal val wireName: String) {
    AMHARIC("amharic"),
    ENGLISH("english")
}

enum class TelemetryLayout(internal val wireName: String) {
    LETTERS("letters"),
    NUMBERS("numbers"),
    SYMBOLS("symbols"),
    KEYPAD("keypad"),
    EMOJI("emoji")
}

enum class TelemetrySuggestionKind(internal val wireName: String) {
    COMPLETION("completion"),
    PREDICTION("prediction")
}

enum class TelemetryVoiceResult(internal val wireName: String) {
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    ERROR("error")
}

enum class TelemetryVoiceError(internal val wireName: String) {
    PERMISSION("permission"),
    UNAVAILABLE("unavailable"),
    NETWORK("network"),
    SILENCE("silence"),
    BUSY("busy"),
    OTHER("other")
}

enum class TelemetrySetting(internal val wireName: String) {
    VIBRATION("vibration"),
    SOUND("sound"),
    NUMBER_ROW("number_row")
}

internal sealed interface TelemetryEvent {
    data object AnalyticsFirstEnable : TelemetryEvent
    data class ImeSessionStart(val language: TelemetryLanguage) : TelemetryEvent
    data class LanguageSwitch(val destination: TelemetryLanguage) : TelemetryEvent
    data class LayoutOpen(val layout: TelemetryLayout) : TelemetryEvent
    data class SuggestionAccept(val kind: TelemetrySuggestionKind) : TelemetryEvent
    data class VoiceStart(val language: TelemetryLanguage) : TelemetryEvent
    data class VoiceFinish(
        val result: TelemetryVoiceResult,
        val error: TelemetryVoiceError?
    ) : TelemetryEvent
    data object OnboardingComplete : TelemetryEvent
    data class SettingChange(
        val setting: TelemetrySetting,
        val enabled: Boolean
    ) : TelemetryEvent
}

internal interface TelemetryBackend {
    val available: Boolean
    fun setAnalyticsCollectionEnabled(enabled: Boolean)
    fun resetAnalyticsData()
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
    fun deleteUnsentReports()
    fun log(event: TelemetryEvent)
    fun record(report: SanitizedNonFatal)
}

internal object NoOpTelemetryBackend : TelemetryBackend {
    override val available = false
    override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
    override fun resetAnalyticsData() = Unit
    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) = Unit
    override fun deleteUnsentReports() = Unit
    override fun log(event: TelemetryEvent) = Unit
    override fun record(report: SanitizedNonFatal) = Unit
}

internal object TelemetrySchema {
    const val ANALYTICS_FIRST_ENABLE = "analytics_first_enable"
    const val IME_SESSION_START = "ime_session_start"
    const val LANGUAGE_SWITCH = "language_switch"
    const val LAYOUT_OPEN = "layout_open"
    const val SUGGESTION_ACCEPT = "suggestion_accept"
    const val VOICE_START = "voice_start"
    const val VOICE_FINISH = "voice_finish"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val SETTING_CHANGE = "setting_change"

    const val LANGUAGE = "language"
    const val DESTINATION = "destination"
    const val LAYOUT = "layout"
    const val KIND = "kind"
    const val RESULT = "result"
    const val ERROR_CATEGORY = "error_category"
    const val SETTING = "setting"
    const val ENABLED = "enabled"

    val events: Map<String, Set<String>> = mapOf(
        ANALYTICS_FIRST_ENABLE to emptySet(),
        IME_SESSION_START to setOf(LANGUAGE),
        LANGUAGE_SWITCH to setOf(DESTINATION),
        LAYOUT_OPEN to setOf(LAYOUT),
        SUGGESTION_ACCEPT to setOf(KIND),
        VOICE_START to setOf(LANGUAGE),
        VOICE_FINISH to setOf(RESULT, ERROR_CATEGORY),
        ONBOARDING_COMPLETE to emptySet(),
        SETTING_CHANGE to setOf(SETTING, ENABLED)
    )
}
