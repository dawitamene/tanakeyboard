package com.addiyon.keyboard.composing

import com.addiyon.keyboard.editor.EditorOperations
import com.addiyon.keyboard.editor.EditorReadValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TypingControllerContractTest {
    @Test
    fun charactersComposeAndSpaceCommits() {
        val editor = InMemoryEditor()
        val controller = TypingController(
            editor,
            profile = { TypingProfile(isWordCharacter = { it.all(Char::isLetter) }) }
        )

        "hello".forEach { controller.onCharacter(it.toString()) }
        controller.onSpace()

        assertEquals("hello ", editor.text)
        assertFalse(controller.isComposing)
    }

    @Test
    fun languageChangeUsesTheOutgoingCommitTransform() {
        val editor = InMemoryEditor()
        var transform: (String) -> String = { it.uppercase() }
        val controller = TypingController(
            editor,
            profile = {
                TypingProfile(
                    isWordCharacter = { true },
                    commitTransform = transform
                )
            }
        )

        "sera".forEach { controller.onCharacter(it.toString()) }
        controller.onLanguageChange()
        transform = { it }

        assertEquals("SERA", editor.text)
        assertFalse(controller.isComposing)
    }

    @Test
    fun completionReplacesOnlyTheComposingRegion() {
        val editor = InMemoryEditor("say ")
        val controller = TypingController(
            editor,
            profile = { TypingProfile(isWordCharacter = { true }) }
        )

        "hel".forEach { controller.onCharacter(it.toString()) }
        controller.onSuggestionTap("hello", SuggestionKind.COMPLETION)

        assertEquals("say hello ", editor.text)
    }

    @Test
    fun completionReplacesCommittedDisplayTextWithoutReverseConversion() {
        val editor = InMemoryEditor("እንዴ")
        val controller = TypingController(
            editor,
            profile = {
                TypingProfile(
                    isWordCharacter = { text -> text.all(Char::isLetter) },
                    wordEndingAtCursor = { before, after ->
                        before.takeLastWhile(Char::isLetter).takeIf {
                            it.isNotEmpty() && after.firstOrNull()?.isLetter() != true
                        }
                    },
                    remembersRawLatin = true
                )
            }
        )

        controller.onSuggestionTap("እንዴት", SuggestionKind.COMPLETION)

        assertEquals("እንዴት ", editor.text)
    }

    private class InMemoryEditor(initial: String = "") : EditorOperations {
        private val document = StringBuilder(initial)
        private var cursor = document.length
        private var composingStart: Int? = null

        val text: String get() = document.toString()

        override fun textBeforeCursor(maxChars: Int, optional: Boolean) =
            read(document.substring(0, cursor).takeLast(maxChars))

        override fun textAfterCursor(maxChars: Int, optional: Boolean) =
            read(document.substring(cursor).take(maxChars))

        override fun selectedText(optional: Boolean) = read("")

        override fun setComposingText(text: CharSequence): Boolean {
            replaceOwnedRegion(text.toString())
            if (text.isEmpty()) composingStart = null
            return true
        }

        override fun finishComposingText(): Boolean {
            composingStart = null
            return true
        }

        override fun commitText(text: CharSequence): Boolean {
            replaceOwnedRegion(text.toString())
            composingStart = null
            return true
        }

        override fun deleteBeforeCursor(chars: Int): Boolean {
            val start = (cursor - chars).coerceAtLeast(0)
            document.delete(start, cursor)
            cursor = start
            return true
        }

        override fun recomposeBeforeCursor(chars: Int, text: CharSequence): Boolean {
            deleteBeforeCursor(chars)
            composingStart = cursor
            return setComposingText(text)
        }

        private fun replaceOwnedRegion(value: String) {
            val start = composingStart ?: cursor.also { composingStart = it }
            document.replace(start, cursor, value)
            cursor = start + value.length
        }

        private fun <T> read(value: T): EditorReadValue<T> = object : EditorReadValue<T> {
            override val value: T = value
        }
    }
}
