package com.addiyon.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.LatinNumberRow
import com.addiyon.keyboard.model.EnterAction
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.ShiftState
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.ui.AppPageTopBar
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_DEFAULT
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MAX
import com.addiyon.keyboard.ui.KEYBOARD_HEIGHT_SCALE_MIN
import com.addiyon.keyboard.ui.KeyButton
import com.addiyon.keyboard.ui.KeyboardMetrics
import com.addiyon.keyboard.ui.computeKeyboardMetrics
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.keyboardRowCount
import com.addiyon.keyboard.ui.keyboardRowsHeight
import com.addiyon.keyboard.ui.keys.DeleteKey
import com.addiyon.keyboard.ui.keys.EnterKey
import com.addiyon.keyboard.ui.keys.LanguageToggleKey
import com.addiyon.keyboard.ui.keys.NumberToggleKey
import com.addiyon.keyboard.ui.keys.ShiftKey
import com.addiyon.keyboard.ui.keys.SpaceKey
import com.addiyon.keyboard.ui.keys.usesSpecialBackground
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import kotlin.math.roundToInt

/** testTags so instrumented tests can drive the resize controls. */
const val KEYBOARD_HEIGHT_HANDLE_TAG = "keyboard_height_handle"
const val KEYBOARD_HEIGHT_SLIDER_TAG = "keyboard_height_slider"

/** How translucent the replica's keys are, to read as a non-typing preview. */
private const val PREVIEW_ALPHA = 0.6f

/** Light-gray face for the Reset/Done controls, fixed across light/dark. */
private val ControlButtonBackground = Color(0xFFE6E6E6)
private val ControlButtonContent = Color(0xFF1F1F1F)

/**
 * "Keyboard height" screen: direct-manipulation resizing. A faithful, live
 * replica of the real keyboard sits at the bottom of the screen; dragging the
 * handle on its top edge up/down grows/shrinks it in real time. The chosen
 * scale is written to [KeyboardPrefs.setKeyboardHeightScale] on release, and the
 * IME service observes that pref so the real keyboard adopts the same size.
 *
 * The replica reuses the real key composables ([ShiftKey], [SpaceKey], ...) and
 * the real sizing math ([computeKeyboardMetrics]) under the user's own keyboard
 * palette ([CustomKeyboardTheme]), so what the user drags looks and measures
 * exactly like the keyboard they type on. Its keys are drawn translucent
 * ([PREVIEW_ALPHA]) so it clearly reads as a preview, and only the character
 * keys use a local, non-interactive face ([PreviewCharacterKey]) since the real
 * [CharacterKey] needs the running IME service to dispatch typing -- which a
 * settings screen has no access to.
 *
 * The replica reserves the bottom system-bar space the real keyboard reserves
 * (via [WindowInsets.systemBars], mirroring KeyboardScreen), so it sits at the
 * same height off the bottom -- above the navigation / IME-switcher bar -- and
 * doesn't read as lower than the real thing.
 */
@Composable
fun KeyboardHeightScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val isDark = isSystemInDarkTheme()
    // Snapshot the keyboard's own settings so the replica matches what the user
    // actually types on (their palette, script, and number-row choice).
    val palette = remember { KeyboardPrefs.palette(context) }
    val isAmharic = remember { KeyboardPrefs.amharicMode(context) }
    val showNumberRow = remember { KeyboardPrefs.numberRow(context) }
    var scale by remember { mutableFloatStateOf(KeyboardPrefs.keyboardHeightScale(context)) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Consume nothing: the replica reserves the bottom system-bar inset
        // itself (below) via the SAME WindowInsets.systemBars source the real
        // keyboard uses, so its offset from the bottom matches exactly -- and is
        // 0 on phones with no bottom bar. The top bar handles the status bar on
        // its own.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AppPageTopBar(
                title = strings.keyboardHeight,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Only the top inset here; the replica reserves the BOTTOM
                // system-bar space itself (below), exactly like the real
                // keyboard, so it isn't double-counted and lands at the same
                // height off the bottom.
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.keyboardHeightHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // A smooth (unstepped) slider as an alternative to dragging the
                // keyboard's edge; both drive the same live scale.
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = { KeyboardPrefs.setKeyboardHeightScale(context, scale) },
                    valueRange = KEYBOARD_HEIGHT_SCALE_MIN..KEYBOARD_HEIGHT_SCALE_MAX,
                    modifier = Modifier.testTag(KEYBOARD_HEIGHT_SLIDER_TAG)
                )
            }

            // Push the keyboard to the bottom of the screen, like a real IME.
            Spacer(Modifier.weight(1f))

            // The replica is themed as the real keyboard so its colors match.
            CustomKeyboardTheme(isDarkTheme = isDark, palette = palette) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Reserve the same bottom bar space the real keyboard
                        // does, so the replica sits above the nav / IME-switcher
                        // bar rather than flush against the screen bottom.
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val layout = if (isAmharic) AmharicLayout else EnglishLayout
                        val rows =
                            if (showNumberRow) listOf(LatinNumberRow) + layout.rows else layout.rows
                        val rowCount = keyboardRowCount(showNumberRow)
                        // Mirror KeyboardScreen: the key rows sit inside 4.dp of
                        // horizontal padding, so the width they size against is
                        // the full width minus 8.dp.
                        val availableWidth = maxWidth - 8.dp
                        val metrics = computeKeyboardMetrics(
                            rows = rows,
                            availableWidth = availableWidth,
                            columns = layout.columns,
                            heightScale = scale
                        )
                        // The keyboard's height change per unit scale, in px:
                        // d(height)/d(scale) = baseKeyHeight * rowCount, so this
                        // is the conversion factor for a 1:1 "the border follows
                        // your finger" drag.
                        val heightPerScalePx = with(density) {
                            computeKeyboardMetrics(
                                rows = rows,
                                availableWidth = availableWidth,
                                columns = layout.columns,
                                heightScale = 1f
                            ).keyHeight.toPx()
                        } * rowCount

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            ResizeHandle(
                                onDrag = { dy ->
                                    scale = (scale - dy / heightPerScalePx)
                                        .coerceIn(KEYBOARD_HEIGHT_SCALE_MIN, KEYBOARD_HEIGHT_SCALE_MAX)
                                },
                                onDragEnd = { KeyboardPrefs.setKeyboardHeightScale(context, scale) }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                // The keys themselves -- translucent, so the
                                // surface reads as a preview, not a live keyboard.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(keyboardRowsHeight(metrics.keyHeight, rowCount))
                                        .alpha(PREVIEW_ALPHA),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rows.forEach { row ->
                                        PreviewKeyRow(row = row, metrics = metrics, isAmharic = isAmharic)
                                    }
                                }

                                // Crisp controls floating over the dimmed keys,
                                // both on a light-gray face.
                                val controlColors = ButtonDefaults.buttonColors(
                                    containerColor = ControlButtonBackground,
                                    contentColor = ControlButtonContent
                                )
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scale = KEYBOARD_HEIGHT_SCALE_DEFAULT
                                            KeyboardPrefs.setKeyboardHeightScale(context, scale)
                                        },
                                        colors = controlColors
                                    ) {
                                        Text(strings.reset)
                                    }
                                    Button(
                                        onClick = {
                                            KeyboardPrefs.setKeyboardHeightScale(context, scale)
                                            onBack()
                                        },
                                        colors = controlColors
                                    ) {
                                        Text(strings.done)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The grab bar on the keyboard's top edge. The whole bar is the touch target;
 * a centered pill is the visual affordance. Vertical drags are reported to
 * [onDrag] (px, positive = downward) and the gesture's end to [onDragEnd].
 */
@Composable
private fun ResizeHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .testTag(KEYBOARD_HEIGHT_HANDLE_TAG)
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = onDragEnd) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
    }
}

/**
 * One row of the replica. Reuses the real special-key composables verbatim
 * (they already take a plain onClick, no service) with inert callbacks; only
 * character keys need the local face.
 */
@Composable
private fun PreviewKeyRow(
    row: List<KeyData>,
    metrics: KeyboardMetrics,
    isAmharic: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.forEach { key ->
            when (key) {
                is KeyData.Character ->
                    PreviewCharacterKey(key, isAmharic, metrics.keyWidth, metrics.keyHeight)

                KeyData.Shift ->
                    ShiftKey(ShiftState.OFF, metrics.keyWidth, metrics.keyHeight, false, false) {}

                KeyData.Delete ->
                    DeleteKey(
                        width = metrics.keyWidth,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onPressStart = {},
                        onPressEnd = {},
                        onClick = {}
                    )

                KeyData.Space ->
                    SpaceKey(
                        isAmharic = isAmharic,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onClick = {},
                        onSwipe = {}
                    )

                KeyData.Enter ->
                    EnterKey(
                        action = EnterAction.NEWLINE,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onClick = {}
                    )

                KeyData.NumberToggle ->
                    NumberToggleKey(
                        isNumberMode = false,
                        isAmharic = isAmharic,
                        height = metrics.keyHeight,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onClick = {}
                    )

                KeyData.LanguageToggle ->
                    LanguageToggleKey(
                        height = metrics.keyHeight,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onClick = {}
                    )

                // Not present on the letter layouts this screen renders.
                KeyData.SymbolsToggle, KeyData.KeypadToggle -> Unit
            }
        }
    }
}

/**
 * A non-interactive letter key for the replica, mirroring [CharacterKey]'s
 * face at shift-off: the Latin letter centered, plus the live fidel corner
 * glyph in Amharic (computed the same way the real key does, via
 * [Transliterator.transliterate], so it never bakes in a divergent glyph).
 */
@Composable
private fun PreviewCharacterKey(
    key: KeyData.Character,
    isAmharic: Boolean,
    width: Dp,
    height: Dp
) {
    val latin = remember(key.latin) { key.latin.lowercase() }
    val amharic = remember(latin) { Transliterator.transliterate(latin) }
    val isPunctuation = key.latin == "," || key.latin == "."

    val primaryText: String
    val secondaryText: String?
    val secondarySize: TextUnit
    if (isPunctuation && isAmharic) {
        primaryText = amharic
        secondaryText = latin
        secondarySize = 14.sp
    } else if (isPunctuation) {
        primaryText = latin
        secondaryText = null
        secondarySize = 10.sp
    } else {
        primaryText = latin
        secondaryText = amharic.takeIf { isAmharic && it != latin }
        secondarySize = 10.sp
    }

    KeyButton(
        primaryText = primaryText,
        primaryFontSize = 23.sp,
        secondaryText = secondaryText,
        secondaryFontSize = secondarySize,
        modifier = Modifier.width(width * key.width),
        height = height,
        isSpecial = key.usesSpecialBackground(),
        onClick = {}
    )
}
