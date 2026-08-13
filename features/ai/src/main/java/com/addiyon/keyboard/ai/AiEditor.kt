package com.addiyon.keyboard.ai

interface AiEditor {
    fun captureInput(): AiEditorCapture?
    fun replaceIfCurrent(snapshot: AiSnapshot, replacement: String): AiEditorReplaceResult
    fun captureCompletionContext(): AiCompletionCapture? = null
    fun isCompletionCaptureCurrent(snapshot: AiCompletionSnapshot): Boolean = false
    fun invalidateCaptures()
}

data class AiEditorCapture(
    val text: String,
    val source: AiSource,
    val snapshot: AiSnapshot
)

enum class AiEditorReplaceResult {
    Replaced,
    TextChanged,
    SelectionChanged,
    Failed
}

data class AiCompletionCapture(
    val prefix: String,
    val contextKey: AiCompletionContextKey,
    val snapshot: AiCompletionSnapshot
)

data class AiCompletionContextKey(
    val sessionId: Long,
    val prefix: String
)

@JvmInline
value class AiCompletionSnapshot(val captureId: Long)
