package com.addiyon.keyboard.ai

import androidx.compose.runtime.Immutable

@Immutable
data class AiUiState(
    val isVisible: Boolean = false,
    val selectedTab: AiToneTab = AiToneTab.Humanize,
    val strength: AiStrength = AiStrength.Balanced,
    val input: AiInput? = null,
    val result: AiResult? = null,
    val alternatives: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: AiError? = null,
    val quota: AiQuota = AiQuota(0, 800, 800, todayIso()),
    val isPrivateField: Boolean = false,
    val needsAuth: Boolean = false,
    val authEmail: String = "",
    val authSending: Boolean = false,
    val authMessage: String? = null
) {
    val canRevamp: Boolean
        get() = !isPrivateField && !needsAuth && (input?.wordCount ?: 0) > 0 && quota.remaining > 0 && !isLoading

    val inputWordCount: Int get() = input?.wordCount ?: 0
    val hasInput: Boolean get() = input?.text?.isNotBlank() == true
    val hasResult: Boolean get() = result != null
    val quotaExceeded: Boolean get() = quota.remaining <= 0
}
