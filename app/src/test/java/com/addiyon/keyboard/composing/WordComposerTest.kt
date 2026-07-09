package com.addiyon.keyboard.composing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordComposerTest {

    // No InputConnection is needed to exercise the composer's own state
    // machine -- inputConnection() is only ever used via a safe call
    // (`inputConnection()?.commitText(...)`), so returning null here just
    // skips the (untestable-without-Android) field-write side effect while
    // leaving buffer/raw/display/onCommit behavior fully exercisable.
    private fun composer(
        render: (String) -> String = { it },
        composesInline: Boolean = true,
        onCommit: (raw: String, display: String) -> Unit = { _, _ -> }
    ) = WordComposer(
        inputConnection = { null },
        render = render,
        composesInline = composesInline,
        onCommit = onCommit
    )

    @Test
    fun onCharacterAppendsToBufferAndIsComposing() {
        val c = composer()
        assertFalse(c.isComposing)
        c.onCharacter("i")
        c.onCharacter("n")
        c.onCharacter("f")
        assertTrue(c.isComposing)
        assertEquals("inf", c.raw)
        assertEquals("inf", c.display)
    }

    @Test
    fun resumeSeedsBufferAndSubsequentCharactersExtendIt() {
        val c = composer()
        c.resume("info")
        assertTrue(c.isComposing)
        assertEquals("info", c.raw)

        c.onCharacter("r")
        c.onCharacter("m")
        c.onCharacter("a")
        c.onCharacter("t")
        c.onCharacter("i")
        c.onCharacter("o")
        c.onCharacter("n")
        assertEquals("information", c.raw)
    }

    @Test
    fun resumeWithEmptyPrefixIsANoOp() {
        val c = composer()
        c.resume("")
        assertFalse(c.isComposing)
    }

    @Test
    fun commitEmitsRenderedFormAndClearsBuffer() {
        var committedRaw: String? = null
        var committedDisplay: String? = null
        val c = composer(
            render = { it.uppercase() },
            onCommit = { raw, display -> committedRaw = raw; committedDisplay = display }
        )
        c.onCharacter("h")
        c.onCharacter("i")
        c.commit()

        assertFalse(c.isComposing)
        assertEquals("hi", committedRaw)
        assertEquals("HI", committedDisplay)
    }

    @Test
    fun commitOnEmptyBufferDoesNotInvokeOnCommit() {
        var invoked = false
        val c = composer(onCommit = { _, _ -> invoked = true })
        c.commit()
        assertFalse(invoked)
    }

    @Test
    fun resumeThenCommitRoundTripsTheFullWord() {
        var committedRaw: String? = null
        val c = composer(
            render = { it.uppercase() },
            onCommit = { raw, _ -> committedRaw = raw }
        )
        c.resume("info")
        c.onCharacter("r")
        c.onCharacter("m")
        c.onCharacter("a")
        c.onCharacter("t")
        c.onCharacter("i")
        c.onCharacter("o")
        c.onCharacter("n")
        c.commit()

        assertEquals("information", committedRaw)
        assertFalse(c.isComposing)
    }

    @Test
    fun onBackspaceRemovesOneUnitAtATime() {
        val c = composer()
        c.onCharacter("a")
        c.onCharacter("b")
        c.onCharacter("c")
        assertTrue(c.onBackspace())
        assertEquals("ab", c.raw)
        assertTrue(c.onBackspace())
        assertTrue(c.onBackspace())
        assertFalse(c.isComposing)
        // Buffer now empty -- caller's fallback should kick in.
        assertFalse(c.onBackspace())
    }
}
