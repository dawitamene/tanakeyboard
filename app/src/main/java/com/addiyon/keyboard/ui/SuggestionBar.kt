// ui/SuggestionBar.kt
package com.addiyon.keyboard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The word-completion strip above the key rows. Always reserves a fixed-height
 * row while showing (the caller decides IF it should show at all, e.g. only on
 * a letter layout -- see [com.addiyon.keyboard.ui.KeyboardScreen]) so the
 * keyboard's height doesn't visibly jump as [suggestions] goes from empty to
 * populated and back; an empty [suggestions] just renders a blank bar.
 *
 * Two layouts, chosen by [isAmharic]:
 *
 *  - AMHARIC: the strip can be long (multiple fidel readings + completions of
 *    each), so it SCROLLS HORIZONTALLY -- each chip is sized to its content
 *    and a thin separator follows every word.
 *  - ENGLISH: a fixed three-up layout (Gboard-style), each slot an equal third
 *    of the width with its word CENTERED. A long word doesn't scroll or
 *    ellipsize -- its font auto-shrinks to fit its slot.
 *
 * Each chip routes its tap through [onTap] -- the same "everything goes through
 * a service method" convention every other key already follows (see [KeyRow]).
 */
@Composable
fun SuggestionArea(
    suggestions: List<String>,
    isAmharic: Boolean,
    onTap: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenGuide: () -> Unit,
    onFeedback: () -> Unit,
    onAi: () -> Unit,
    onClipboard: () -> Unit,
    isListening: Boolean = false,
    onVoice: () -> Unit = {}
) {
    // When there's nothing to suggest, the row is a toolbar of quick actions
    // spread evenly across the width (justify space-between); when suggestions
    // exist the strip fills the whole row.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
        horizontalArrangement =
            if (suggestions.isEmpty()) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (suggestions.isEmpty()) {
            // AI + Clipboard kept for later, commented out for now:
            // ToolbarIcon(Icons.Outlined.AutoAwesome, "AI", onAi)
            // ToolbarIcon(Icons.Outlined.ContentPaste, "Clipboard", onClipboard)
            ToolbarIcon(Icons.Outlined.Settings, "Settings", onOpenSettings)
            ToolbarIcon(Icons.Outlined.MenuBook, "Typing guide", onOpenGuide)
            ToolbarIcon(Icons.Outlined.Feedback, "Feedback", onFeedback)
            ToolbarIcon(Icons.Outlined.Palette, "Themes", onOpenThemes)
            MicToolbarIcon(isListening = isListening, onClick = onVoice)
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (isAmharic) {
                    AmharicSuggestionStrip(suggestions, onTap)
                } else {
                    EnglishSuggestionStrip(suggestions, onTap)
                }
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tinted: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (tinted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The mic toolbar icon, pulsing gently (scale + fade) on an infinite loop
 * while [isListening] is true, so the toolbar reads as "actively recording"
 * at a glance instead of just swapping to the filled glyph.
 */
@Composable
private fun MicToolbarIcon(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "micPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Mic else Icons.Outlined.Mic,
            contentDescription = if (isListening) "Stop voice input" else "Voice input",
            tint = if (isListening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier
                .size(22.dp)
                .scale(if (isListening) pulse else 1f)
        )
    }
}

@Composable
private fun AmharicSuggestionStrip(
    suggestions: List<String>,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEachIndexed { index, word ->
            // The first chip is the greedy reading -- what SPACE auto-commits.
            // Make it stand out (bolder, accent color) so the default choice
            // is obvious versus the alternates.
            val isPrimary = index == 0
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onTap(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    fontSize = 16.sp,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = if (isPrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // A separator BETWEEN words only -- no trailing divider after the
            // last chip.
            if (index < suggestions.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.5f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}

/**
 * English: always three equal-width, centered slots. Empty slots (fewer than
 * three suggestions) render blank but still hold their third of the row, so
 * the present chips stay put instead of re-centering as the count changes.
 */
@Composable
private fun EnglishSuggestionStrip(
    suggestions: List<String>,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            EnglishSuggestionSlot(word = suggestions.getOrNull(i), onTap = onTap)

            if (i < 2) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.5f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun RowScope.EnglishSuggestionSlot(
    word: String?,
    onTap: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = word != null) { word?.let(onTap) },
        contentAlignment = Alignment.Center
    ) {
        if (word != null) {
            // autoSize shrinks the font (only down to 15sp) when the word is
            // too long to fit its slot at the normal 16sp -- short words stay
            // full size, long ones scale down slightly and then ellipsize
            // rather than shrinking to an unreadable size.
            BasicText(
                text = word,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 15.sp,
                    maxFontSize = 16.sp,
                    stepSize = 1.sp
                ),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
