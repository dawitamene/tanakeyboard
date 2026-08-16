// ui/KeyboardScreen.kt
package com.addiyon.keyboard.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

import com.addiyon.keyboard.language.LanguagePresentationCategory
import com.addiyon.keyboard.model.KeyData
import com.addiyon.keyboard.model.KeyboardLayout
import com.addiyon.keyboard.model.NumbersMode
import com.addiyon.keyboard.model.StandardKeypadLayout
import com.addiyon.keyboard.model.StandardLatinNumberRow
import com.addiyon.keyboard.model.StandardMoreSymbolsLayout
import com.addiyon.keyboard.model.StandardNumberLayout
import com.addiyon.keyboard.model.StandardSymbolsLayout
import com.addiyon.keyboard.model.standardNumericRows
import com.addiyon.keyboard.ui.emoji.EmojiPanel
import com.addiyon.keyboard.ui.emoji.EmojiSearchHeader

private val KEY_ROWS_VERTICAL_PADDING = 9.dp
private const val OPTIONAL_CONTENT_DIMMED_ALPHA = 0.15f

private fun keyboardRows(
    layout: KeyboardLayout,
    numbersMode: NumbersMode,
    numberRowEnabled: Boolean,
    emojiSearching: Boolean
): List<List<KeyData>> {
    val showLetterNumberRow = numberRowEnabled &&
        (numbersMode == NumbersMode.OFF || emojiSearching)
    return if (showLetterNumberRow) {
        listOf(StandardLatinNumberRow) + layout.rows
    } else {
        standardNumericRows(layout, numbersMode, numberRowEnabled)
    }
}

@Composable
private fun KeyboardSuggestionArea(
    service: KeyboardController,
    isAmharic: Boolean,
    optionalUi: OptionalKeyboardUi,
) {
    SuggestionArea(
        state = service.suggestionUiState,
        isAmharic = isAmharic,
        onTap = {
            service.onSuggestionTapped(it)
            service.hideExpandedSuggestions()
        },
        onOpenSettings = service::openSettings,
        onOpenThemes = service::openThemes,
        onOpenGuide = service::openTypingGuide,
        showTypingGuide = service.showTypingGuide,
        onFeedback = service::openFeedback,
        optionalAction = optionalUi.toolbarAction,
        onClipboard = service::onClipboardAction,
        onEmoji = service::openEmojiPanel,
        onVoice = service::onVoiceInput,
        onExitVoice = service::exitVoiceMode,
        onDismissSuggestions = service::dismissSuggestions,
        onToggleExpanded = service::toggleExpandedSuggestions,
    )
}

@Composable
fun KeyboardScreen(
    service: KeyboardController,
    optionalUi: OptionalKeyboardUi = OptionalKeyboardUi()
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val standardRowSpacing = if (isLandscape) 7.dp else 10.dp

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

    val isAmharic =
        service.activePack.presentationCategory == LanguagePresentationCategory.AMHARIC
    val isShift = service.isShiftEnabled
    val isNumberMode = service.isNumberMode
    val vibrateOnKeypress = service.vibrateOnKeypress
    val soundOnKeypress = service.soundOnKeypress
    val actions = remember(service) {
        object : KeyboardActions {
            override fun character(value: String) = service.onCharacter(value)
            override fun commitText(value: String) = service.commitText(value)
            override fun shift() = service.toggleShift()
            override fun deleteRepeatStart() = service.onDeleteRepeatStart()
            override fun deleteRepeatEnd() = service.onDeleteRepeatEnd()
            override fun delete() = service.onDelete()
            override fun space() = service.onSpace()
            override fun language() = service.toggleLanguage()
            override fun enter() = service.onEnter()
            override fun numbers() = service.toggleNumberMode()
            override fun symbols() = service.toggleSymbolsPage()
            override fun keypad() = service.openKeypad()
        }
    }
    val keyboardState = KeyboardUiState(
        isShift = isShift,
        languageDisplayName = service.activePack.displayName,
        alternateNumbersLabel = service.activePack.alternateNumbersLabel,
        languageKeyPresentation = service.languageKeyPresentation,
        characterPresentation = service::characterKeyPresentation,
        showLanguagePresentation = true,
        showLanguageSwitchKey = service.showLanguageSwitchKey,
        isNumberMode = isNumberMode,
        isEmailField = service.isEmailField,
        isPrivateField = service.isPrivateField,
        numbersMode = service.numbersMode,
        shiftState = service.shiftState,
        enterAction = service.enterAction,
        vibrateOnKeypress = vibrateOnKeypress,
        soundOnKeypress = soundOnKeypress,
    )
    // The user's "Keyboard height" preference, multiplied into every key's
    // height below so the whole keyboard grows/shrinks with the slider.
    val heightScale = service.keyboardHeightScale

    val layout = when (service.numbersMode) {
        NumbersMode.NUMBERS -> StandardNumberLayout
        NumbersMode.SYMBOLS -> StandardSymbolsLayout
        NumbersMode.MORE_SYMBOLS -> StandardMoreSymbolsLayout
        NumbersMode.GEEZ_NUMBERS -> service.activePack.alternateNumbersLayout ?: StandardNumberLayout
        NumbersMode.KEYPAD -> StandardKeypadLayout
        NumbersMode.OFF -> service.activePack.letterLayout
    }
    val optionalContentAlpha = if (optionalUi.contentDimmed) {
        OPTIONAL_CONTENT_DIMMED_ALPHA
    } else {
        1f
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
                        // BoxWithConstraints measures the same width AFTER the
                        // 2.dp horizontal padding (hence -4.dp here), each row
                        // is keyHeight plus the standard row spacer
                        // (keyboardRowsHeight), plus the same vertical padding
                        // top and bottom. Plus the 40.dp suggestion area this
                        // panel renders in place of.
                        val rows = remember(
                            layout,
                            service.numbersMode,
                            service.showNumberRow
                        ) {
                            keyboardRows(
                                layout = layout,
                                numbersMode = service.numbersMode,
                                numberRowEnabled = service.showNumberRow,
                                emojiSearching = false
                            )
                        }
                        val metrics = remember(rows, maxWidth, heightScale, isLandscape) {
                            computeKeyboardMetrics(
                                rows = rows,
                                availableWidth = maxWidth - 4.dp,
                                columns = layout.columns,
                                heightScale = heightScale,
                                isLandscape = isLandscape
                            )
                        }
                        val targetRowCount = remember(service.showNumberRow) {
                            keyboardRowCount(service.showNumberRow)
                        }
                        val panelHeight = 40.dp + keyboardRowsHeight(
                            keyHeight = metrics.keyHeight,
                            rowCount = targetRowCount,
                            rowSpacing = standardRowSpacing
                        ) + KEY_ROWS_VERTICAL_PADDING * 2
                        EmojiPanel(controller = service, height = panelHeight)
                    }
                    return@keyboardContent
                }

                if (emojiSearching) {
                    EmojiSearchHeader(service)
                } else {
                    if (optionalUi.contextualRowVisible) {
                        optionalUi.contextualRow()
                    }
                    if (optionalUi.contentOverlayVisible) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val rows = remember(
                                layout,
                                service.numbersMode,
                                service.showNumberRow
                            ) {
                                keyboardRows(
                                    layout = layout,
                                    numbersMode = service.numbersMode,
                                    numberRowEnabled = service.showNumberRow,
                                    emojiSearching = false
                                )
                            }
                            val metrics = remember(rows, maxWidth, heightScale, isLandscape) {
                                computeKeyboardMetrics(
                                    rows = rows,
                                    availableWidth = maxWidth - 4.dp,
                                    columns = layout.columns,
                                    heightScale = heightScale,
                                    isLandscape = isLandscape
                                )
                            }
                            val targetRowCount = remember(service.showNumberRow) {
                                keyboardRowCount(service.showNumberRow)
                            }
                            val contentHeight = 40.dp + keyboardRowsHeight(
                                keyHeight = metrics.keyHeight,
                                rowCount = targetRowCount,
                                rowSpacing = standardRowSpacing
                            ) + KEY_ROWS_VERTICAL_PADDING * 2
                            Box(modifier = Modifier.height(contentHeight)) {
                                optionalUi.contentOverlay()
                            }
                        }
                        return@keyboardContent
                    }
                    // Always present -- across letter AND number/symbol layouts. When
                    // there's nothing to suggest (always the case on the numeric pages,
                    // where no word composes) it's the quick-action toolbar with the
                    // logo; otherwise the suggestion strip.
                    Box(modifier = Modifier.alpha(optionalContentAlpha)) {
                        KeyboardSuggestionArea(service, isAmharic, optionalUi)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    val showExpanded = service.expandedSuggestionsVisible
                    val expandedWords = remainingSuggestions(service.suggestionUiState)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .alpha(optionalContentAlpha)
                            .padding(horizontal = 2.dp, vertical = KEY_ROWS_VERTICAL_PADDING)
                    ) {

                    // Emoji search always types on the plain English rows (the
                    // query is Latin, and CLDR keywords are English), whatever
                    // language or number row the keyboard itself is in.
                    val effectiveLayout = if (emojiSearching) service.latinSearchLayout else layout
                    val rows = remember(
                        effectiveLayout,
                        service.numbersMode,
                        service.showNumberRow,
                        emojiSearching
                    ) {
                        keyboardRows(
                            layout = effectiveLayout,
                            numbersMode = service.numbersMode,
                            numberRowEnabled = service.showNumberRow,
                            emojiSearching = emojiSearching
                        )
                    }
                    val availableWidth = maxWidth
                    val metrics = remember(
                        effectiveLayout,
                        rows,
                        availableWidth,
                        heightScale,
                        isLandscape
                    ) {
                        computeKeyboardMetrics(
                            rows = rows,
                            availableWidth = availableWidth,
                            columns = effectiveLayout.columns,
                            heightScale = heightScale,
                            isLandscape = isLandscape
                        )
                    }
                    val isKeypadLayout = effectiveLayout === StandardKeypadLayout
                    val targetRowCount = keyboardRowCount(service.showNumberRow)
                    val renderedMetrics = if (isKeypadLayout) {
                        metrics.copy(
                            keyHeight = expandedKeyHeight(
                                baseKeyHeight = metrics.keyHeight,
                                targetRowCount = targetRowCount,
                                actualRowCount = rows.size,
                                targetRowSpacing = standardRowSpacing,
                                actualRowSpacing = if (isLandscape) 3.dp else 4.dp
                            )
                        )
                    } else {
                        metrics
                    }
                    val rowSpacing =
                        if (isKeypadLayout && !isLandscape) 4.dp else standardRowSpacing
                    val prefixRowCount = rows.size - effectiveLayout.rows.size

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                keyboardRowsHeight(
                                    keyHeight = metrics.keyHeight,
                                    rowCount = targetRowCount,
                                    rowSpacing = standardRowSpacing
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
                                    availableWidth = availableWidth,
                                    heightScale = heightScale,
                                    isLandscape = isLandscape
                                )
                                rowColumns != null -> metrics.copy(
                                    keyWidth = availableWidth / rowColumns,
                                    keyHeight = renderedMetrics.keyHeight
                                )
                                else -> renderedMetrics
                            }
                            val showLanguagePresentation = !emojiSearching && layoutRowIndex >= 0
                            KeyRow(
                                row = row,
                                state = keyboardState.copy(
                                    showLanguagePresentation = showLanguagePresentation
                                ),
                                metrics = rowMetrics,
                                actions = actions,
                            )
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showExpanded && expandedWords.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(optionalContentAlpha)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { service.hideExpandedSuggestions() }
                            .padding(horizontal = 2.dp, vertical = KEY_ROWS_VERTICAL_PADDING)
                    ) {
                        val rows = (expandedWords.size + 2) / 3
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            for (row in 0 until rows) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    for (col in 0 until 3) {
                                        val word = expandedWords.getOrNull(row * 3 + col)
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable(enabled = word != null) {
                                                    word?.let {
                                                        val gen = when (val s = service.suggestionUiState) {
                                                            is com.addiyon.keyboard.ui.SuggestionUiState.WordCompletions -> s.actionGeneration
                                                            is com.addiyon.keyboard.ui.SuggestionUiState.NextWordPredictions -> s.actionGeneration
                                                            is com.addiyon.keyboard.ui.SuggestionUiState.EmailSuggestions -> s.actionGeneration
                                                            else -> 0L
                                                        }
                                                        service.onSuggestionTapped(com.addiyon.keyboard.ui.SuggestionTap(it, gen))
                                                        service.hideExpandedSuggestions()
                                                    }
                                                },
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            if (word != null) {
                                                androidx.compose.material3.Text(
                                                    text = word,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        fontSize = 16.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (optionalUi.contentLoadingVisible) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        optionalUi.contentLoadingIndicator()
                    }
                }
            }
        }
    }
}
}
