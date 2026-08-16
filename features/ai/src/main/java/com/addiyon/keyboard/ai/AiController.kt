package com.addiyon.keyboard.ai

class AiController(
    private val editor: AiEditor,
    private val repository: AiRepository,
    private val quotaProvider: () -> AiQuota,
    private val jwtProvider: () -> String?,
    private val anonIdProvider: () -> String,
    private val isPrivateFieldProvider: () -> Boolean
) {
    fun captureInput(): AiInput {
        if (isPrivateFieldProvider()) {
            return AiInput("", 0, AiSource.Empty, null)
        }
        val capture = editor.captureInput() ?: return AiInput("", 0, AiSource.Empty, null)
        return AiInput(
            text = capture.text,
            wordCount = countWords(capture.text),
            source = capture.source,
            snapshot = capture.snapshot
        )
    }

    suspend fun revamp(input: AiInput, tab: AiToneTab, strength: AiStrength = AiStrength.Balanced): Result<AiResult> {
        if (isPrivateFieldProvider()) return Result.failure(Exception(AiError.PrivateField.toString()))
        if (input.text.isBlank()) return Result.failure(Exception(AiError.NoText.toString()))
        val quota = quotaProvider()
        if (quota.remaining <= 0) {
            return Result.failure(Exception(AiError.QuotaExceeded(quota.remaining).toString()))
        }
        val jwt = jwtProvider()
        val anonId = anonIdProvider()
        return repository.revamp(input.text, tab, strength, jwt, anonId)
    }

    suspend fun revampVariants(input: AiInput, tab: AiToneTab): Map<AiStrength, Result<AiResult>> =
        requestVariants(input, tab.tone, tab.instruction)

    suspend fun revampCustomVariants(
        input: AiInput,
        instruction: String
    ): Map<AiStrength, Result<AiResult>> =
        requestVariants(input, CUSTOM_TONE_DEFAULT_TONE, instruction)

    private suspend fun requestVariants(
        input: AiInput,
        tone: String,
        instruction: String?
    ): Map<AiStrength, Result<AiResult>> {
        if (isPrivateFieldProvider()) {
            val err = Result.failure<AiResult>(Exception(AiError.PrivateField.toString()))
            return AiStrength.entries.associateWith { err }
        }
        if (input.text.isBlank()) {
            val err = Result.failure<AiResult>(Exception(AiError.NoText.toString()))
            return AiStrength.entries.associateWith { err }
        }
        val quota = quotaProvider()
        if (quota.remaining <= 0) {
            val err = Result.failure<AiResult>(Exception(AiError.QuotaExceeded(quota.remaining).toString()))
            return AiStrength.entries.associateWith { err }
        }
        val jwt = jwtProvider()
        val anonId = anonIdProvider()
        return repository.revampVariants(input.text, tone, instruction, jwt, anonId).fold(
            onSuccess = { variants ->
                AiStrength.entries.associateWith { strength ->
                    variants[strength]
                        ?.let { Result.success(it) }
                        ?: Result.failure(Exception(AiError.Unknown.toString()))
                }
            },
            onFailure = { throwable ->
                AiStrength.entries.associateWith { Result.failure(throwable) }
            }
        )
    }

    fun replaceIfCurrent(snapshot: AiSnapshot, replacement: String): AiEditorReplaceResult =
        editor.replaceIfCurrent(snapshot, replacement)

    fun invalidateEditorCaptures() = editor.invalidateCaptures()

    suspend fun loadQuota(): Result<AiQuota> = repository.quota(
        jwt = jwtProvider(),
        anonId = anonIdProvider()
    )

    fun quotaRemaining(): AiQuota = quotaProvider()

    fun parseError(t: Throwable): AiError {
        val msg = t.message ?: ""
        return when {
            msg.contains("PrivateField") -> AiError.PrivateField
            msg.contains("NoText") -> AiError.NoText
            msg.contains("NeedsAuth") -> AiError.NeedsAuth
            msg.contains("QuotaExceeded") ->
                AiError.QuotaExceeded(quotaRemainingFromError(msg) ?: 0)
            msg.contains("Offline") -> AiError.Offline
            msg.contains("Unknown") -> AiError.Unknown
            else -> repository.parseAiError(t)
        }
    }
}
