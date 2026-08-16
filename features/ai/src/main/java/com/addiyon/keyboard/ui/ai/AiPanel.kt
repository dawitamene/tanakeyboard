package com.addiyon.keyboard.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.addiyon.keyboard.ai.AiError
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ai.CustomTone
import com.addiyon.keyboard.ui.SuggestionChevronLeftButton
import com.addiyon.keyboard.ui.design.AddiyonAiToneGlowColors
import com.addiyon.keyboard.ui.design.AddiyonBorders
import com.addiyon.keyboard.ui.design.AddiyonElevation
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.design.addiyonColors

fun aiResponseVariantTag(strength: AiStrength): String =
    "ai.response.variant.${strength.name.lowercase()}"

fun aiPanelToneIconTag(tab: AiToneTab): String =
    "ai.tone.icon.${tab.name.lowercase()}"

const val AI_TONE_ACTION_ROW_TAG = "ai.tone.action.row"
const val AI_TONE_ADD_TAG = "ai.tone.add"
const val AI_RESULTS_OVERLAY_TAG = "ai.results.overlay"
const val AI_RESULTS_DISMISS_TAG = "ai.results.dismiss"
const val AI_RESULTS_SKELETON_TAG = "ai.results.skeleton"

fun aiResultSkeletonCardTag(index: Int): String = "ai.results.skeleton.card.$index"

fun aiCustomToneChipTag(custom: CustomTone): String = "ai.tone.custom.${custom.id}"

@Composable
fun AiToneRow(
    selectedTab: AiToneTab?,
    customTones: List<CustomTone> = emptyList(),
    selectedCustomToneId: String? = null,
    isLoading: Boolean,
    enabled: Boolean,
    onTabSelected: (AiToneTab) -> Unit,
    onCustomToneSelected: (CustomTone) -> Unit = {},
    onAddCustomTone: (() -> Unit)? = null,
    strings: AiUiStrings,
    onBack: (() -> Unit)? = null,
    useKeyboardTopRowSpacing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val toneShape = RoundedCornerShape(AddiyonRadii.pill)
    val toneGlowVisuals = toneGlowVisuals(isLoading)
    val rowContentPadding = if (useKeyboardTopRowSpacing) {
        PaddingValues(horizontal = AddiyonSpacing.xs, vertical = AddiyonSpacing.xs)
    } else {
        PaddingValues(horizontal = AddiyonSpacing.xs)
    }
    val rowHeight = if (useKeyboardTopRowSpacing) {
        AddiyonSizes.compact + AddiyonSpacing.xs * 2
    } else {
        AddiyonSizes.keyboardAction
    }
    Box(
        modifier = modifier
            .height(rowHeight)
            .fillMaxWidth()
            .testTag(AI_TONE_ACTION_ROW_TAG)
            .padding(rowContentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Spacer(Modifier.width(AddiyonSizes.compact))
            }
            AiToneTab.DefaultTabs.forEach { tab ->
                AiToneChip(
                    icon = toneIcon(tab),
                    label = toneLabel(tab, strings),
                    selected = selectedTab == tab,
                    enabled = enabled,
                    iconColor = toneIconColor(tab),
                    toneShape = toneShape,
                    toneGlowVisuals = toneGlowVisuals,
                    onClick = { onTabSelected(tab) },
                    iconModifier = Modifier.testTag(aiPanelToneIconTag(tab))
                )
            }
            customTones.forEach { custom ->
                AiToneChip(
                    icon = customToneIcon(custom.icon),
                    label = custom.label,
                    selected = selectedCustomToneId == custom.id,
                    enabled = enabled,
                    iconColor = customToneColor(custom.color),
                    toneShape = toneShape,
                    toneGlowVisuals = toneGlowVisuals,
                    onClick = { onCustomToneSelected(custom) },
                    iconModifier = Modifier.testTag(aiCustomToneChipTag(custom))
                )
            }
            if (onAddCustomTone != null) {
                AiToneChip(
                    icon = Icons.Outlined.Add,
                    label = strings.aiAddCustomTone,
                    selected = false,
                    enabled = true,
                    iconColor = MaterialTheme.colorScheme.primary,
                    toneShape = toneShape,
                    toneGlowVisuals = toneGlowVisuals,
                    onClick = onAddCustomTone,
                    iconModifier = Modifier.testTag(AI_TONE_ADD_TAG)
                )
            }
        }
        onBack?.let {
            val glassSurface = MaterialTheme.addiyonColors.resultSurface
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(AddiyonSizes.compact)
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(
                            radius = AddiyonElevation.raised,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = BACK_GLASS_SHADOW_ALPHA
                            )
                        )
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                glassSurface.copy(alpha = BACK_GLASS_TOP_ALPHA),
                                glassSurface.copy(alpha = BACK_GLASS_BOTTOM_ALPHA)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                SuggestionChevronLeftButton(
                    onClick = it,
                    contentDescription = strings.back,
                    testTag = AI_RESULTS_DISMISS_TAG,
                    buttonSize = AddiyonSizes.compact,
                    containerSize = AddiyonSizes.compact,
                    iconSize = AddiyonSizes.iconMedium,
                    containerColor = Color.Transparent,
                    iconTint = MaterialTheme.addiyonColors.onResultSurface
                )
            }
        }
    }
}

@Composable
private fun AiToneChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    iconColor: Color,
    toneShape: Shape,
    toneGlowVisuals: ToneGlowVisuals,
    onClick: () -> Unit,
    iconModifier: Modifier
) {
    val contentAlpha = if (enabled) 1f else DISABLED_TONE_ALPHA
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
    val glowModifier = if (selected && enabled) {
        Modifier.dropShadow(
            shape = toneShape,
            shadow = toneGlowVisuals.glow
        )
    } else {
        Modifier
    }
    Surface(
        modifier = glowModifier,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = toneShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        border = if (selected && enabled) toneGlowVisuals.border else null
    ) {
        Row(
            modifier = Modifier
                .height(AddiyonSizes.compact)
                .padding(horizontal = AddiyonSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = contentAlpha),
                modifier = iconModifier.size(AddiyonSizes.iconSmall)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AiResultsOverlay(
    state: AiUiState,
    strings: AiUiStrings,
    onReplaceVariant: (AiStrength) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(AI_RESULTS_OVERLAY_TAG)
    ) {
        if (state.isLoading) {
            AiResultsSkeleton()
        } else if (state.variantResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = AddiyonSpacing.xs,
                        end = AddiyonSpacing.xs,
                        bottom = AddiyonSpacing.xs
                    ),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
            ) {
                state.variantResults.entries
                    .sortedBy { it.key.ordinal }
                    .forEach { (strength, result) ->
                        Surface(
                            onClick = { onReplaceVariant(strength) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag(aiResponseVariantTag(strength)),
                            shape = RoundedCornerShape(AddiyonRadii.medium),
                            color = MaterialTheme.addiyonColors.resultSurface,
                            contentColor = MaterialTheme.addiyonColors.onResultSurface
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(AddiyonSpacing.sm),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = result.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.addiyonColors.onResultSurface
                                )
                            }
                        }
                    }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AddiyonSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = responseErrorMessage(state.error, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AiResultsSkeleton() {
    val transition = rememberInfiniteTransition(label = "aiResultSkeleton")
    val pulseAlpha by transition.animateFloat(
        initialValue = SKELETON_PULSE_MIN_ALPHA,
        targetValue = SKELETON_PULSE_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AddiyonMotion.gentle),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aiResultSkeletonPulse"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = AddiyonSpacing.xs,
                end = AddiyonSpacing.xs,
                bottom = AddiyonSpacing.xs
            )
            .testTag(AI_RESULTS_SKELETON_TAG),
        verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
    ) {
        repeat(RESULT_SKELETON_CARD_COUNT) { index ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(aiResultSkeletonCardTag(index)),
                shape = RoundedCornerShape(AddiyonRadii.medium),
                color = MaterialTheme.addiyonColors.resultSurface,
                contentColor = MaterialTheme.addiyonColors.onResultSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AddiyonSpacing.sm),
                    verticalArrangement = Arrangement.Center
                ) {
                    SkeletonLine(pulseAlpha = pulseAlpha)
                    Spacer(Modifier.height(AddiyonSpacing.xs))
                    SkeletonLine(
                        widthFraction = SKELETON_MEDIUM_LINE_WIDTH,
                        pulseAlpha = pulseAlpha
                    )
                    Spacer(Modifier.height(AddiyonSpacing.xs))
                    SkeletonLine(
                        widthFraction = SKELETON_SHORT_LINE_WIDTH,
                        pulseAlpha = pulseAlpha
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    pulseAlpha: Float,
    widthFraction: Float = SKELETON_FULL_LINE_WIDTH
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(AddiyonSpacing.xs)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha),
                shape = RoundedCornerShape(AddiyonRadii.pill)
            )
    )
}

private data class ToneGlowVisuals(
    val border: BorderStroke,
    val glow: Shadow
)

@Composable
private fun toneGlowVisuals(isLoading: Boolean): ToneGlowVisuals {
    val colors = MaterialTheme.addiyonColors.aiToneGlow
    val phase = if (isLoading) {
        val transition = rememberInfiniteTransition(label = "toneGlow")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = AddiyonMotion.gentle * 2,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "toneGlowPhase"
        )
        animatedPhase
    } else {
        0f
    }
    val glowAlpha = if (isLoading) {
        val pulseRange = LOADING_TONE_GLOW_MAX_ALPHA - LOADING_TONE_GLOW_MIN_ALPHA
        if (phase <= 0.5f) {
            LOADING_TONE_GLOW_MIN_ALPHA + phase * pulseRange * 2f
        } else {
            LOADING_TONE_GLOW_MAX_ALPHA - (phase - 0.5f) * pulseRange * 2f
        }
    } else {
        SELECTED_TONE_GLOW_ALPHA
    }
    val borderBrush = if (isLoading) {
        Brush.sweepGradient(animatedToneGradient(colors, phase))
    } else {
        Brush.horizontalGradient(listOf(colors.start, colors.end))
    }
    val glowBrush = if (isLoading) {
        Brush.sweepGradient(
            animatedToneGradient(colors, phase).map { it.copy(alpha = glowAlpha) }
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                colors.start.copy(alpha = glowAlpha),
                colors.end.copy(alpha = glowAlpha)
            )
        )
    }
    return ToneGlowVisuals(
        border = BorderStroke(
            width = AddiyonBorders.selectedTone,
            brush = borderBrush
        ),
        glow = Shadow(
            radius = AddiyonElevation.overlay,
            brush = glowBrush
        )
    )
}

private fun animatedToneGradient(
    colors: AddiyonAiToneGlowColors,
    phase: Float
): List<Color> = List(12) { index ->
    val position = (index / 11f + phase) % 1f
    val blend = if (position <= 0.5f) position * 2f else (1f - position) * 2f
    lerp(colors.start, colors.end, blend)
}

private fun toneIcon(tab: AiToneTab): ImageVector = when (tab) {
    AiToneTab.Humanize -> Icons.Outlined.Face
    AiToneTab.Professional -> Icons.Outlined.WorkOutline
    AiToneTab.Casual -> Icons.Outlined.SentimentSatisfied
    AiToneTab.Formal -> Icons.Outlined.AccountBalance
    AiToneTab.Friendly -> Icons.Outlined.FavoriteBorder
    AiToneTab.FixGrammar -> Icons.Outlined.Spellcheck
    AiToneTab.Shorten -> Icons.AutoMirrored.Outlined.ShortText
    AiToneTab.Summarize -> Icons.AutoMirrored.Outlined.Subject
}

@Composable
private fun toneIconColor(tab: AiToneTab): Color {
    val colors = MaterialTheme.addiyonColors.aiToneIcons
    return when (tab) {
        AiToneTab.Humanize -> colors.humanize
        AiToneTab.Professional -> colors.professional
        AiToneTab.Casual -> colors.casual
        AiToneTab.Formal -> colors.formal
        AiToneTab.Friendly -> colors.friendly
        AiToneTab.FixGrammar -> colors.fixGrammar
        AiToneTab.Shorten -> colors.shorten
        AiToneTab.Summarize -> colors.summarize
    }
}

private fun toneLabel(tab: AiToneTab, strings: AiUiStrings): String = when (tab) {
    AiToneTab.Humanize -> strings.aiToneHumanize
    AiToneTab.Professional -> strings.aiToneProfessional
    AiToneTab.Casual -> strings.aiToneCasual
    AiToneTab.Formal -> strings.aiToneFormal
    AiToneTab.Friendly -> strings.aiToneFriendly
    AiToneTab.FixGrammar -> strings.aiToneFixGrammar
    AiToneTab.Shorten -> strings.aiToneShorten
    AiToneTab.Summarize -> strings.aiToneSummarize
}

private fun responseErrorMessage(error: AiError?, strings: AiUiStrings): String = when (error) {
    is AiError.NeedsAuth -> strings.aiErrorNeedsAuth
    is AiError.QuotaExceeded -> strings.aiErrorQuotaFormat.format(error.remaining.coerceAtLeast(0))
    is AiError.NoText -> strings.aiErrorNoText
    is AiError.PrivateField -> strings.aiErrorPrivateField
    is AiError.Offline -> strings.aiErrorOffline
    is AiError.Server -> error.message
    is AiError.RateLimited -> error.message ?: strings.aiErrorRateLimited
    is AiError.Unknown, null -> strings.aiErrorUnknown
}

private const val DISABLED_TONE_ALPHA = 0.38f
private const val SELECTED_TONE_GLOW_ALPHA = 0.32f
private const val LOADING_TONE_GLOW_MIN_ALPHA = 0.46f
private const val LOADING_TONE_GLOW_MAX_ALPHA = 0.68f
private const val BACK_GLASS_TOP_ALPHA = 0.96f
private const val BACK_GLASS_BOTTOM_ALPHA = 0.86f
private const val BACK_GLASS_SHADOW_ALPHA = 0.24f
private const val RESULT_SKELETON_CARD_COUNT = 3
private const val SKELETON_FULL_LINE_WIDTH = 1f
private const val SKELETON_MEDIUM_LINE_WIDTH = 0.82f
private const val SKELETON_SHORT_LINE_WIDTH = 0.58f
private const val SKELETON_PULSE_MIN_ALPHA = 0.45f
private const val SKELETON_PULSE_MAX_ALPHA = 0.82f
