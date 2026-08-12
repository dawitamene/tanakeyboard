package com.addiyon.keyboard.ui

import androidx.compose.runtime.Immutable
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.ui.keys.CharacterKeyPresentation
import com.addiyon.keyboard.ui.keys.LanguageKeyPresentation

@Immutable
data class KeyboardUiState(
    val isShift: Boolean,
    val languageDisplayName: String,
    val alternateNumbersLabel: String?,
    val languageKeyPresentation: LanguageKeyPresentation,
    val characterPresentation: (String) -> CharacterKeyPresentation,
    val showLanguagePresentation: Boolean,
    val isNumberMode: Boolean,
    val isEmailField: Boolean,
    val isPrivateField: Boolean,
    val numbersMode: NumbersMode,
    val shiftState: ShiftState,
    val enterAction: EnterAction,
    val vibrateOnKeypress: Boolean,
    val soundOnKeypress: Boolean,
)

interface KeyboardActions {
    fun character(value: String)
    fun commitText(value: String)
    fun shift()
    fun deleteRepeatStart()
    fun deleteRepeatEnd()
    fun delete()
    fun space()
    fun language()
    fun enter()
    fun numbers()
    fun symbols()
    fun keypad()
}
