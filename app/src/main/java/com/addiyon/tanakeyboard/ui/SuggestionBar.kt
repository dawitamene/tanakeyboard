// ui/SuggestionBar.kt
package com.addiyon.tanakeyboard.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The word-completion strip above the key rows. Always reserves a fixed-height
 * row while showing (the caller decides IF it should show at all, e.g. only on
 * a letter layout -- see [com.addiyon.tanakeyboard.ui.KeyboardScreen]) so the
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
fun SuggestionBar(
    suggestions: List<String>,
    isAmharic: Boolean,
    onTap: (String) -> Unit
) {
    if (isAmharic) {
        AmharicSuggestionStrip(suggestions, onTap)
    } else {
        EnglishSuggestionStrip(suggestions, onTap)
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
        suggestions.forEach { word ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onTap(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // A separator after every word.
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.5f)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
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
            // autoSize shrinks the font (down to 11sp) only when the word is
            // too long to fit its slot at the normal 16sp -- short words stay
            // full size, long ones scale down instead of ellipsizing.
            BasicText(
                text = word,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
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
