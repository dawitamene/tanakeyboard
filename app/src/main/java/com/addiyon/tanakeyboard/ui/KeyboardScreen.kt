// ui/KeyboardScreen.kt
package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.layout.AmharicLayout
import com.addiyon.tanakeyboard.layout.EnglishLayout
import com.addiyon.tanakeyboard.layout.GeezNumbersLayout
import com.addiyon.tanakeyboard.layout.NumberLayout
import com.addiyon.tanakeyboard.layout.SymbolsLayout
import com.addiyon.tanakeyboard.model.NumbersMode

@Composable
fun KeyboardScreen(
    service: TanaKeyboardService
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

    // A Column, not a Box: the suggestion strip and the key rows need to
    // STACK vertically. A Box overlays children by alignment instead --
    // a TopCenter-aligned strip inside the same Box as a BottomCenter-
    // aligned key block would draw on top of the first key row rather
    // than push it down.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // On both letter layouts (Amharic and English), but not the
        // Numbers/Symbols pages -- suggestions are keyed off the active
        // word composer's buffer, and no word is ever composing there.
        if (!isNumberMode) {
            SuggestionBar(
                suggestions = service.suggestions,
                isAmharic = isAmharic,
                onTap = { word -> service.onSuggestionTapped(word) }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {

            val metrics = computeKeyboardMetrics(rows = layout.rows, availableWidth = maxWidth)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {

                layout.rows.forEach { row ->
                    KeyRow(
                        row = row,
                        isShift = isShift,
                        isAmharic = isAmharic,
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