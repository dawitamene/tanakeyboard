package com.addiyon.tanakeyboard.ui.keys

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.model.KeyData
import com.addiyon.tanakeyboard.model.ShiftState
import com.addiyon.tanakeyboard.ui.KeyButton
import com.addiyon.tanakeyboard.ui.icons.ShiftIconFilled
import com.addiyon.tanakeyboard.ui.icons.ShiftIconOutlined

/**
 * A regular letter key. Fixed width -> identical size on every row.
 * Rendered as a light "surface" key (not special) so it stands apart
 * visually from function keys.
 */
@Composable
fun CharacterKey(
    key: KeyData.Character,
    isShift: Boolean,
    isAmharic: Boolean,
    width: Dp,
    height: Dp,
    service: TanaKeyboardService,
    ic: android.view.inputmethod.InputConnection?
) {
    KeyButton(
        primaryText = if (isShift && !isAmharic) {
            key.latin.uppercase()
        } else {
            key.latin.lowercase()
        },
        secondaryText = key.amharic,
        modifier = Modifier.width(width),
        height = height,
        isSpecial = false
    ) {
        val output = when {
            isAmharic -> key.latin
            isShift -> key.latin.uppercase()
            else -> key.latin.lowercase()
        }

        ic?.commitText(output, 1)

        // One-shot SHIFT consumes itself after a single character;
        // CAPS_LOCK stays engaged until the shift key is tapped again.
        service.consumeShiftAfterCharacter()
    }
}

/**
 * Shift / caps-lock key. Weighted -> flexes to fill leftover space in its
 * row. Uses the real KeyboardCapslock glyph (the same shift-arrow shape
 * AOSP/Gboard use) with three visual states mirroring [ShiftState]:
 *
 * OFF       - outlined icon, normal color.
 * SHIFT     - filled icon, primary (blue) tint. One-shot: capitalizes the
 *             next letter only.
 * CAPS_LOCK - filled icon, primary (blue) tint, plus a small underline
 *             bar drawn beneath it -- the same "locked" indicator Gboard
 *             draws under its shift icon (there's no separate "badged"
 *             Material icon for this; it's a custom mark, so we draw it
 *             ourselves). Stays capitalized until tapped again.
 *
 * The key's background never changes -- only the icon communicates state.
 */
@Composable
fun RowScope.ShiftKey(
    shiftState: ShiftState,
    height: Dp,
    onClick: () -> Unit
) {
    val icon = if (shiftState == ShiftState.OFF) {
        ShiftIconOutlined
    } else {
        ShiftIconFilled
    }

    val tint = if (shiftState == ShiftState.OFF) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }

    KeyButton(
        icon = icon,
        iconTint = tint,
        showLockIndicator = shiftState == ShiftState.CAPS_LOCK,
        modifier = Modifier.weight(KeyWeights.SHIFT),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Delete/backspace key. Weighted -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 */
@Composable
fun RowScope.DeleteKey(
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        icon = Icons.Outlined.Backspace,
        modifier = Modifier.weight(KeyWeights.DELETE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Space bar. Weighted heavily -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 */
@Composable
fun RowScope.SpaceKey(
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = "space",
        modifier = Modifier.weight(KeyWeights.SPACE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Enter/return key. Weighted -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 */
@Composable
fun RowScope.EnterKey(
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
        modifier = Modifier.weight(KeyWeights.ENTER),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * "123" number-layout toggle. Weighted -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 */
@Composable
fun RowScope.NumberToggleKey(
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = "123",
        modifier = Modifier.weight(KeyWeights.NUMBER_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Amharic/English language toggle. Weighted -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 */
@Composable
fun RowScope.LanguageToggleKey(
    isAmharic: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = if (isAmharic) "EN" else "አ",
        modifier = Modifier.weight(KeyWeights.LANGUAGE_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}