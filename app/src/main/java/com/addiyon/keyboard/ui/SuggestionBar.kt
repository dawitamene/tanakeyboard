// ui/SuggestionBar.kt
package com.addiyon.keyboard.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.ui.theme.LocalLowRamKeyboard
import com.addiyon.keyboard.suggestion.EmailChip
import com.addiyon.keyboard.voice.VoiceUiState
import com.addiyon.keyboard.voice.isRecording
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

internal const val AMHARIC_SUGGESTION_STRIP_TAG = "amharic-suggestion-strip"
internal const val LANGUAGE_LOADING_INDICATOR_TAG = "language-loading-indicator"
internal const val PREDICTION_LOADING_STRIP_TAG = "prediction-loading-strip"
internal const val PREDICTION_LOADING_INDICATOR_TAG = "prediction-loading-indicator"
internal const val PREDICTION_LOADING_INDICATOR_DELAY_MILLIS = 120L
internal fun predictionLoadingSlotTag(index: Int) = "prediction-loading-slot-$index"

@Composable
fun SuggestionArea(
    state: SuggestionUiState,
    isAmharic: Boolean,
    onTap: (SuggestionTap) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenGuide: () -> Unit,
    onFeedback: () -> Unit,
    onAi: () -> Unit,
    onClipboard: () -> Unit,
    onEmoji: () -> Unit,
    onVoice: () -> Unit = {},
    onExitVoice: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (
            state == SuggestionUiState.Toolbar ||
            state == SuggestionUiState.Private
        ) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            SuggestionUiState.Toolbar -> {
                ToolbarActions(
                    onOpenSettings = onOpenSettings,
                    onOpenThemes = onOpenThemes,
                    onOpenGuide = onOpenGuide,
                    onFeedback = onFeedback,
                    onEmoji = onEmoji,
                )
                MicToolbarIcon(isListening = false, onClick = onVoice)
            }

            SuggestionUiState.Private -> {
                ToolbarActions(
                    onOpenSettings = onOpenSettings,
                    onOpenThemes = onOpenThemes,
                    onOpenGuide = onOpenGuide,
                    onFeedback = onFeedback,
                    onEmoji = onEmoji,
                )
            }

            is SuggestionUiState.Voice -> {
                ToolbarIcon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    "Exit voice input",
                    onExitVoice,
                )
                Text(
                    text = voiceLabel(state.voiceUiState, isAmharic),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                MicToolbarIcon(
                    isListening = state.voiceUiState.isRecording,
                    onClick = onVoice,
                )
            }

            else -> {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (state) {
                        SuggestionUiState.LoadingLanguage -> LanguageLoadingDot()
                        SuggestionUiState.LoadingPredictions,
                        SuggestionUiState.LoadingCompletions -> PredictionLoadingStrip()
                        is SuggestionUiState.WordCompletions -> {
                            val scopedTap: (String) -> Unit = {
                                onTap(SuggestionTap(it, state.actionGeneration))
                            }
                            if (isAmharic) {
                                AmharicSuggestionStrip(
                                    suggestions = state.words,
                                    isPredictions = false,
                                    onTap = scopedTap,
                                )
                            } else {
                                EnglishSuggestionStrip(state.words, scopedTap)
                            }
                        }

                        is SuggestionUiState.NextWordPredictions -> {
                            val scopedTap: (String) -> Unit = {
                                onTap(SuggestionTap(it, state.actionGeneration))
                            }
                            if (isAmharic) {
                                AmharicSuggestionStrip(
                                    suggestions = state.words,
                                    isPredictions = true,
                                    onTap = scopedTap,
                                )
                            } else {
                                EnglishSuggestionStrip(state.words, scopedTap)
                            }
                        }

                        is SuggestionUiState.EmailSuggestions -> {
                            val scopedTap: (String) -> Unit = {
                                onTap(SuggestionTap(it, state.actionGeneration))
                            }
                            EmailSuggestionStrip(state.chips, scopedTap)
                        }

                        SuggestionUiState.Private,
                        SuggestionUiState.Toolbar,
                        is SuggestionUiState.Voice -> Unit
                    }
                }
                MicToolbarIcon(isListening = false, onClick = onVoice)
            }
        }
    }
}

@Composable
private fun ToolbarActions(
    onOpenSettings: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenGuide: () -> Unit,
    onFeedback: () -> Unit,
    onEmoji: () -> Unit,
) {
    ToolbarIcon(Icons.Outlined.Settings, "Settings", onOpenSettings)
    ToolbarIcon(Icons.Outlined.EmojiEmotions, "Emoji", onEmoji)
    ToolbarIcon(Icons.Outlined.MenuBook, "Typing guide", onOpenGuide)
    ToolbarIcon(Icons.Outlined.Feedback, "Feedback", onFeedback)
    ToolbarIcon(Icons.Outlined.Palette, "Themes", onOpenThemes)
}

@Composable
private fun PredictionLoadingStrip() {
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PREDICTION_LOADING_INDICATOR_DELAY_MILLIS)
        showIndicator = true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag(PREDICTION_LOADING_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(predictionLoadingSlotTag(index)),
                contentAlignment = Alignment.Center,
            ) {
                if (index == 1 && showIndicator) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .testTag(PREDICTION_LOADING_INDICATOR_TAG)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                shape = CircleShape,
                            )
                    )
                }
            }
            if (index < 2) {
                SuggestionDivider()
            }
        }
    }
}

@Composable
private fun LanguageLoadingDot() {
    val alpha = if (!animationsAllowed()) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "loading-dot")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loading-alpha",
        )
        animated
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LANGUAGE_LOADING_INDICATOR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(alpha)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                )
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha((1f - alpha).coerceIn(0.35f, 1f))
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                )
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(alpha)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                )
        )
    }
}

// Deliberately static per state -- recognized text streams into the field's
// composing region, never into this label, and session churn inside the
// controller doesn't surface here. That's what keeps the bar flicker-free.
private fun voiceLabel(state: VoiceUiState, isAmharic: Boolean): String = when (state) {
    VoiceUiState.Idle -> ""
    VoiceUiState.Listening -> if (isAmharic) "በማዳመጥ ላይ..." else "Listening…"
    VoiceUiState.Paused -> "Paused"
    VoiceUiState.PermissionRequired -> "Microphone permission required"
    is VoiceUiState.Unavailable -> state.message
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tinted: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (tinted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The mic toolbar icon, pulsing gently (scale + fade) on an infinite loop
 * while [isListening] is true, so the toolbar reads as "actively recording"
 * at a glance instead of just swapping to the filled glyph.
 */
@Composable
private fun MicToolbarIcon(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val pulse = if (isListening && animationsAllowed()) {
        val transition = rememberInfiniteTransition(label = "micPulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micPulseScale"
        )
        animated
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.Mic else Icons.Outlined.Mic,
            contentDescription = if (isListening) "Stop voice input" else "Voice input",
            tint = if (isListening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier
                .size(22.dp)
                .scale(pulse)
        )
    }
}

@Composable
private fun animationsAllowed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed &&
        !LocalLowRamKeyboard.current &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled())
}

@Composable
private fun AmharicSuggestionStrip(
    suggestions: List<String>,
    isPredictions: Boolean,
    onTap: (String) -> Unit
) {
    if (suggestions.size in 2..3) {
        AmharicFixedSuggestionStrip(suggestions, isPredictions, onTap)
    } else {
        AmharicScrollableSuggestionStrip(suggestions, isPredictions, onTap)
    }
}

@Composable
private fun AmharicFixedSuggestionStrip(
    suggestions: List<String>,
    isPredictions: Boolean,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
            .testTag(AMHARIC_SUGGESTION_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until 3) {
            val word = suggestions.getOrNull(index)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = word != null) { word?.let(onTap) },
                contentAlignment = Alignment.Center
            ) {
                if (word != null) {
                    AmharicSuggestionText(
                        word = word,
                        isTop = index == 0 && !isPredictions
                    )
                }
            }
            if (index < 2) {
                SuggestionDivider()
            }
        }
    }
}

@Composable
private fun AmharicScrollableSuggestionStrip(
    suggestions: List<String>,
    isPredictions: Boolean,
    onTap: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        val viewportWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .testTag(AMHARIC_SUGGESTION_STRIP_TAG)
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = viewportWidth)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                suggestions.forEachIndexed { index, word ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable { onTap(word) },
                        contentAlignment = Alignment.Center
                    ) {
                        AmharicSuggestionText(
                            word = word,
                            isTop = index == 0 && !isPredictions
                        )
                    }
                    if (index < suggestions.lastIndex) {
                        SuggestionDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AmharicSuggestionText(
    word: String,
    isTop: Boolean
) {
    Text(
        text = word,
        fontSize = 16.sp,
        fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp),
        color = if (isTop) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SuggestionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight(0.5f)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    )
}

/**
 * English: always three equal-width, centered slots. Empty slots (fewer than
 * three suggestions) render blank but still hold their third of the row, so
 * the present chips stay put instead of re-centering as the count changes.
 */
@Composable
private fun EnglishSuggestionStrip(
    suggestions: List<String>,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            EnglishSuggestionSlot(word = suggestions.getOrNull(i), onTap = onTap)

            if (i < 2) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.5f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun RowScope.EnglishSuggestionSlot(
    word: String?,
    onTap: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = word != null) { word?.let(onTap) },
        contentAlignment = Alignment.Center
    ) {
        if (word != null) {
            // autoSize shrinks the font (only down to 15sp) when the word is
            // too long to fit its slot at the normal 16sp -- short words stay
            // full size, long ones scale down slightly and then ellipsize
            // rather than shrinking to an unreadable size.
            BasicText(
                text = word,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 15.sp,
                    maxFontSize = 16.sp,
                    stepSize = 1.sp
                ),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

/**
 * Email-domain chips: same three-slot equal-width layout as the English
 * strip, but each chip's [EmailChip.display] label is what's shown (just
 * the domain suffix, not the full address) while the chip tap invokes
 * [onTap] with [EmailChip.commit] (the full text that lands in the field,
 * e.g. "jo@gmail.com" rather than "@gmail.com").
 */
@Composable
private fun EmailSuggestionStrip(
    chips: List<EmailChip>,
    onTap: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            EmailSuggestionSlot(chip = chips.getOrNull(i), onTap = onTap)
            if (i < 2) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.5f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun RowScope.EmailSuggestionSlot(
    chip: EmailChip?,
    onTap: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = chip != null) { chip?.let { onTap(it.commit) } },
        contentAlignment = Alignment.Center
    ) {
        if (chip != null) {
            BasicText(
                text = chip.display,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = 16.sp,
                    stepSize = 1.sp
                ),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
