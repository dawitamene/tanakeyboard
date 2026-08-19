package com.addiyon.keyboard.ai

import com.addiyon.keyboard.ui.ai.AiAccountStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCompletionControllerTest {
    @Test
    fun completionIsHiddenUntilEnabledAndConsented() {
        val editor = FakeCompletionEditor(capture("I will send the draft", 1))
        val store = FakeAccountStore(enabled = false, consentVersion = 0)
        val scope = testScope()
        val controller = controller(editor, store, RecordingSource(), scope)

        controller.onEditorContextChanged(panelVisible = false)

        assertTrue(controller.uiState is AiCompletionUiState.Hidden)
        scope.cancel()
    }

    @Test
    fun readyCompletionIsRevalidatedAndCommittedExactly() = runBlocking {
        val editor = FakeCompletionEditor(capture("I will send the draft", 1))
        val source = RecordingSource()
        val committed = mutableListOf<String>()
        val scope = testScope()
        val controller = controller(
            editor = editor,
            store = FakeAccountStore(),
            source = source,
            scope = scope,
            commit = { committed += it; true }
        )

        controller.onEditorContextChanged(panelVisible = false)
        awaitRequest(source, "I will send the draft")
        source.complete("I will send the draft", " before the meeting.")
        awaitState(controller) { it is AiCompletionUiState.Ready }

        assertTrue(controller.accept())
        assertEquals(listOf(" before the meeting."), committed)
        scope.cancel()
    }

    @Test
    fun lateResponseCannotReplaceTheNewContext() = runBlocking {
        val first = capture("I will send the draft", 1)
        val second = capture("I will send the final draft", 2)
        val editor = FakeCompletionEditor(first)
        val source = RecordingSource()
        val scope = testScope()
        val controller = controller(editor, FakeAccountStore(), source, scope)

        controller.onEditorContextChanged(panelVisible = false)
        awaitRequest(source, first.prefix)
        editor.current = second
        controller.onEditorContextChanged(panelVisible = false)
        awaitRequest(source, second.prefix)
        source.complete(first.prefix, " too late.")
        delay(20)
        assertFalse(
            (controller.uiState as? AiCompletionUiState.Ready)
                ?.completion == " too late."
        )
        source.complete(second.prefix, " before lunch.")
        awaitState(controller) {
            it is AiCompletionUiState.Ready && it.completion == " before lunch."
        }
        scope.cancel()
    }

    private fun controller(
        editor: FakeCompletionEditor,
        store: FakeAccountStore,
        source: RecordingSource,
        scope: CoroutineScope,
        commit: (String) -> Boolean = { true }
    ) = AiCompletionController(
        editor = editor,
        source = source,
        store = store,
        isFieldEligible = { true },
        commitText = commit,
        scope = scope,
        debounceMilliseconds = 0
    )

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private suspend fun awaitRequest(source: RecordingSource, prefix: String) {
        withTimeout(1_000) {
            while (prefix !in source.requests) delay(5)
        }
    }

    private suspend fun awaitState(
        controller: AiCompletionController,
        predicate: (AiCompletionUiState) -> Boolean
    ) {
        withTimeout(1_000) {
            while (!predicate(controller.uiState)) delay(5)
        }
    }

    private fun capture(prefix: String, id: Long) = AiCompletionCapture(
        prefix = prefix,
        contextKey = AiCompletionContextKey(1, prefix),
        snapshot = AiCompletionSnapshot(id)
    )
}

private class RecordingSource : AiCompletionSource {
    val requests = mutableSetOf<String>()
    private val responses = mutableMapOf<String, CompletableDeferred<Result<String?>>>()

    override suspend fun complete(prefix: String, jwt: String?, anonId: String): Result<String?> {
        requests += prefix
        return responses.getOrPut(prefix) { CompletableDeferred() }.await()
    }

    fun complete(prefix: String, completion: String?) {
        responses.getOrPut(prefix) { CompletableDeferred() }
            .complete(Result.success(completion))
    }
}

private class FakeCompletionEditor(
    var current: AiCompletionCapture?
) : AiEditor {
    override fun captureInput(): AiEditorCapture? = null
    override fun replaceIfCurrent(
        snapshot: AiSnapshot,
        replacement: String
    ): AiEditorReplaceResult = AiEditorReplaceResult.Failed

    override fun captureCompletionContext(): AiCompletionCapture? = current

    override fun isCompletionCaptureCurrent(snapshot: AiCompletionSnapshot): Boolean =
        current?.snapshot == snapshot

    override fun invalidateCaptures() = Unit
}

private class FakeAccountStore(
    private var enabled: Boolean = true,
    private var consentVersion: Int = CURRENT_PHRASE_COMPLETION_CONSENT_VERSION
) : AiAccountStore {
    override fun jwt(): String = "jwt"
    override fun setJwt(value: String?) = Unit
    override fun email(): String? = null
    override fun setEmail(value: String?) = Unit
    override fun anonymousId(): String = "anonymous"
    override fun quota(): AiQuota = AiQuota(0, 1_000, 1_000, "2026-08-13")
    override fun saveQuota(quota: AiQuota) = Unit
    override fun clearJwt() = Unit
    override fun phraseCompletionsEnabled(): Boolean = enabled
    override fun setPhraseCompletionsEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    override fun phraseCompletionConsentVersion(): Int = consentVersion
    override fun setPhraseCompletionConsentVersion(version: Int) {
        consentVersion = version
    }
    override fun customTones(): List<CustomTone> = emptyList()
    override fun setCustomTones(tones: List<CustomTone>) = Unit
    override fun addCustomTone(
        title: String,
        instruction: String,
        icon: String,
        color: String
    ): CustomTone? = null
    override fun updateCustomTone(
        id: String,
        title: String,
        instruction: String,
        icon: String,
        color: String
    ): CustomTone? = null
    override fun removeCustomTone(id: String) = Unit
    override fun registerCustomToneChangeListener(listener: () -> Unit) = Unit
    override fun unregisterCustomToneChangeListener(listener: () -> Unit) = Unit
    override fun toneOrder(): List<String> = emptyList()
    override fun setToneOrder(order: List<String>) = Unit
    override fun registerToneOrderChangeListener(listener: () -> Unit) = Unit
    override fun unregisterToneOrderChangeListener(listener: () -> Unit) = Unit
}
