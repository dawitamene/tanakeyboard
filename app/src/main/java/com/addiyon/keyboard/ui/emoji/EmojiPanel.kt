package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.emoji.Emoji
import com.addiyon.keyboard.emoji.EmojiCell
import com.addiyon.keyboard.emoji.EmojiData
import kotlinx.coroutines.launch

/**
 * The emoji picker: replaces the toolbar + key rows at exactly their
 * combined [height] (computed by KeyboardScreen from the same metrics the
 * key rows use), so opening and closing it never resizes the IME window.
 *
 * Layout: a 40.dp search pill (enters search mode, which KeyboardScreen
 * renders instead of this panel) over the category grid (weight 1) over a
 * 40.dp bottom bar -- ABC (back to letters) | category tabs | backspace.
 * Shows a lightweight placeholder until
 * [com.addiyon.keyboard.emoji.EmojiRepository] finishes its background load
 * (its `data` is Compose state, so readiness recomposes this automatically).
 *
 * The "Recently used" section is prepended from a snapshot frozen with
 * `remember` -- this panel leaves composition when it closes, so the
 * snapshot refreshes per OPEN, never mid-session (committing an emoji must
 * not reorder the grid under the user's finger).
 */
@Composable
fun EmojiPanel(
    service: AddiyonKeyboardService,
    height: Dp
) {
    val data = service.emojiRepository.data

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (data == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading emoji…",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            BottomBar(service, titles = emptyList(), activeIndex = 0, onSelect = {})
            return@Column
        }

        val recents = remember { service.recentEmojiSnapshot() }
        val browse = remember(data) { BrowseList(data, recents) }
        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()

        // Which group's section the grid is currently in: the last header at
        // or above the first visible item. derivedStateOf so scrolling only
        // recomposes the tab row when the SECTION changes, not per pixel.
        val activeIndex by remember(browse) {
            derivedStateOf {
                val first = gridState.firstVisibleItemIndex
                val at = browse.headerIndices.indexOfLast { it <= first }
                if (at >= 0) at else 0
            }
        }

        SearchPill(onClick = { service.openEmojiSearch() })

        EmojiGrid(
            cells = browse.cells,
            state = gridState,
            selectedTones = service.selectedSkinTones,
            onEmojiTap = remember(service) { { service.commitEmoji(it) } },
            onToneSelected = remember(service) { { base, v -> service.setSkinTone(base, v) } },
            modifier = Modifier.weight(1f)
        )

        BottomBar(
            service = service,
            titles = browse.titles,
            activeIndex = activeIndex,
            onSelect = { index ->
                // Jump, not animate -- Gboard jumps, and an animated scroll
                // across thousands of items composes everything it passes.
                scope.launch { gridState.scrollToItem(browse.headerIndices[index]) }
            }
        )
    }
}

/**
 * [EmojiData.browseCells] with a "Recently used" section (header + one cell
 * per recent commit) prepended when there are any recents; when there are
 * none the section AND its tab are simply absent. Recents cells wrap the
 * committed string (possibly already toned) in a variant-less [Emoji], so
 * they tap-commit verbatim and never long-press.
 */
private class BrowseList(data: EmojiData, recents: List<String>) {
    val cells: List<EmojiCell>
    val headerIndices: IntArray
    val titles: List<String>

    init {
        if (recents.isEmpty()) {
            cells = data.browseCells
            headerIndices = data.headerIndices
            titles = data.groups.map { it.name }
        } else {
            val combined = ArrayList<EmojiCell>(data.browseCells.size + recents.size + 1)
            combined.add(EmojiCell.Header(RECENTS_TITLE))
            recents.forEach {
                combined.add(EmojiCell.Item(Emoji(it, "", emptyList(), "")))
            }
            combined.addAll(data.browseCells)
            cells = combined

            val offset = recents.size + 1
            headerIndices = IntArray(data.headerIndices.size + 1) { i ->
                if (i == 0) 0 else data.headerIndices[i - 1] + offset
            }
            titles = listOf(RECENTS_TITLE) + data.groups.map { it.name }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Search emoji",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun BottomBar(
    service: AddiyonKeyboardService,
    titles: List<String>,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable { service.closeEmojiPanel() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ABC",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }

        EmojiCategoryTabs(titles = titles, activeIndex = activeIndex, onSelect = onSelect)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable { service.onDelete() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Backspace",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.height(20.dp)
            )
        }
    }
}
