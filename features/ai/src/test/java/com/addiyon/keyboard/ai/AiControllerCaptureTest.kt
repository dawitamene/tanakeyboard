package com.addiyon.keyboard.ai

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class AiControllerCaptureTest {
    @Test
    fun `capture sends the entire field when there is no selection`() {
        val text = "  First sentence. Second sentence.  "
        val controller = controller(text, AiSource.Field, 1L)

        val input = controller.captureInput()

        assertEquals(text, input.text)
        assertEquals(AiSource.Field, input.source)
        assertEquals(1L, input.snapshot?.captureId)
    }

    @Test
    fun `capture sends only the highlighted range when text is selected`() {
        val controller = controller("chosen text", AiSource.Selection, 2L)

        val input = controller.captureInput()

        assertEquals("chosen text", input.text)
        assertEquals(AiSource.Selection, input.source)
        assertEquals(2L, input.snapshot?.captureId)
    }

    private fun controller(text: String, source: AiSource, captureId: Long): AiController {
        val api = Proxy.newProxyInstance(
            AiApi::class.java.classLoader,
            arrayOf(AiApi::class.java)
        ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as AiApi
        return AiController(
            editor = FakeAiEditor(AiEditorCapture(text, source, AiSnapshot(captureId))),
            repository = AiRepository(api),
            quotaProvider = { AiQuota(0, 50_000, 50_000, "2026-08-08") },
            jwtProvider = { "token" },
            anonIdProvider = { "anonymous" },
            isPrivateFieldProvider = { false }
        )
    }

    private class FakeAiEditor(
        private val capture: AiEditorCapture
    ) : AiEditor {
        override fun captureInput(): AiEditorCapture = capture

        override fun replaceIfCurrent(
            snapshot: AiSnapshot,
            replacement: String
        ): AiEditorReplaceResult = AiEditorReplaceResult.Replaced

        override fun invalidateCaptures() = Unit
    }
}
