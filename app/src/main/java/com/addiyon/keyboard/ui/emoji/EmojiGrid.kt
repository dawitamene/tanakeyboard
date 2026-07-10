package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.emoji.Emoji
import com.addiyon.keyboard.emoji.EmojiCell

/**
 * The scrolling emoji area: ONE lazy grid over a prebuilt flat list of
 * full-width group headers and emoji cells (see [EmojiCell]) -- deliberately
 * not a per-category pager, so scrolling is a single lazy composition with
 * no page churn, and categories flow continuously like Gboard.
 *
 * Items are positional (no keys): [cells] is immutable for as long as it's
 * displayed (a new snapshot means a fresh grid), so keyed diffing would buy
 * nothing and cost a map per layout pass. [contentType] still splits headers
 * from emoji so the two recycled item pools don't pollute each other.
 *
 * [selectedTones] is the service's `mutableStateMapOf` mirror of remembered
 * skin tones: each cell reads only its own base's key, so picking a tone
 * recomposes exactly that cell.
 */
@Composable
internal fun EmojiGrid(
    cells: List<EmojiCell>,
    state: LazyGridState,
    selectedTones: Map<String, String>,
    onEmojiTap: (String) -> Unit,
    onToneSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CELL_SIZE),
        state = state,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        items(
            count = cells.size,
            span = { index ->
                if (cells[index] is EmojiCell.Header) GridItemSpan(maxLineSpan)
                else GridItemSpan(1)
            },
            contentType = { index ->
                if (cells[index] is EmojiCell.Header) CONTENT_TYPE_HEADER
                else CONTENT_TYPE_EMOJI
            }
        ) { index ->
            when (val cell = cells[index]) {
                is EmojiCell.Header -> GroupHeader(cell.title)
                is EmojiCell.Item -> EmojiKey(
                    emoji = cell.emoji,
                    selectedTones = selectedTones,
                    onTap = onEmojiTap,
                    onToneSelected = onToneSelected
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
    )
}

/**
 * One tappable cell. Shows the remembered tone (falling back to the base),
 * commits what it shows on tap, and long-press opens [SkinTonePopup] when
 * the emoji has variants (recents entries never do -- their long-press is
 * disabled rather than popping an empty chooser).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiKey(
    emoji: Emoji,
    selectedTones: Map<String, String>,
    onTap: (String) -> Unit,
    onToneSelected: (String, String) -> Unit
) {
    val displayed = selectedTones[emoji.base] ?: emoji.base
    var showTones by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .height(CELL_SIZE)
            .clip(CircleShape)
            .combinedClickable(
                onClick = { onTap(displayed) },
                onLongClick = if (emoji.variants.isEmpty()) null else {
                    { showTones = true }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = displayed, fontSize = 24.sp)

        if (showTones) {
            SkinTonePopup(
                emoji = emoji,
                onPick = { variant ->
                    showTones = false
                    onToneSelected(emoji.base, variant)
                    onTap(variant)
                },
                onDismiss = { showTones = false }
            )
        }
    }
}

private val CELL_SIZE = 40.dp
private const val CONTENT_TYPE_HEADER = 0
private const val CONTENT_TYPE_EMOJI = 1
