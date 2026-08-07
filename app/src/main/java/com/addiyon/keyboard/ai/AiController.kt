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
        val selected = editorGateway.selectedText(optional = true)?.value
        if (!selected.isNullOrBlank()) {
            val trimmed = selected.trim()
            val snapshot = buildSelectionSnapshot()
            return AiInput(trimmed, countWords(trimmed), AiSource.Selection, snapshot)
        }
        val surrounding = editorGateway.surroundingText(beforeChars = 400, afterChars = 50, optional = true)
        if (surrounding != null) {
            val before = surrounding.value.textBeforeSelection
            val after = surrounding.value.textAfterSelection
            val cursorText = before
            val sentence = extractSentence(cursorText)
            if (sentence.isNotBlank()) {
                val snapshot = buildSentenceSnapshot(surrounding.value.textBeforeSelection, sentence)
                return AiInput(sentence, countWords(sentence), AiSource.Sentence, snapshot)
            }
            val fallback = cursorText.trim().split(Regex("\\s+")).takeLast(40).joinToString(" ").trim()
            if (fallback.isNotBlank()) {
                val snapshot = buildSentenceSnapshot(surrounding.value.textBeforeSelection, fallback)
                return AiInput(fallback, countWords(fallback), AiSource.Sentence, snapshot)
            }
        }
        val beforeCursor = editorGateway.textBeforeCursor(400, optional = true)?.value ?: ""
        val sentence = extractSentence(beforeCursor)
        if (sentence.isNotBlank()) {
            return AiInput(sentence, countWords(sentence), AiSource.Sentence, null)
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

    private fun buildSentenceSnapshot(fullBefore: String, sentence: String): AiSnapshot? {
        val token = editorGateway.currentToken() ?: return null
        val sel = token.selectionStart
        if (sel < 0) return null
        val idx = fullBefore.lastIndexOf(sentence)
        if (idx < 0) return null
        val absoluteStart = token.selectionStart - (fullBefore.length - idx)
        val absoluteEnd = absoluteStart + sentence.length
        if (absoluteStart < 0) return null
        return AiSnapshot(absoluteStart, absoluteEnd, token.generation, token.selectionGeneration)
    }

    suspend fun revamp(input: AiInput, tab: AiToneTab, strength: AiStrength = AiStrength.Balanced): Result<AiResult> {
        if (isPrivateFieldProvider()) return Result.failure(Exception(AiError.PrivateField.toString()))
        if (input.text.isBlank()) return Result.failure(Exception(AiError.NoText.toString()))
        val quota = quotaProvider()
        if (quota.remaining <= 0 || input.wordCount > quota.remaining) {
            return Result.failure(Exception(AiError.QuotaExceeded(quota.remaining).toString()))
        }
        val jwt = jwtProvider()
        val anonId = anonIdProvider()
        return repository.revamp(input.text, tab, strength, jwt, anonId)
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
            else -> repository.parseAiError(t)
        }
    }
}

private val unusedAiControllerSentinel = Unit
