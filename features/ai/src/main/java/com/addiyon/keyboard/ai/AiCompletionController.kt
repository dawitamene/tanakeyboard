package com.addiyon.keyboard.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.addiyon.keyboard.ui.ai.AiAccountStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiCompletionUiState {
    data object Hidden : AiCompletionUiState
    data object Idle : AiCompletionUiState
    data object Debouncing : AiCompletionUiState
    data object Loading : AiCompletionUiState
    data class Ready(
        val completion: String,
        val capture: AiCompletionCapture
    ) : AiCompletionUiState
}

class AiCompletionController(
    private val editor: AiEditor,
    private val source: AiCompletionSource,
    private val store: AiAccountStore,
    private val isFieldEligible: () -> Boolean,
    private val commitText: (String) -> Boolean,
    private val scope: CoroutineScope,
    private val debounceMilliseconds: Long = DEBOUNCE_MILLISECONDS
) {
    var uiState by mutableStateOf<AiCompletionUiState>(AiCompletionUiState.Hidden)
        private set

    private var requestJob: Job? = null
    private var generation = 0L
    private var activeContextKey: AiCompletionContextKey? = null
    private var suppressedContextKey: AiCompletionContextKey? = null
    private var suppressNextContext = false

    fun onEditorContextChanged(panelVisible: Boolean) {
        if (!isAvailable(panelVisible)) {
            hide()
            return
        }
        val capture = editor.captureCompletionContext()
        if (capture == null) {
            clearRequest()
            activeContextKey = null
            uiState = AiCompletionUiState.Idle
            return
        }
        if (capture.contextKey == activeContextKey) return

        clearRequest()
        activeContextKey = capture.contextKey
        uiState = AiCompletionUiState.Idle
        if (suppressNextContext) {
            suppressNextContext = false
            suppressedContextKey = capture.contextKey
            return
        }
        if (capture.contextKey == suppressedContextKey || !isUseful(capture.prefix)) return

        val expectedGeneration = generation
        uiState = AiCompletionUiState.Debouncing
        requestJob = scope.launch {
            delay(debounceMilliseconds)
            if (!isCurrent(expectedGeneration, capture)) return@launch
            uiState = AiCompletionUiState.Loading
            val result = withContext(Dispatchers.IO) {
                source.complete(
                    prefix = capture.prefix,
                    jwt = store.jwt(),
                    anonId = store.anonymousId()
                )
            }
            if (!isCurrent(expectedGeneration, capture)) return@launch
            val completion = result.getOrNull()
            uiState = if (completion.isNullOrBlank()) {
                AiCompletionUiState.Idle
            } else {
                AiCompletionUiState.Ready(completion, capture)
            }
            requestJob = null
        }
    }

    fun accept(): Boolean {
        val ready = uiState as? AiCompletionUiState.Ready ?: return false
        if (!editor.isCompletionCaptureCurrent(ready.capture.snapshot)) {
            hide()
            return false
        }
        suppressNextContext = true
        val committed = commitText(ready.completion)
        if (!committed) suppressNextContext = false
        uiState = AiCompletionUiState.Idle
        return committed
    }

    fun dismiss() {
        suppressedContextKey = activeContextKey
        clearRequest()
        uiState = if (activeContextKey == null) {
            AiCompletionUiState.Hidden
        } else {
            AiCompletionUiState.Idle
        }
    }

    fun reset() {
        hide()
        editor.invalidateCaptures()
        suppressedContextKey = null
        suppressNextContext = false
    }

    private fun isAvailable(panelVisible: Boolean): Boolean =
        !panelVisible &&
            store.phraseCompletionsEnabled() &&
            store.phraseCompletionConsentVersion() >=
                CURRENT_PHRASE_COMPLETION_CONSENT_VERSION &&
            !store.jwt().isNullOrBlank() &&
            isFieldEligible()

    private fun isCurrent(
        expectedGeneration: Long,
        capture: AiCompletionCapture
    ): Boolean =
        expectedGeneration == generation &&
            activeContextKey == capture.contextKey &&
            editor.isCompletionCaptureCurrent(capture.snapshot)

    private fun isUseful(prefix: String): Boolean {
        val trimmed = prefix.trim()
        if (trimmed.length < MINIMUM_CONTEXT_CHARACTERS) return false
        if (trimmed.split(Regex("\\s+")).size < MINIMUM_CONTEXT_WORDS) return false
        if (URL.matches(trimmed) || EMAIL.matches(trimmed)) return false
        return trimmed.any(Char::isLetter)
    }

    private fun hide() {
        clearRequest()
        activeContextKey = null
        uiState = AiCompletionUiState.Hidden
    }

    private fun clearRequest() {
        generation += 1
        requestJob?.cancel()
        requestJob = null
    }

    private companion object {
        const val DEBOUNCE_MILLISECONDS = 300L
        const val MINIMUM_CONTEXT_CHARACTERS = 12
        const val MINIMUM_CONTEXT_WORDS = 2
        val URL = Regex("^(?:https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
