package com.addiyon.keyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.addiyon.keyboard.emoji.EmojiUiController
import com.addiyon.keyboard.language.LanguagePack
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.ui.keys.CharacterKeyPresentation
import com.addiyon.keyboard.ui.keys.LanguageKeyPresentation

interface KeyboardController : EmojiUiController {
    val activePack: LanguagePack
    val latinSearchLayout: KeyboardLayout
    val languageKeyPresentation: LanguageKeyPresentation
    val numbersMode: NumbersMode
    val shiftState: ShiftState
    val enterAction: EnterAction
    val isEmailField: Boolean
    val isPrivateField: Boolean
    val isLowRam: Boolean
    val isDarkTheme: Boolean
    val isShiftEnabled: Boolean
    val isNumberMode: Boolean
    val keyboardHeightScale: Float
    val showNumberRow: Boolean
    val vibrateOnKeypress: Boolean
    val soundOnKeypress: Boolean
    val suggestionUiState: SuggestionUiState
    val expandedSuggestionsVisible: Boolean
    val showEmojiPanel: Boolean
    val emojiSearchQuery: String?
    val showTypingGuide: Boolean get() = true

    fun characterKeyPresentation(value: String): CharacterKeyPresentation
    fun onCharacter(value: String)
    fun commitText(value: String)
    fun toggleShift()
    fun onDeleteRepeatStart()
    fun onDeleteRepeatEnd()
    override fun onDelete()
    fun onSpace()
    fun toggleLanguage()
    fun onEnter()
    fun toggleNumberMode()
    fun toggleSymbolsPage()
    fun openKeypad()
    fun onSuggestionTapped(tap: SuggestionTap)
    fun hideExpandedSuggestions()
    fun dismissSuggestions()
    fun toggleExpandedSuggestions()
    fun openSettings()
    fun openThemes()
    fun openTypingGuide()
    fun openFeedback()
    fun onClipboardAction()
    fun openEmojiPanel()
    fun onVoiceInput()
    fun exitVoiceMode()
}

data class OptionalKeyboardUi(
    val toolbarAction: KeyboardToolbarAction? = null,
    val panelVisible: Boolean = false,
    val panel: @Composable (Dp) -> Unit = {}
)

data class KeyboardToolbarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)
