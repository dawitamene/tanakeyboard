package com.addiyon.keyboard.ui

object KeyboardTestTags {
    const val KEY_SHIFT = "keyboard.key.shift"
    const val KEY_DELETE = "keyboard.key.delete"
    const val KEY_SPACE = "keyboard.key.space"
    const val KEY_ENTER = "keyboard.key.enter"
    const val KEY_NUMBER_TOGGLE = "keyboard.key.numberToggle"
    const val KEY_SYMBOLS_TOGGLE = "keyboard.key.symbolsToggle"
    const val KEY_KEYPAD_TOGGLE = "keyboard.key.keypadToggle"
    const val KEY_LANGUAGE_TOGGLE = "keyboard.key.languageToggle"

    fun character(latin: String): String = "keyboard.key.character.$latin"
}

object SuggestionAreaTestContract {
    const val AMHARIC_SUGGESTION_STRIP_TAG = "amharic-suggestion-strip"
    const val LANGUAGE_LOADING_INDICATOR_TAG = "language-loading-indicator"
    const val PREDICTION_LOADING_STRIP_TAG = "prediction-loading-strip"
    const val PREDICTION_LOADING_INDICATOR_TAG = "prediction-loading-indicator"
    const val PREDICTION_LOADING_INDICATOR_DELAY_MILLIS = 120L
    const val SUGGESTION_DISMISS_TAG = "suggestion-dismiss"

    fun predictionLoadingSlotTag(index: Int): String = "prediction-loading-slot-$index"
}
