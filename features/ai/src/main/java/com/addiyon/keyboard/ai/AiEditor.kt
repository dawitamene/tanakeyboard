package com.addiyon.keyboard.ai

interface AiEditor {
    fun captureInput(): AiEditorCapture?
    fun replaceIfCurrent(snapshot: AiSnapshot, replacement: String): AiEditorReplaceResult
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
