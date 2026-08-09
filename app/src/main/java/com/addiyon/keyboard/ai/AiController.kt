package com.addiyon.keyboard.ai

import com.addiyon.keyboard.EditorGateway

internal class AiController(
    private val editorGateway: EditorGateway,
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
        val selected = editorGateway.selectedText(optional = false)?.value
        if (!selected.isNullOrEmpty()) {
            val snapshot = buildSelectionSnapshot()
            return AiInput(selected, countWords(selected), AiSource.Selection, snapshot)
        }
        val field = editorGateway.surroundingText(
            beforeChars = Int.MAX_VALUE,
            afterChars = Int.MAX_VALUE,
            optional = false
        )?.value
        if (field != null && field.offset == 0 && field.text.isNotBlank()) {
            val snapshot = buildFieldSnapshot(field.text.length)
            return AiInput(field.text, countWords(field.text), AiSource.Field, snapshot)
        }
        return AiInput("", 0, AiSource.Empty, null)
    }

    private fun buildSelectionSnapshot(): AiSnapshot? {
        val token = editorGateway.currentToken() ?: return null
        val selStart = token.selectionStart
        val selEnd = token.selectionEnd
        if (selStart < 0 || selEnd < 0 || selStart == selEnd) return null
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        return AiSnapshot(start, end, token.generation, token.selectionGeneration)
    }

    private fun buildFieldSnapshot(fieldLength: Int): AiSnapshot? {
        val token = editorGateway.currentToken() ?: return null
        if (token.selectionStart !in 0..fieldLength || token.selectionEnd !in 0..fieldLength) return null
        return AiSnapshot(0, fieldLength, token.generation, token.selectionGeneration)
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

    suspend fun revampVariants(input: AiInput, tab: AiToneTab): Map<AiStrength, Result<AiResult>> {
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
        return repository.revampVariants(input.text, tab, jwt, anonId).fold(
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

    fun isReplaceValid(snapshot: AiSnapshot?): Boolean {
        if (snapshot == null) return false
        val token = editorGateway.currentToken() ?: return false
        return token.generation == snapshot.tokenGeneration
    }

    fun quotaRemaining(): AiQuota = quotaProvider()

    fun parseError(t: Throwable): AiError {
        val msg = t.message ?: ""
        return when {
            msg.contains("PrivateField") -> AiError.PrivateField
            msg.contains("NoText") -> AiError.NoText
            msg.contains("NeedsAuth") -> AiError.NeedsAuth
            msg.contains("QuotaExceeded") -> AiError.QuotaExceeded()
            msg.contains("Offline") -> AiError.Offline
            msg.contains("Unknown") -> AiError.Unknown
            else -> repository.parseAiError(t)
        }
    }
}

private val unusedAiControllerSentinel = Unit
