package com.addiyon.keyboard.emoji

import androidx.compose.ui.text.input.TextFieldValue

interface EmojiUiController {
    val emojiRepository: EmojiRepository
    val emojiSearchField: TextFieldValue?
    val selectedSkinTones: Map<String, String>
    fun recentEmojiSnapshot(): List<String>
    fun closeEmojiPanel()
    fun openEmojiSearch()
    fun closeEmojiSearch()
    fun clearEmojiSearchQuery()
    fun updateEmojiSearchField(value: TextFieldValue)
    fun commitEmoji(emoji: String)
    fun setSkinTone(base: String, variant: String)
    fun onDelete()
}
