// ui/BufferPreviewStrip.kt
package com.addiyon.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Amharic-only strip shown above [SuggestionArea] while a word is composing:
 * just the raw Latin being typed, sized to its own content and left-aligned
 * -- no background is painted, not even for the empty space beside it, so
 * it reads as text sitting directly on the keyboard's own background.
 *
 * Its fidel reading isn't repeated here -- it's already the first
 * (bolded/primary) chip in the suggestion strip below. Nothing is written to
 * the target field until commit (see
 * [com.addiyon.keyboard.composing.WordComposer]'s "WHY AMHARIC COMPOSES
 * OUT-OF-FIELD" doc), so this strip is the only place the in-progress Latin
 * is visible at all. The caller (KeyboardScreen) mounts it only while the
 * buffer is non-empty, so it appears/disappears with composing state.
 */
@Composable
fun BufferPreviewStrip(latin: String) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .padding(horizontal = 24.dp,),

        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = latin,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
