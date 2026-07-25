package com.addiyon.keyboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.ShiftState

@Immutable
internal data class KeyboardUiState(
    val isShift: Boolean,
    val isAmharic: Boolean,
    val isNumberMode: Boolean,
    val isEmailField: Boolean,
    val isPrivateField: Boolean,
    val numbersMode: NumbersMode,
    val shiftState: ShiftState,
    val enterAction: EnterAction,
    val vibrateOnKeypress: Boolean,
    val soundOnKeypress: Boolean,
)

@Stable
internal class KeyboardActions(private val service: AddiyonKeyboardService) {
    fun character(value: String) = service.onCharacter(value)
    fun commitText(value: String) = service.commitText(value)
    fun shift() = service.toggleShift()
    fun deleteRepeatStart() = service.onDeleteRepeatStart()
    fun deleteRepeatEnd() = service.onDeleteRepeatEnd()
    fun delete() = service.onDelete()
    fun space() = service.onSpace()
    fun language() = service.toggleLanguage()
    fun enter() = service.onEnter()
    fun numbers() = service.toggleNumberMode()
    fun symbols() = service.toggleSymbolsPage()
    fun keypad() = service.openKeypad()
}
