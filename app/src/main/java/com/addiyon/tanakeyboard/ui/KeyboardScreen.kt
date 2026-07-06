// ui/KeyboardScreen.kt
package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import com.addiyon.tanakeyboard.MainActivity
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

    // Feedback sheet shown OVER the keyboard (an in-hierarchy overlay, not a
    // Dialog/ModalBottomSheet -- those need a window token an IME can't
    // reliably provide).
    var showFeedback by remember { mutableStateOf(false) }

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
            .background(MaterialTheme.colorScheme.background)
    ) {
        // A Column, not a Box: the suggestion strip and the key rows need to
        // STACK vertically.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {

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
                onFeedback = { showFeedback = true },
                onAi = { service.onAiAction() },
                onClipboard = { service.onClipboardAction() }
            )

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

        if (showFeedback) {
            FeedbackSheet(
                onDismiss = { showFeedback = false },
                onEmail = {
                    showFeedback = false
                    service.sendFeedbackEmail()
                },
                onTelegram = {
                    showFeedback = false
                    service.openFeedbackTelegram()
                }
            )
        }
    }
}

/**
 * The feedback bottom sheet, drawn over the keyboard as an in-hierarchy
 * overlay (scrim + bottom-anchored panel) so it doesn't depend on a separate
 * window. Mirrors the settings feedback sheet: Email and Telegram.
 */
@Composable
private fun BoxScope.FeedbackSheet(
    onDismiss: () -> Unit,
    onEmail: () -> Unit,
    onTelegram: () -> Unit
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Consume taps on the panel so they don't dismiss the sheet.
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Send feedback",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            FeedbackRow(Icons.Filled.Email, "Email", onEmail)
            FeedbackRow(Icons.AutoMirrored.Filled.Send, "Telegram", onTelegram)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FeedbackRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}