package com.addiyon.keyboard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.telemetry.NonFatalCategory
import com.addiyon.keyboard.telemetry.Telemetry

class ImeTestCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
        val value = intent.getStringExtra(EXTRA_VALUE).orEmpty()
        if (command == COMMAND_READY) {
            resultCode =
                if (AddiyonKeyboardService.currentInstance == null) 0 else 1
            return
        }
        Handler(Looper.getMainLooper()).post {
            if (command == COMMAND_TELEMETRY_FATAL) {
                throw IllegalStateException("Addiyon controlled telemetry test crash")
            }
            if (command == COMMAND_TELEMETRY_NON_FATAL) {
                Telemetry.recordNonFatal(
                    NonFatalCategory.APPLICATION_OPERATION,
                    IllegalStateException("Addiyon controlled sanitized non-fatal")
                )
                return@post
            }
            val service = AddiyonKeyboardService.currentInstance ?: return@post
            when (command) {
                COMMAND_CHARACTER -> service.onCharacter(value)
                COMMAND_TEXT -> service.commitText(value)
                COMMAND_SPACE -> service.onSpace()
                COMMAND_ENTER -> service.onEnter()
                COMMAND_DELETE -> service.onDelete()
                COMMAND_DELETE_START -> service.onDeleteRepeatStart()
                COMMAND_DELETE_END -> service.onDeleteRepeatEnd()
                COMMAND_LANGUAGE -> service.toggleLanguage()
                COMMAND_LANGUAGE_AMHARIC -> service.setLanguage(amharic = true)
                COMMAND_LANGUAGE_ENGLISH -> service.setLanguage(amharic = false)
                COMMAND_NUMBER -> service.toggleNumberMode()
                COMMAND_SYMBOLS -> service.toggleSymbolsPage()
                COMMAND_KEYPAD -> service.openKeypad()
                COMMAND_SHIFT -> service.toggleShift()
                COMMAND_SHIFT_RESET -> service.resetShift()
                COMMAND_SUGGESTION -> service.onSuggestionTapped(value)
                COMMAND_EMOJI_OPEN -> service.openEmojiPanel()
                COMMAND_EMOJI_CLOSE -> service.closeEmojiPanel()
                COMMAND_EMOJI -> service.commitEmoji(value)
            }
        }
    }

    companion object {
        const val EXTRA_COMMAND = "command"
        const val EXTRA_VALUE = "value"
        const val COMMAND_CHARACTER = "character"
        const val COMMAND_TEXT = "text"
        const val COMMAND_SPACE = "space"
        const val COMMAND_ENTER = "enter"
        const val COMMAND_DELETE = "delete"
        const val COMMAND_DELETE_START = "delete_start"
        const val COMMAND_DELETE_END = "delete_end"
        const val COMMAND_LANGUAGE = "language"
        const val COMMAND_LANGUAGE_AMHARIC = "language_amharic"
        const val COMMAND_LANGUAGE_ENGLISH = "language_english"
        const val COMMAND_NUMBER = "number"
        const val COMMAND_SYMBOLS = "symbols"
        const val COMMAND_KEYPAD = "keypad"
        const val COMMAND_SHIFT = "shift"
        const val COMMAND_SHIFT_RESET = "shift_reset"
        const val COMMAND_SUGGESTION = "suggestion"
        const val COMMAND_EMOJI_OPEN = "emoji_open"
        const val COMMAND_EMOJI_CLOSE = "emoji_close"
        const val COMMAND_EMOJI = "emoji"
        const val COMMAND_READY = "ready"
        const val COMMAND_TELEMETRY_FATAL = "telemetry_fatal"
        const val COMMAND_TELEMETRY_NON_FATAL = "telemetry_non_fatal"
    }
}
