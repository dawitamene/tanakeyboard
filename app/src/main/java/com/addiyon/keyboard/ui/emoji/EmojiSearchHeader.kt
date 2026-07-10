package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.AddiyonKeyboardService

private const val EMPTY_SEARCH_EMOJI_LIMIT = 60

@Composable
fun EmojiSearchHeader(service: AddiyonKeyboardService) {
    val fieldValue = service.emojiSearchField ?: return
    val query = fieldValue.text
    val data = service.emojiRepository.data
    val results = remember(data, query) { data?.search(query) ?: emptyList() }
    val recents = remember { service.recentEmojiSnapshot() }
    val resultGlyphs = if (query.isBlank()) {
        val seen = LinkedHashSet<String>()
        recents.forEach { seen.add(it) }
        data?.allEmoji?.forEach { emoji ->
            if (seen.size < EMPTY_SEARCH_EMOJI_LIMIT) {
                seen.add(service.selectedSkinTones[emoji.base] ?: emoji.base)
            }
        }
        seen.take(EMPTY_SEARCH_EMOJI_LIMIT)
    } else {
        results.map { emoji -> service.selectedSkinTones[emoji.base] ?: emoji.base }
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        focusRequester.captureFocus()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (resultGlyphs.isEmpty()) {
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
                    items(count = resultGlyphs.size) { index ->
                        val displayed = resultGlyphs[index]
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .clickable { service.commitEmoji(displayed) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = displayed, fontSize = 28.sp)
                        }
                    }
                }
            }
        }

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

            // The IME window never gains real window focus (the app behind it
            // keeps it), and Compose gates the text cursor's blink -- and
            // selection handles -- on LocalWindowInfo.isWindowFocused. Lie to
            // just this field so it presents as the active editor it
            // effectively is; everything else from the real WindowInfo is
            // kept by delegation.
            val realWindowInfo = LocalWindowInfo.current
            val focusedWindowInfo = remember(realWindowInfo) {
                object : WindowInfo by realWindowInfo {
                    override val isWindowFocused: Boolean get() = true
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides focusedWindowInfo) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { service.updateEmojiSearchField(it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) focusRequester.captureFocus()
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search emoji",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
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
    }
}
