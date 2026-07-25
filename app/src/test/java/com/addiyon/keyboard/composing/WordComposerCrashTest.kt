package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class WordComposerCrashTest {

    private fun throwingProxy(message: String = "boom"): InputConnection {
        val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
            if (method.name == "closeConnection") {
                null
            } else {
                throw RuntimeException("$message on ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
            handler
        ) as InputConnection
    }

    private class RecordingInputConnection : InputConnection {
        val commits = mutableListOf<String>()
        val composingTexts = mutableListOf<String>()
        override fun beginBatchEdit(): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun closeConnection() = Unit
        override fun commitCompletion(info: android.view.inputmethod.CompletionInfo?): Boolean = true
        override fun commitContent(inputContentInfo: android.view.inputmethod.InputContentInfo, flags: Int, opts: android.os.Bundle?): Boolean = true
        override fun commitCorrection(info: android.view.inputmethod.CorrectionInfo?): Boolean = true
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            commits.add(text?.toString() ?: "")
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = true
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? = null
        override fun getHandler(): android.os.Handler? = null
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getTextAfterCursor(maxChars: Int, flags: Int): CharSequence? = null
        override fun getTextBeforeCursor(maxChars: Int, flags: Int): CharSequence? = null
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun performEditorAction(editorAction: Int): Boolean = true
        override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun sendKeyEvent(event: android.view.KeyEvent?): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun setComposingText(text: CharSequence?, cursor: Int): Boolean {
            composingTexts.add(text?.toString() ?: "")
            return true
        }
        override fun setSelection(start: Int, end: Int): Boolean = true
    }

    @Test
    fun onCharacterSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        composer.onCharacter("b")
        assertEquals("ab", composer.raw)
    }

    @Test
    fun onBackspaceSwallowsThrowingInputConnectionWhenBufferBecomesEmpty() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        val absorbed = composer.onBackspace()
        assertTrue(absorbed)
        assertEquals("", composer.raw)
        assertFalse(composer.isComposing)
    }

    @Test
    fun commitSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        composer.commit()
        assertFalse(composer.isComposing)
    }

    @Test
    fun finishSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        composer.finish()
        assertFalse(composer.isComposing)
    }

    @Test
    fun abandonSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        composer.abandon()
        assertFalse(composer.isComposing)
    }

    @Test
    fun commitSuggestionSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.onCharacter("a")
        composer.commitSuggestion("alphabet")
        assertFalse(composer.isComposing)
    }

    @Test
    fun resumeSwallowsThrowingInputConnection() {
        val composer = WordComposer(inputConnection = { throwingProxy() })
        composer.resume("hello")
        assertEquals("hello", composer.raw)
    }

    @Test
    fun healthyInputConnectionStillReceivesCalls() {
        val ic = RecordingInputConnection()
        val composer = WordComposer(inputConnection = { ic })
        composer.onCharacter("a")
        composer.onCharacter("b")
        assertEquals(listOf("a", "ab"), ic.composingTexts)
    }

    @Test
    fun nullInputConnectionDoesNotThrow() {
        val composer = WordComposer(inputConnection = { null })
        composer.onCharacter("a")
        composer.onBackspace()
        composer.commit()
    }
}
