package com.addiyon.keyboard.ui.emoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiPeople
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.EmojiTransportation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The category tab strip in the panel's bottom bar -- one icon per grid
 * section, highlighted (accent tint) for the section currently at the top of
 * the grid. Tab icons come from the material `Emoji*` set, matched by the
 * group names `tools/build_emoji_data.py` emits; an unknown name (a future
 * Unicode group rename) falls back to a generic tag icon rather than
 * crashing or vanishing.
 */
@Composable
internal fun RowScope.EmojiCategoryTabs(
    titles: List<String>,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == activeIndex
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconFor(title),
                    contentDescription = title,
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Shared with the Recents section added in the panel. */
internal const val RECENTS_TITLE = "Recently used"

private fun iconFor(title: String): ImageVector = when (title) {
    RECENTS_TITLE -> Icons.Outlined.Schedule
    "Smileys & Emotion" -> Icons.Outlined.EmojiEmotions
    "People & Body" -> Icons.Outlined.EmojiPeople
    "Animals & Nature" -> Icons.Outlined.EmojiNature
    "Food & Drink" -> Icons.Outlined.EmojiFoodBeverage
    "Travel & Places" -> Icons.Outlined.EmojiTransportation
    "Activities" -> Icons.Outlined.EmojiEvents
    "Objects" -> Icons.Outlined.EmojiObjects
    "Symbols" -> Icons.Outlined.EmojiSymbols
    "Flags" -> Icons.Outlined.EmojiFlags
    else -> Icons.Outlined.Tag
}
