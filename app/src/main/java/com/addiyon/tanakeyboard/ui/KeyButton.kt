package com.addiyon.tanakeyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single keyboard key. Renders as either a letter/character key
 * (light surface) or a "special" function key (shift, delete, space,
 * enter, toggles) which gets a slightly darker surface so it's easy to
 * pick out at a glance, matching the visual language of Gboard/iOS.
 *
 * [isHighlighted] draws a soft primary-tinted background instead of the
 * normal special-key surface -- available for states that need to stand
 * out via background rather than icon (currently unused by shift).
 *
 * [iconTint] lets callers color an icon independently of the default
 * content color (e.g. tinting the shift icon blue while it's active).
 *
 * [showLockIndicator] draws a small filled bar directly beneath the icon,
 * matching the underline mark Gboard shows when caps-lock is engaged.
 * There's no dedicated "badged" Material icon for this, so it's drawn
 * here rather than swapped in as a different icon asset.
 */
@Composable
fun KeyButton(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    primaryText: String? = null,
    secondaryText: String? = null,
    icon: ImageVector? = null,
    isSpecial: Boolean = false,
    isHighlighted: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    showLockIndicator: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isSpecial -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val content = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier
            .height(height)
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )

                    if (showLockIndicator) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .background(
                                    color = iconTint,
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    primaryText?.let {
                        Text(
                            text = it,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal,
                            color = content
                        )
                    }

                    secondaryText?.let {
                        Text(
                            text = it,
                            fontSize = 10.sp,
                            color = content.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}