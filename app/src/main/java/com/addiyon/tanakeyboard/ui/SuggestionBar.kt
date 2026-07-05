// ui/SuggestionBar.kt
package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Amharic word-completion strip above the key rows. Always reserves a
 * fixed-height row while showing (the caller decides IF it should show at
 * all, e.g. only in Amharic letter mode -- see [com.addiyon.tanakeyboard.ui.KeyboardScreen])
 * so the keyboard's height doesn't visibly jump as [suggestions] goes from
 * empty to populated and back while the user types; an empty [suggestions]
 * just renders a blank bar rather than collapsing to nothing.
 *
 * Each chip routes its tap through [onTap] -- the same "everything goes
 * through a service method" convention every other key already follows
 * (see [KeyRow]'s class doc).
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEach { word ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTap(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
