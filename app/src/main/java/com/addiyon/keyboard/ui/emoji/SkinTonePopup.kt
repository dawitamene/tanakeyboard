package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.addiyon.keyboard.emoji.Emoji

/**
 * The long-press skin-tone chooser: a floating row (grid for the 25-variant
 * multi-person combos) of the base emoji plus its renderable variants,
 * anchored above the pressed cell. Picking one commits it AND remembers it
 * per base (see [com.addiyon.keyboard.AddiyonKeyboardService.setSkinTone]);
 * picking the base itself resets to yellow.
 *
 * Not focusable: the popup must never steal input focus from the field the
 * IME is serving.
 */
@Composable
internal fun SkinTonePopup(
    emoji: Emoji,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = remember(emoji) { (listOf(emoji.base) + emoji.variants).chunked(TONES_PER_ROW) }

    // Anchored to the cell's top edge, lifted by the popup's own (estimated)
    // height so it floats just above the pressed cell. The Popup clamps to
    // the window, so edge cells shift inward instead of clipping.
    val yOffset = with(LocalDensity.current) {
        -(TONE_CELL * rows.size + POPUP_PADDING * 2 + 6.dp).roundToPx()
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(POPUP_PADDING)) {
                rows.forEach { row ->
                    Row {
                        row.forEach { option ->
                            Box(
                                modifier = Modifier
                                    .size(TONE_CELL)
                                    .clip(CircleShape)
                                    .clickable { onPick(option) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = option, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TONE_CELL = 44.dp
private val POPUP_PADDING = 6.dp

// Six per row: the common case (base + 5 tones) stays a single row, and the
// 26-option multi-person combos wrap into a compact grid.
private const val TONES_PER_ROW = 6
