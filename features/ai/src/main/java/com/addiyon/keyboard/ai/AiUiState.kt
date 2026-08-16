package com.addiyon.keyboard.ai

import androidx.compose.runtime.Immutable

@Immutable
data class AiUiState(
    val selectedTab: AiToneTab? = null,
    val selectedCustomToneId: String? = null,
    @Deprecated("Use variantResults; strength selection removed")
    val strength: AiStrength = AiStrength.Balanced,
    val input: AiInput? = null,
    val result: AiResult? = null,
    val alternatives: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isResultLoading: Boolean = false,
    val isQuotaLoading: Boolean = false,
    val error: AiError? = null,
    val quota: AiQuota = AiQuota(0, 50_000, 50_000, todayIso()),
    val isPrivateField: Boolean = false,
    val needsAuth: Boolean = false,
    val authEmail: String = "",
    val authSending: Boolean = false,
    val authMessage: String? = null,
    val variantResults: Map<AiStrength, AiResult> = emptyMap(),
    val variantErrors: Map<AiStrength, AiError> = emptyMap(),
    val selectedVariant: AiStrength? = null
) {
    val canRevamp: Boolean
        get() = (selectedTab != null || selectedCustomToneId != null) && !isPrivateField && !needsAuth &&
            (input?.wordCount ?: 0) > 0 && quota.remaining > 0 && !isLoading && !isQuotaLoading

    val inputWordCount: Int get() = input?.wordCount ?: 0
    val hasInput: Boolean get() = input?.text?.isNotBlank() == true
    val hasResult: Boolean get() = result != null || variantResults.isNotEmpty()
    val quotaExceeded: Boolean get() = quota.remaining <= 0

    val effectiveResult: AiResult?
        get() = selectedVariant?.let { variantResults[it] } ?: result ?: variantResults.values.firstOrNull()
}
