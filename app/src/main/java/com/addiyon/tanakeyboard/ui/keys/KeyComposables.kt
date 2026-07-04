// ui/keys/KeyComposables.kt
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
*
* IMPORTANT: this composable no longer talks to the InputConnection. It
* just tells the service which key was pressed via [service.onCharacter];
* the service is where case is resolved (from shift state) and where the
* Amharic composer is fed. See TanaKeyboardService.onCharacter and the
* KeyRow doc for the full reasoning.
*/
@Composable
fun CharacterKey(
    key: KeyData.Character,
    isShift: Boolean,
    isAmharic: Boolean,
    width: Dp,
    height: Dp,
    service: TanaKeyboardService
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
        service.onCharacter(key.latin)
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
 *
 * [repeatable] is set so holding the key down deletes continuously (one
 * character immediately, then repeating every ~50ms after a short initial
 * delay) instead of requiring a fresh tap per character -- see
 * [com.addiyon.tanakeyboard.ui.keys.repeatingClickable].
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
        repeatable = true,
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
 *
 * Always shows the Amharic glyph as its label -- it doesn't switch to "EN"
 * when Amharic mode is on. Instead, active/inactive state is communicated
 * the same way ShiftKey communicates SHIFT/CAPS_LOCK: normal color when
 * off, primary-tinted plus a small underline bar when Amharic mode is on.
 */
@Composable
fun RowScope.LanguageToggleKey(
    isAmharic: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = "አ",
        iconTint = if (isAmharic) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        showLockIndicator = isAmharic,
        modifier = Modifier.weight(KeyWeights.LANGUAGE_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}