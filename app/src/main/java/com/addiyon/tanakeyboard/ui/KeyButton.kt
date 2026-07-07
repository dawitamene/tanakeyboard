// ui/KeyButton.kt
package com.addiyon.tanakeyboard.ui

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

import com.addiyon.tanakeyboard.ui.keys.repeatingClickable
import com.addiyon.tanakeyboard.ui.settings.KeyboardPrefs
import kotlin.math.abs

/**
 * Per-keypress feedback, gated on the user's own preferences (both OFF by
 * default -- see [KeyboardPrefs]). Vibration additionally respects the system
 * touch-feedback setting via [View.performHapticFeedback]; sound plays the
 * standard system keypress click. Read fresh on every press so a settings
 * change takes effect immediately.
 */
private fun keypressFeedback(view: View) {
    val context = view.context
    if (KeyboardPrefs.vibrateOnKeypress(context)) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    if (KeyboardPrefs.soundOnKeypress(context)) {
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }
}

/**
 * A single keyboard key. Renders as either a letter/character key
 * (light surface) or a "special" function key (shift, delete, space,
 * enter, toggles) which gets a slightly darker surface so it's easy to
 * pick out at a glance, matching the visual language of Gboard/iOS.
 *
 * PRESS FEEDBACK (iOS-style, no ripple):
 *
 * The default Material ripple is suppressed (`indication = null`) --
 * press feedback instead mirrors what the iPhone keyboard does:
 *
 *   - Letter keys ([showsPreviewOnPress]) pop up a magnified "balloon"
 *     of the key's character directly above the key for as long as it's
 *     held. The balloon is a [Popup] with clipping disabled so it can
 *     extend above the keyboard window even for top-row keys.
 *   - Special keys (no balloon on iOS either) swap their surface shade
 *     while pressed -- the dark special surface flips to the light
 *     letter-key surface and vice versa.
 *   - Every press-down fires [keypressFeedback]: a
 *     [HapticFeedbackConstants.KEYBOARD_TAP] haptic and/or a keypress
 *     click sound, each gated on the user's own preference (both OFF by
 *     default, see [KeyboardPrefs]) and the haptic additionally on the
 *     system "touch feedback" setting. It fires on DOWN, not on release,
 *     matching iOS -- and for repeatable keys each auto-repeat tick gets
 *     its own feedback.
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
 * [repeatable] switches the key's press handling from a normal single-shot
 * `clickable` (fires once on tap/release) to [repeatingClickable] (fires
 * once immediately on press-down, then keeps firing [onClick] every ~50ms
 * for as long as the key is held). Used by Delete/Backspace so holding it
 * down deletes continuously, the way every other keyboard does.
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
    primaryFontSize: TextUnit = 23.sp,
    secondaryText: String? = null,
    secondaryFontSize: TextUnit = 10.sp,
    icon: ImageVector? = null,
    isSpecial: Boolean = false,
    isHighlighted: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    showLockIndicator: Boolean = false,
    repeatable: Boolean = false,
    showsPreviewOnPress: Boolean = false,
    onSwipe: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    /**
     * Optional custom face for the key, replacing the icon/[primaryText]
     * layout entirely (still centered, and still wrapped in all the press /
     * haptic / surface-swap behavior). Used by keys whose label isn't a single
     * glyph or string -- e.g. the language toggle stacks "ሀለ" over "AB" with a
     * divider between them.
     */
    content: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val background = when {
        isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        // iOS-style pressed shade swap: while a preview balloon is up the
        // balloon itself IS the feedback, so letter keys with a balloon
        // keep their normal surface; everything else flips between the
        // light and dark key surfaces for the duration of the press.
        isSpecial -> if (isPressed) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant
        else -> if (isPressed && !showsPreviewOnPress) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    }

    // Removes the extra ascent/descent space Android normally reserves
    // above and below a line of text (includeFontPadding). Without this,
    // even a perfectly box-centered letter looks like it's sitting with
    // room to spare above it, because the Text composable's own bounding
    // box is taller than the glyph it draws.
    val tightText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

    val pressModifier = if (repeatable) {
        Modifier.repeatingClickable(interactionSource = interactionSource) {
            keypressFeedback(view)
            onClick()
        }
    } else if (onLongPress != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongPress
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    // Horizontal swipe handler (currently the space bar, to flip language).
    // Accumulates the drag and fires [onSwipe] once on release if it crossed
    // a distance threshold, so a plain tap still falls through to onClick and
    // only a deliberate left/right flick triggers the swipe. The drag
    // consumes horizontal moves so it doesn't fight the tap detection.
    val swipeModifier = if (onSwipe != null) {
        Modifier.pointerInput(onSwipe) {
            val threshold = 32.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (abs(total) > threshold) onSwipe() }
            ) { change, dragAmount ->
                total += dragAmount
                change.consume()
            }
        }
    } else {
        Modifier
    }

    if (!repeatable) {
        // Haptic on press-DOWN (clickable's onClick only fires on release,
        // which feels laggy for a keyboard -- iOS buzzes the moment your
        // finger lands).
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    keypressFeedback(view)
                }
            }
        }
    }

    Card(
        modifier = modifier
            .height(height)
            .padding(horizontal = 3.dp)
            .then(pressModifier)
            .then(swipeModifier),
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
            if (content != null) {
                content()
            } else if (icon != null) {
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
                            fontSize = primaryFontSize,
                            fontWeight = FontWeight.Normal,
                            color = iconTint,
                            style = tightText
                        )

                        if (showLockIndicator) {
                            // Text bounding boxes have a natural descent area below the baseline.
                            // We omit the spacer and apply a slight negative y-offset here so the
                            // underline visually hugs the letter, matching the tight gap of the
                            // shift icon's indicator.
                            Box(
                                modifier = Modifier
                                    .offset(y = (-2).dp)
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
                        fontSize = secondaryFontSize,
                        fontWeight = FontWeight.Normal,
                        color = iconTint.copy(alpha = 0.6f),
                        style = tightText,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 3.dp, top = 0.dp)
                    )
                }
            }

            if (isPressed && showsPreviewOnPress && primaryText != null) {
                KeyPressPreview(
                    text = primaryText,
                    secondaryText = secondaryText,
                    fontSize = primaryFontSize,
                    keyHeight = height
                )
            }
        }
    }
}

/**
 * The iOS-style magnifier balloon shown above a letter key while it's
 * held: the key's character, enlarged, on a rounded surface sitting
 * flush against the top of the key.
 *
 * Implemented as a [Popup] (its own window) rather than an overlay in the
 * keyboard's own hierarchy because top-row keys need the balloon to
 * extend ABOVE the IME window's bounds -- an in-hierarchy overlay would
 * be clipped there. `clippingEnabled = false` is what allows the popup
 * window to be positioned partially outside the keyboard window, and
 * `focusable = false` keeps the popup from stealing input focus from the
 * text field being typed into (which would close the input session).
 *
 * The balloon is anchored to the key composable it's emitted from:
 * [PopupPositionProvider] receives the key's bounds and centers the
 * balloon horizontally over it, bottom edge touching the key's top edge.
 */
@Composable
private fun KeyPressPreview(
    text: String,
    secondaryText: String?,
    fontSize: TextUnit,
    keyHeight: Dp
) {
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left +
                        (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(10.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp)
                )
                .defaultMinSize(minWidth = 52.dp, minHeight = keyHeight + 12.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Mirror the key face: the small fidel annotation (when in
                // Amharic mode) rides above the enlarged Latin letter.
                secondaryText?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
                Text(
                    text = text,
                    fontSize = (fontSize.value * 1.5f).sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}
