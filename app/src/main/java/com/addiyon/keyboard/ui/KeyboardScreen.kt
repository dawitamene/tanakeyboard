// ui/KeyboardScreen.kt
package com.addiyon.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.MainActivity
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.GeezNumbersLayout
import com.addiyon.keyboard.layout.NumberLayout
import com.addiyon.keyboard.layout.SymbolsLayout
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.NumbersMode

// The optional number row (Latin 1-0), matching Row 1 of NumberLayout so its
// 10 keys keep maxLetterCount aligned with the letter rows it sits above.
private val NumberRowKeys = listOf(
    KeyData.Character("1"),
    KeyData.Character("2"),
    KeyData.Character("3"),
    KeyData.Character("4"),
    KeyData.Character("5"),
    KeyData.Character("6"),
    KeyData.Character("7"),
    KeyData.Character("8"),
    KeyData.Character("9"),
    KeyData.Character("0")
)

@Composable
fun KeyboardScreen(
    service: AddiyonKeyboardService
) {

    // NOTE: we deliberately do NOT read service.currentInputConnection here.
    // Reading it once at composition time would bake a possibly-stale
    // InputConnection into every key's onClick closure below. Android does
    // not guarantee this composable gets recomposed every time the keyboard
    // is hidden and reshown, but the system DOES swap out the underlying
    // InputConnection for the new input session. If we captured it here,
    // reopening the keyboard without a fresh composition would leave every
    // key silently writing into a dead InputConnection. Instead, each key's
    // onClick (in KeyRow / KeyComposables) fetches
    // service.currentInputConnection fresh at the moment it's tapped.

    val isAmharic = service.isAmharic
    val isShift = service.isShiftEnabled
    val isNumberMode = service.isNumberMode

    val layout = when (service.numbersMode) {
        NumbersMode.NUMBERS -> NumberLayout
        NumbersMode.SYMBOLS -> SymbolsLayout
        NumbersMode.GEEZ_NUMBERS -> GeezNumbersLayout
        NumbersMode.OFF -> if (isAmharic) AmharicLayout else EnglishLayout
    }

    // Outer Box lets the feedback sheet overlay the whole keyboard; it wraps
    // the keyboard Column and sizes to it, so the overlay's matchParentSize
    // covers exactly the keyboard area.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // A Column, not a Box: the suggestion strip and the key rows need to
        // STACK vertically.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {

            // Amharic-only, shown only while a word is composing (nothing is
            // written to the field until commit -- see WordComposer's "WHY
            // AMHARIC COMPOSES OUT-OF-FIELD" doc). Grows the keyboard by
            // ~40.dp while typing and shrinks back on commit; that's
            // intended, since the strip is meant to be ephemeral.
            if (service.amharicBufferLatin.isNotEmpty()) {
                BufferPreviewStrip(latin = service.amharicBufferLatin)
            }

            // Always present -- across letter AND number/symbol layouts. When
            // there's nothing to suggest (always the case on the numeric pages,
            // where no word composes) it's the quick-action toolbar with the
            // logo; otherwise the suggestion strip.
            SuggestionArea(
                suggestions = service.suggestions,
                isAmharic = isAmharic,
                onTap = { word -> service.onSuggestionTapped(word) },
                onOpenSettings = { service.openAppScreen(MainActivity.SCREEN_SETTINGS) },
                onOpenThemes = { service.openAppScreen(MainActivity.SCREEN_THEMES) },
                onOpenGuide = { service.openAppScreen(MainActivity.SCREEN_GUIDE) },
                onFeedback = { service.openFeedbackScreen() },
                onAi = { service.onAiAction() },
                onClipboard = { service.onClipboardAction() },
                isListening = service.isListening,
                onVoice = { service.onVoiceInput() }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {

                val showNumberRow = service.showNumberRow && service.numbersMode == NumbersMode.OFF
                val rows = if (showNumberRow) listOf(NumberRowKeys) + layout.rows else layout.rows
                val metrics = computeKeyboardMetrics(rows = rows, availableWidth = maxWidth)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {

                    rows.forEachIndexed { index, row ->
                        // The digit row (row 0 when showNumberRow) always
                        // commits its literal character, so it never shows
                        // the Amharic fidel corner preview, regardless of
                        // the active language.
                        val rowIsAmharic = isAmharic && !(showNumberRow && index == 0)
                        KeyRow(
                            row = row,
                            isShift = isShift,
                            isAmharic = rowIsAmharic,
                            isNumberMode = isNumberMode,
                            metrics = metrics,
                            service = service
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}