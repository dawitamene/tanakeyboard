// ui/keys/KeyComposables.kt
package com.addiyon.tanakeyboard.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.addiyon.tanakeyboard.TanaKeyboardService
import com.addiyon.tanakeyboard.model.KeyData
import com.addiyon.tanakeyboard.model.NumbersMode
import com.addiyon.tanakeyboard.model.ShiftState
import com.addiyon.tanakeyboard.transliteration.Transliterator
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
 *
 * CASE MATTERS IN AMHARIC MODE TOO (step 6):
 *
 * The transliteration table distinguishes h/H (ሀ/ሐ), t/T (ተ/ጠ), and
 * ch/C (ቸ/ጨ) as entirely different consonant families -- so unlike a
 * plain English keyboard, shift in Amharic mode isn't cosmetic, it
 * changes what actually gets typed. That means:
 *
 *   - [primaryText] must reflect shift in BOTH modes. Previously this
 *     was hardcoded to always show lowercase while isAmharic was true,
 *     which silently lied about what tapping the key would produce.
 *
 *   - [secondaryText] (the small fidel preview in the corner) is not a
 *     static glyph baked into the layout file -- it's computed by running
 *     [Transliterator.transliterate] on the same case-resolved letter
 *     that's about to be typed, i.e. the LITERAL function the composer
 *     applies on the keypress. Preview and behavior therefore cannot
 *     disagree by construction (they used to: a parallel lookup path
 *     showed ች on the C key while typing produced ጭ). Toggling shift
 *     updates the preview too (e.g. "h" -> ህ, shift on -> "H" -> ሕ), and
 *     punctuation gets its Ethiopic form ("," -> ፣) from the same call.
 *     A key whose output transliteration leaves unchanged (digits on the
 *     numeric pages) shows no preview rather than echoing itself.
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
    // The letter as it will actually be typed once shift is resolved --
    // used both for the key face and, in Amharic mode, to pick which
    // family's preview glyph to show. Mirrors the resolution logic in
    // TanaKeyboardService.onCharacter so the label never disagrees with
    // what tapping the key produces.
    val effectiveLatin = if (isShift) key.latin.uppercase() else key.latin.lowercase()

    KeyButton(
        primaryText = effectiveLatin,
        secondaryText = if (isAmharic) {
            Transliterator.transliterate(effectiveLatin).takeIf { it != effectiveLatin }
        } else {
            null
        },
        modifier = Modifier.width(width),
        height = height,
        isSpecial = key.latin == "," || key.latin == ".",
        showsPreviewOnPress = true
    ) {
        service.onCharacter(key.latin)
    }
}


/**
 * Shift / caps-lock key. Fixed width (a multiple of a single letter key's
 * width, same as [CharacterKey]) rather than [Modifier.weight] -- Shift only
 * ever appears on the letter layouts, but it shares row 3 with [DeleteKey],
 * which also appears on the Numbers/Symbols layouts; a fixed width is what
 * keeps Delete (and Shift, on the letter layouts) an identical pixel size
 * everywhere instead of a fraction of however much space row 3 happens to
 * have left over on a given layout. Uses the real KeyboardCapslock glyph
 * (the same shift-arrow shape AOSP/Gboard use) with three visual states
 * mirroring [ShiftState]:
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
    width: Dp,
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
        modifier = Modifier.width(width * KeyWeights.SHIFT),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Delete/backspace key. Fixed width (see [ShiftKey]'s doc for why this key
 * in particular needs to be layout-invariant rather than weighted) -- it's
 * the one key present in row 3 of every layout (letters, Numbers, Symbols),
 * so it's the key where a weight-based, row-relative width would otherwise
 * visibly differ depending on which layout is showing.
 *
 * [repeatable] is set so holding the key down deletes continuously (one
 * character immediately, then repeating every ~50ms after a short initial
 * delay) instead of requiring a fresh tap per character -- see
 * [com.addiyon.tanakeyboard.ui.keys.repeatingClickable].
 */
@Composable
fun RowScope.DeleteKey(
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        icon = Icons.Outlined.Backspace,
        modifier = Modifier.width(width * KeyWeights.DELETE),
        height = height,
        isSpecial = true,
        repeatable = true,
        onClick = onClick
    )
}

/**
 * Space bar. Weighted heavily -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys. Shows the
 * active language ("English"/"አማርኛ") instead of the word "space" -- the
 * label always reflects the letter layout that's currently active (or was
 * last active, while a numeric layout is showing), not literally which of
 * the 4 layouts is on screen.
 *
 * A horizontal swipe (left OR right) across the space bar flips the
 * language via [onSwipe] -- there are only two scripts, so either
 * direction just toggles. A plain tap still inserts a space.
 */
@Composable
fun RowScope.SpaceKey(
    isAmharic: Boolean,
    height: Dp,
    onClick: () -> Unit,
    onSwipe: () -> Unit
) {
    KeyButton(
        primaryText = if (isAmharic) "አማርኛ" else "English",
        primaryFontSize = 16.sp,
        iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.weight(KeyWeights.SPACE),
        height = height,
        isSpecial = false,
        onSwipe = onSwipe,
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
 * "?123" / "ABC" number-layout toggle. Weighted -> flexes to fill leftover
 * space in its row. Rendered as a "special" key -> darker surface than
 * letter keys. Smaller font than an ordinary key's label -- "ABC"/"?123"
 * are 3-4 characters, not a single glyph, so they need the room.
 *
 * The same key slot is reused bidirectionally: it reads "?123" on a letter
 * layout (tap to go to numbers/symbols) and "ABC" on the numbers layout
 * (tap to go back), always via the one [onClick] flipping
 * TanaKeyboardService.isNumberMode.
 */
@Composable
fun RowScope.NumberToggleKey(
    isNumberMode: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = if (isNumberMode) "ABC" else "?123",
        primaryFontSize = 14.sp,
        modifier = Modifier.weight(KeyWeights.NUMBER_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * "=\<" / "123" toggle between the two numeric pages. Fixed width (see
 * [ShiftKey]'s doc for the general reasoning) so it renders the same size on
 * the Numbers page as on the Symbols page, rather than a fraction of however
 * much row 3 has left over on each. Rendered as a "special" key -> darker
 * surface than letter keys, with the same reduced font as [NumberToggleKey]
 * (see there for why).
 *
 * Unlike [NumberToggleKey] (which is threaded down as an explicit
 * `isNumberMode` parameter because it affects row-wide rendering), this key's
 * label only matters to itself, so [numbersMode] is read straight from the
 * service by the caller rather than plumbed through KeyboardScreen/KeyRow.
 */
@Composable
fun RowScope.SymbolsToggleKey(
    numbersMode: NumbersMode,
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        primaryText = if (numbersMode == NumbersMode.SYMBOLS) "123" else "=\\<",
        primaryFontSize = 14.sp,
        modifier = Modifier.width(width * KeyWeights.SYMBOLS_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick
    )
}

/**
 * Amharic/English language toggle. Weighted -> flexes to fill leftover space in its row.
 * Rendered as a "special" key -> darker surface than letter keys.
 *
 * Labelled with "ሀለ" over "AB", separated by a thin horizontal divider -- a
 * static hint that this key flips between the two scripts, rather than an
 * active/inactive indicator (which script is currently live is already shown
 * on the space bar's label). The stacked, divided label (instead of a "ሀለ /
 * AB" slash) needs a custom face, so it's passed as [KeyButton]'s content
 * slot rather than a single primaryText string.
 */
@Composable
fun RowScope.LanguageToggleKey(
    height: Dp,
    onClick: () -> Unit
) {
    KeyButton(
        modifier = Modifier.weight(KeyWeights.LANGUAGE_TOGGLE),
        height = height,
        isSpecial = true,
        onClick = onClick,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "ሀለ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(18.dp)
                        .height(1.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(1.dp)
                        )
                )
                Text(text = "AB", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}