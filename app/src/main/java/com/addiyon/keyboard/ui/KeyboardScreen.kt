// ui/KeyboardScreen.kt
package com.addiyon.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.MainActivity
import com.addiyon.keyboard.AddiyonKeyboardService
import com.addiyon.keyboard.layout.AmharicLayout
import com.addiyon.keyboard.layout.EnglishLayout
import com.addiyon.keyboard.layout.GeezNumbersLayout
import com.addiyon.keyboard.layout.KeypadLayout
import com.addiyon.keyboard.layout.LatinNumberRow
import com.addiyon.keyboard.layout.MoreSymbolsLayout
import com.addiyon.keyboard.layout.NumberLayout
import com.addiyon.keyboard.layout.SymbolsLayout
import com.addiyon.keyboard.layout.numericRows
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.ui.emoji.EmojiPanel
import com.addiyon.keyboard.ui.emoji.EmojiSearchHeader

private fun keyboardRows(
    layout: KeyboardLayout,
    numbersMode: NumbersMode,
    numberRowEnabled: Boolean,
    emojiSearching: Boolean
): List<List<KeyData>> {
    val showLetterNumberRow = numberRowEnabled &&
        (numbersMode == NumbersMode.OFF || emojiSearching)
    return if (showLetterNumberRow) {
        listOf(LatinNumberRow) + layout.rows
    } else {
        numericRows(layout, numbersMode, numberRowEnabled)
    }
}

@Composable
fun KeyboardScreen(
    service: AddiyonKeyboardService
) {

    // NOTE: we deliberately do NOT read service.currentInputConnection here.
    // Reading it once at composition time would bake a possibly-stale
    // InputConnection into every key's onClick closure below. Android does
    // not guarantee this composable gets recomposed every time the keyboard
    // is hidden and reshown, but the system DOES swap out the underlying
    // InputConnection for the new input session. If we captured it here,
    // reopening the keyboard without a fresh composition would leave every
    // key silently writing into a dead InputConnection. Instead, each key's
    // onClick (in KeyRow / KeyComposables) fetches
    // service.currentInputConnection fresh at the moment it's tapped.

    val isAmharic = service.isAmharic
    val isShift = service.isShiftEnabled
    val isNumberMode = service.isNumberMode
    val vibrateOnKeypress = service.vibrateOnKeypress
    val soundOnKeypress = service.soundOnKeypress

    val layout = when (service.numbersMode) {
        NumbersMode.NUMBERS -> NumberLayout
        NumbersMode.SYMBOLS -> SymbolsLayout
        NumbersMode.MORE_SYMBOLS -> MoreSymbolsLayout
        NumbersMode.GEEZ_NUMBERS -> GeezNumbersLayout
        NumbersMode.KEYPAD -> KeypadLayout
        NumbersMode.OFF -> if (isAmharic) AmharicLayout else EnglishLayout
    }

    // Outer Box lets the feedback sheet overlay the whole keyboard; it wraps
    // the keyboard Column and sizes to it, so the overlay's matchParentSize
    // covers exactly the keyboard area.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) keyboardContent@ {
                // Emoji SEARCH mode is not the fixed-height panel: it's the
                // search header (query + result strip) in place of the
                // suggestion area, with the real English key rows below --
                // rendered by the shared key-rows block at the bottom of this
                // Column, with the layout forced to English.
                val emojiSearching = service.showEmojiPanel && service.emojiSearchQuery != null

                // The emoji panel replaces BOTH the suggestion strip and the key
                // rows at exactly their combined height (computed below from the
                // same metrics the key branch uses), so opening/closing it never
                // resizes the IME window. The composer is committed on open, so
                // no composing word is ever visible alongside it.
                if (service.showEmojiPanel && !emojiSearching) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // Mirror the key branch's sizing exactly so opening the
                        // emoji panel never resizes the IME window: its
                        // BoxWithConstraints measures width AFTER 4.dp horizontal
                        // padding (hence -8.dp here), each row is keyHeight plus
                        // a 6.dp spacer (keyboardRowsHeight), and the box adds
                        // 6.dp vertical padding top and bottom (12.dp). Plus the
                        // 40.dp suggestion area this panel renders in place of.
                        val rows = keyboardRows(
                            layout = layout,
                            numbersMode = service.numbersMode,
                            numberRowEnabled = service.showNumberRow,
                            emojiSearching = false
                        )
                        val metrics = computeKeyboardMetrics(
                            rows = rows,
                            availableWidth = maxWidth - 8.dp,
                            columns = layout.columns
                        )
                        val targetRowCount = keyboardRowCount(service.showNumberRow)
                        val panelHeight = 40.dp + keyboardRowsHeight(
                            keyHeight = metrics.keyHeight,
                            rowCount = targetRowCount
                        ) + 12.dp
                        EmojiPanel(service = service, height = panelHeight)
                    }
                    return@keyboardContent
                }

                if (emojiSearching) {
                    EmojiSearchHeader(service)
                } else
                // Always present -- across letter AND number/symbol layouts. When
                // there's nothing to suggest (always the case on the numeric pages,
                // where no word composes) it's the quick-action toolbar with the
                // logo; otherwise the suggestion strip.
                SuggestionArea(
                    suggestions = service.suggestions,
                    isAmharic = isAmharic,
                    isPredictions = service.suggestionsArePredictions,
                    onTap = { word -> service.onSuggestionTapped(word) },
                    onOpenSettings = { service.openAppScreen(MainActivity.SCREEN_SETTINGS) },
                    onOpenThemes = { service.openAppScreen(MainActivity.SCREEN_THEMES) },
                    onOpenGuide = { service.openAppScreen(MainActivity.SCREEN_GUIDE) },
                    onFeedback = { service.openFeedbackScreen() },
                    onAi = { service.onAiAction() },
                    onClipboard = { service.onClipboardAction() },
                    onEmoji = { service.openEmojiPanel() },
                    voiceUiState = service.voiceUiState,
                    onVoice = { service.onVoiceInput() },
                    onExitVoice = { service.exitVoiceMode() }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {

                    // Emoji search always types on the plain English rows (the
                    // query is Latin, and CLDR keywords are English), whatever
                    // language or number row the keyboard itself is in.
                    val effectiveLayout = if (emojiSearching) EnglishLayout else layout
                    val rows = keyboardRows(
                        layout = effectiveLayout,
                        numbersMode = service.numbersMode,
                        numberRowEnabled = service.showNumberRow,
                        emojiSearching = emojiSearching
                    )
                    val availableWidth = maxWidth
                    val metrics = computeKeyboardMetrics(
                        rows = rows,
                        availableWidth = availableWidth,
                        columns = effectiveLayout.columns
                    )
                    val isKeypadLayout = effectiveLayout === KeypadLayout
                    val targetRowCount = keyboardRowCount(service.showNumberRow)
                    val renderedMetrics = if (isKeypadLayout) {
                        metrics.copy(
                            keyHeight = expandedKeyHeight(
                                baseKeyHeight = metrics.keyHeight,
                                targetRowCount = targetRowCount,
                                actualRowCount = rows.size
                            )
                        )
                    } else {
                        metrics
                    }
                    val rowSpacing = if (isKeypadLayout) 4.dp else 6.dp
                    val prefixRowCount = rows.size - effectiveLayout.rows.size

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                keyboardRowsHeight(
                                    keyHeight = metrics.keyHeight,
                                    rowCount = targetRowCount
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing)
                    ) {

                        rows.forEachIndexed { index, row ->
                            // An added top row always commits its literal
                            // character, so it never shows
                            // the Amharic fidel corner preview, regardless of
                            // the active language.
                            val layoutRowIndex = index - prefixRowCount
                            val rowColumns = effectiveLayout.rowColumns
                                ?.getOrNull(layoutRowIndex)
                            val rowMetrics = when {
                                layoutRowIndex < 0 -> computeKeyboardMetrics(
                                    rows = listOf(row),
                                    availableWidth = availableWidth
                                )
                                rowColumns != null -> metrics.copy(
                                    keyWidth = availableWidth / rowColumns,
                                    keyHeight = renderedMetrics.keyHeight
                                )
                                else -> renderedMetrics
                            }
                            val rowIsAmharic = isAmharic && !emojiSearching &&
                                layoutRowIndex >= 0
                            KeyRow(
                                row = row,
                                isShift = isShift,
                                isAmharic = rowIsAmharic,
                                isNumberMode = isNumberMode,
                                metrics = rowMetrics,
                                service = service,
                                vibrateOnKeypress = vibrateOnKeypress,
                                soundOnKeypress = soundOnKeypress
                            )
                        }
                    }
                }
            }
        }
    }
}
