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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
 * It also colors [primaryText] when there's no icon -- e.g. the language
 * toggle key uses this to turn its label blue while Amharic mode is on,
 * the same way ShiftKey tints its icon.
 *
 * [showLockIndicator] draws a small filled bar directly beneath the icon
 * (or beneath [primaryText] when there's no icon), matching the underline
 * mark Gboard shows when caps-lock is engaged. There's no dedicated
 * "badged" Material icon for this, so it's drawn here rather than swapped
 * in as a different icon asset.
 *
 * Text layout: when both [primaryText] and [secondaryText] are supplied
 * (character keys in Amharic mode), [secondaryText] is rendered as a small
 * label tucked into the top-right corner -- the main letter itself (the
 * Latin key, which is what actually gets typed while in Amharic mode)
 * stays centered and at the same full size as an ordinary key, unaffected
 * by whether a corner label is present. When only [primaryText] is
 * supplied (English mode, or any special key with just a label), it
 * renders alone, centered, at full size -- matching a normal Gboard-style
 * key.
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

    // Removes the extra ascent/descent space Android normally reserves
    // above and below a line of text (includeFontPadding). Without this,
    // even a perfectly box-centered letter looks like it's sitting with
    // room to spare above it, because the Text composable's own bounding
    // box is taller than the glyph it draws.
    val tightText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

    Card(
        modifier = modifier
            .height(height)
            .padding(horizontal = 3.dp)
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
                // primaryText is placed via the parent Box's own
                // Alignment.Center (same as every other key, Amharic or
                // not) so it never shifts position depending on whether a
                // secondaryText annotation exists. secondaryText (the
                // Amharic glyph) is a separate overlay pinned to the top of
                // the box, floating above the letter like a superscript,
                // instead of being stacked in a Column with it -- stacking
                // would center the two-line block as a unit and push the
                // main letter down off-center.
                //
                // primaryText is colored with [iconTint] (which defaults to
                // onSurface, same as before) so toggle-style keys like the
                // language switch can tint their label the same way
                // ShiftKey tints its icon. [showLockIndicator] draws the
                // same small underline bar used for caps-lock beneath the
                // letter when there's no icon, e.g. to mark Amharic mode
                // as active.
                primaryText?.let {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = it,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Normal,
                            color = iconTint,
                            style = tightText
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
                }

                // Tucked right into the empty space at the top of the key
                // (which already exists because primaryText is vertically
                // centered and doesn't fill the key's full height) -- flush
                // to the top-right corner rather than pushed further down
                // with extra padding.
                secondaryText?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = iconTint.copy(alpha = 0.6f),
                        style = tightText,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 3.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}