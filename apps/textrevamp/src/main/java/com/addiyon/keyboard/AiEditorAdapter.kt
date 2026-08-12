package com.addiyon.keyboard

import com.addiyon.keyboard.ai.AiEditor
import com.addiyon.keyboard.ai.AiEditorCapture
import com.addiyon.keyboard.ai.AiEditorReplaceResult
import com.addiyon.keyboard.ai.AiSnapshot
import com.addiyon.keyboard.ai.AiSource

internal class AiEditorAdapter(
    private val gateway: EditorGateway
) : AiEditor {
    private var nextCaptureId = 0L
    private val captures = mutableMapOf<Long, ReplacementTarget>()

    override fun captureInput(): AiEditorCapture? {
        val selected = gateway.selectedText(optional = false)
        if (selected != null && selected.value.isNotEmpty()) {
            return capture(
                text = selected.value,
                source = AiSource.Selection,
                target = ReplacementTarget.Selection(selected.token, selected.value)
            )
        }
        val field = gateway.surroundingText(
            beforeChars = MAX_FIELD_SIDE_CHARS,
            afterChars = MAX_FIELD_SIDE_CHARS,
            optional = false
        ) ?: return null
        val surrounding = field.value
        if (surrounding.selectedText.isNotEmpty()) {
            return capture(
                text = surrounding.selectedText,
                source = AiSource.Selection,
                target = ReplacementTarget.Selection(field.token, surrounding.selectedText)
            )
        }
        if (
            surrounding.offset != 0 ||
            surrounding.text.isBlank() ||
            surrounding.textBeforeSelection.length == MAX_FIELD_SIDE_CHARS ||
            surrounding.textAfterSelection.length == MAX_FIELD_SIDE_CHARS
        ) {
            return null
        }
        return capture(
            text = surrounding.text,
            source = AiSource.Field,
            target = ReplacementTarget.Field(
                token = field.token,
                textBeforeCursor = surrounding.textBeforeSelection,
                textAfterCursor = surrounding.textAfterSelection
            )
        )
    }

    override fun replaceIfCurrent(
        snapshot: AiSnapshot,
        replacement: String
    ): AiEditorReplaceResult {
        val target = captures[snapshot.captureId] ?: return AiEditorReplaceResult.Failed
        if (!gateway.isCurrentSession(target.token)) {
            return AiEditorReplaceResult.TextChanged
        }
        if (!gateway.revalidateSelection(target.token)) {
            return AiEditorReplaceResult.SelectionChanged
        }
        val result = when (target) {
            is ReplacementTarget.Selection -> replaceSelection(target, replacement)
            is ReplacementTarget.Field -> replaceField(target, replacement)
        }
        if (result == AiEditorReplaceResult.Replaced) {
            captures.remove(snapshot.captureId)
        }
        return result
    }

    override fun invalidateCaptures() {
        captures.clear()
    }

    private fun replaceSelection(
        target: ReplacementTarget.Selection,
        replacement: String
    ): AiEditorReplaceResult {
        val selected = gateway.selectedText(optional = false)
            ?: return AiEditorReplaceResult.SelectionChanged
        if (selected.value != target.text) return AiEditorReplaceResult.TextChanged
        return if (gateway.commitText(replacement, selected.token)) {
            AiEditorReplaceResult.Replaced
        } else {
            AiEditorReplaceResult.Failed
        }
    }

    private fun replaceField(
        target: ReplacementTarget.Field,
        replacement: String
    ): AiEditorReplaceResult {
        val field = gateway.surroundingText(
            beforeChars = MAX_FIELD_SIDE_CHARS,
            afterChars = MAX_FIELD_SIDE_CHARS,
            optional = false
        ) ?: return AiEditorReplaceResult.TextChanged
        val surrounding = field.value
        if (
            surrounding.offset != 0 ||
            surrounding.selectedText.isNotEmpty() ||
            surrounding.textBeforeSelection != target.textBeforeCursor ||
            surrounding.textAfterSelection != target.textAfterCursor
        ) {
            return AiEditorReplaceResult.TextChanged
        }
        val replaced = gateway.write(field.token) { connection ->
            connection.beginBatchEdit()
            try {
                connection.deleteSurroundingText(
                    target.textBeforeCursor.length,
                    target.textAfterCursor.length
                ) && connection.commitText(replacement, 1)
            } finally {
                connection.endBatchEdit()
            }
        }
        return if (replaced) AiEditorReplaceResult.Replaced else AiEditorReplaceResult.Failed
    }

    private fun capture(
        text: String,
        source: AiSource,
        target: ReplacementTarget
    ): AiEditorCapture {
        val id = ++nextCaptureId
        captures.clear()
        captures[id] = target
        return AiEditorCapture(text, source, AiSnapshot(id))
    }

    private sealed interface ReplacementTarget {
        val token: EditorToken

        data class Selection(
            override val token: EditorToken,
            val text: String
        ) : ReplacementTarget

        data class Field(
            override val token: EditorToken,
            val textBeforeCursor: String,
            val textAfterCursor: String
        ) : ReplacementTarget
    }

    private companion object {
        const val MAX_FIELD_SIDE_CHARS = 32_768
    }
}
