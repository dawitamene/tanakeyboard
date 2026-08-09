package com.addiyon.keyboard.ai

import com.addiyon.keyboard.EditorGateway
import com.addiyon.keyboard.testing.FakeEditor
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class AiControllerCaptureTest {
    @Test
    fun `capture sends the entire field when there is no selection`() {
        val text = "  First sentence. Second sentence.  "
        val controller = controller(text, 9, 9)

        val input = controller.captureInput()

        assertEquals(text, input.text)
        assertEquals(AiSource.Field, input.source)
        assertEquals(0, input.snapshot?.replacementStart)
        assertEquals(text.length, input.snapshot?.replacementEnd)
    }

    @Test
    fun `capture sends only the highlighted range when text is selected`() {
        val text = "Prefix chosen text suffix"
        val selectionStart = text.indexOf("chosen")
        val selectionEnd = selectionStart + "chosen text".length
        val controller = controller(text, selectionStart, selectionEnd)

        val input = controller.captureInput()

        assertEquals("chosen text", input.text)
        assertEquals(AiSource.Selection, input.source)
        assertEquals(selectionStart, input.snapshot?.replacementStart)
        assertEquals(selectionEnd, input.snapshot?.replacementEnd)
    }

    private fun controller(text: String, selectionStart: Int, selectionEnd: Int): AiController {
        val editor = FakeEditor(text, initialSelection = selectionStart)
        editor.setSelection(selectionStart, selectionEnd)
        val gateway = EditorGateway { editor }
        gateway.beginSession(selectionStart, selectionEnd)
        val api = Proxy.newProxyInstance(
            AiApi::class.java.classLoader,
            arrayOf(AiApi::class.java)
        ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as AiApi
        return AiController(
            editorGateway = gateway,
            repository = AiRepository(api),
            quotaProvider = { AiQuota(0, 50, 50, "2026-08-08") },
            jwtProvider = { "token" },
            anonIdProvider = { "anonymous" },
            isPrivateFieldProvider = { false }
        )
    }
}
