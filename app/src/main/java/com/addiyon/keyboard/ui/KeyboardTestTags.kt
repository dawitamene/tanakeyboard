package com.addiyon.keyboard.ui

object KeyboardTestTags {
    const val KEY_SHIFT = "keyboard.key.shift"
    const val KEY_DELETE = "keyboard.key.delete"
    const val KEY_SPACE = "keyboard.key.space"
    const val KEY_ENTER = "keyboard.key.enter"
    const val KEY_NUMBER_TOGGLE = "keyboard.key.numberToggle"
    const val KEY_SYMBOLS_TOGGLE = "keyboard.key.symbolsToggle"
    const val KEY_LANGUAGE_TOGGLE = "keyboard.key.languageToggle"

    fun character(latin: String): String = "keyboard.key.character.$latin"
}
