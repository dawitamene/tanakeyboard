package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.AddiyonKeyboardService

/**
 * Emoji search mode's header, rendered by KeyboardScreen in place of the
 * suggestion strip (with the real ENGLISH key rows below it -- their
 * keypresses are diverted into
 * [AddiyonKeyboardService.emojiSearchQuery] by the service-method guards,
 * because the IME can't pop a TextField to serve itself).
 *
 * Two rows: the query line (back to browse | typed query + cursor bar |
 * clear) and a horizontally scrolling result strip. Tapping a result -- or
 * Enter, for the first one -- commits it with its remembered skin tone.
 */
@Composable
fun EmojiSearchHeader(service: AddiyonKeyboardService) {
    val query = service.emojiSearchQuery ?: return
    val data = service.emojiRepository.data
    val results = remember(data, query) { data?.search(query) ?: emptyList() }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    .clickable { service.closeEmojiSearch() }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to emoji",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search emoji",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                } else {
                    Text(
                        text = query,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        // Not weight(1f): the cursor bar must hug the text's
                        // trailing edge, not be pushed to the row's end.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.width(1.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { service.clearEmojiSearchQuery() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Fixed-height result strip so the key rows below don't shift as
        // results appear and disappear per keystroke.
        Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            if (results.isEmpty()) {
                if (query.isNotBlank()) {
                    Text(
                        text = "No emoji found",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(count = results.size) { index ->
                        val emoji = results[index]
                        val displayed = service.selectedSkinTones[emoji.base] ?: emoji.base
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { service.commitEmoji(displayed) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = displayed, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
