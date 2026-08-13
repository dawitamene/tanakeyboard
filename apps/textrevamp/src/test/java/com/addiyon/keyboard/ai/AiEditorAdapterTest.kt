package com.addiyon.keyboard

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import com.addiyon.keyboard.ai.AiEditorReplaceResult
import com.addiyon.keyboard.ai.AiSource
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEditorAdapterTest {
    @Test
    fun `selected text is revalidated and replaced with commitText`() {
        val editor = MutableEditor("prefix chosen suffix", 7, 13)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(7, 13)
        val adapter = AiEditorAdapter(gateway)

        val capture = requireNotNull(adapter.captureInput())
        val result = adapter.replaceIfCurrent(capture.snapshot, "better")

        assertEquals(AiSource.Selection, capture.source)
        assertEquals("chosen", capture.text)
        assertEquals(AiEditorReplaceResult.Replaced, result)
        assertEquals("prefix better suffix", editor.text)
        assertTrue("commitText" in editor.calls)
        assertFalse("setComposingRegion" in editor.calls)
    }

    @Test
    fun `whole field replacement deletes around the cursor before committing`() {
        val editor = MutableEditor("whole field", 5, 5)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(5, 5)
        val adapter = AiEditorAdapter(gateway)

        val capture = requireNotNull(adapter.captureInput())
        val result = adapter.replaceIfCurrent(capture.snapshot, "replacement")

        assertEquals(AiSource.Field, capture.source)
        assertEquals("whole field", capture.text)
        assertEquals(AiEditorReplaceResult.Replaced, result)
        assertEquals("replacement", editor.text)
        assertEquals(listOf(5, 6), editor.deleteArguments)
        assertTrue("commitText" in editor.calls)
        assertFalse("setComposingRegion" in editor.calls)
    }

    @Test
    fun `selection movement rejects a stale replacement`() {
        val editor = MutableEditor("prefix chosen suffix", 7, 13)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(7, 13)
        val adapter = AiEditorAdapter(gateway)
        val capture = requireNotNull(adapter.captureInput())

        editor.selectionStart = 0
        editor.selectionEnd = 6
        gateway.noteSelection(0, 6)

        assertEquals(
            AiEditorReplaceResult.SelectionChanged,
            adapter.replaceIfCurrent(capture.snapshot, "replacement")
        )
        assertEquals("prefix chosen suffix", editor.text)
    }

    @Test
    fun `silent field mutation rejects a stale replacement`() {
        val editor = MutableEditor("whole field", 5, 5)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(5, 5)
        val adapter = AiEditorAdapter(gateway)
        val capture = requireNotNull(adapter.captureInput())

        editor.text = "other field"

        assertEquals(
            AiEditorReplaceResult.TextChanged,
            adapter.replaceIfCurrent(capture.snapshot, "replacement")
        )
        assertEquals("other field", editor.text)
    }

    @Test
    fun `phrase completion captures only a collapsed caret at field end`() {
        val editor = MutableEditor("I will send the draft", 21, 21)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(21, 21)
        val adapter = AiEditorAdapter(gateway)

        val capture = requireNotNull(adapter.captureCompletionContext())

        assertEquals("I will send the draft", capture.prefix)
        assertTrue(adapter.isCompletionCaptureCurrent(capture.snapshot))

        editor.selectionStart = 4
        editor.selectionEnd = 4
        gateway.noteSelection(4, 4)
        assertFalse(adapter.isCompletionCaptureCurrent(capture.snapshot))
        assertNull(adapter.captureCompletionContext())
    }

    @Test
    fun `phrase completion rejects selected text and changed prefix`() {
        val editor = MutableEditor("I will send the draft", 2, 6)
        val gateway = EditorGateway { editor.connection }
        gateway.beginSession(2, 6)
        val adapter = AiEditorAdapter(gateway)

        assertNull(adapter.captureCompletionContext())

        editor.selectionStart = editor.text.length
        editor.selectionEnd = editor.text.length
        gateway.noteSelection(editor.text.length, editor.text.length)
        val capture = adapter.captureCompletionContext()
        assertNotNull(capture)
        editor.text = "I will change the draft"
        assertFalse(adapter.isCompletionCaptureCurrent(requireNotNull(capture).snapshot))
    }

    private class MutableEditor(
        var text: String,
        var selectionStart: Int,
        var selectionEnd: Int
    ) {
        val calls = mutableListOf<String>()
        var deleteArguments: List<Int>? = null

        val connection: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, arguments ->
            calls += method.name
            when (method.name) {
                "getSelectedText" -> if (selectionStart == selectionEnd) {
                    null
                } else {
                    text.substring(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
                }
                "getExtractedText" -> extractedText()
                "beginBatchEdit", "endBatchEdit" -> true
                "deleteSurroundingText" -> {
                    val before = arguments?.get(0) as Int
                    val after = arguments[1] as Int
                    deleteArguments = listOf(before, after)
                    val start = (minOf(selectionStart, selectionEnd) - before).coerceAtLeast(0)
                    val end = (maxOf(selectionStart, selectionEnd) + after).coerceAtMost(text.length)
                    text = text.removeRange(start, end)
                    selectionStart = start
                    selectionEnd = start
                    true
                }
                "commitText" -> {
                    val replacement = arguments?.get(0).toString()
                    val start = minOf(selectionStart, selectionEnd)
                    val end = maxOf(selectionStart, selectionEnd)
                    text = text.replaceRange(start, end, replacement)
                    selectionStart = start + replacement.length
                    selectionEnd = selectionStart
                    true
                }
                "closeConnection" -> Unit
                else -> defaultValue(method.returnType)
            }
        } as InputConnection

        private fun extractedText(): ExtractedText {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }
            val unsafe = unsafeField.get(null)
            val extracted = unsafeClass
                .getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, ExtractedText::class.java) as ExtractedText
            extracted.text = text
            extracted.startOffset = 0
            extracted.selectionStart = selectionStart
            extracted.selectionEnd = selectionEnd
            return extracted
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}
