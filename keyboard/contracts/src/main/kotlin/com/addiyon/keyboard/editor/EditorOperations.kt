package com.addiyon.keyboard.editor

interface EditorReadValue<out T> {
    val value: T
}

interface EditorOperations {
    fun textBeforeCursor(maxChars: Int, optional: Boolean = true): EditorReadValue<String>?
    fun textAfterCursor(maxChars: Int, optional: Boolean = true): EditorReadValue<String>?
    fun selectedText(optional: Boolean = false): EditorReadValue<String>?
    fun setComposingText(text: CharSequence): Boolean
    fun finishComposingText(): Boolean
    fun commitText(text: CharSequence): Boolean
    fun deleteBeforeCursor(chars: Int): Boolean
    fun recomposeBeforeCursor(chars: Int, text: CharSequence): Boolean
}
