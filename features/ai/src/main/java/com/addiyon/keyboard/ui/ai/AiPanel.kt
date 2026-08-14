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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.addiyon.keyboard.ai.AiError
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ui.SuggestionChevronLeftButton
import com.addiyon.keyboard.ui.design.AddiyonAiToneGlowColors
import com.addiyon.keyboard.ui.design.AddiyonBorders
import com.addiyon.keyboard.ui.design.AddiyonElevation
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.design.addiyonColors

fun aiPanelVariantTag(strength: AiStrength): String = "ai.panel.variant.${strength.name.lowercase()}"
fun aiPanelCopyTag(strength: AiStrength): String = "ai.panel.copy.${strength.name.lowercase()}"
fun aiPanelReplaceTag(strength: AiStrength): String = "ai.panel.replace.${strength.name.lowercase()}"
fun aiPanelToneIconTag(tab: AiToneTab): String = "ai.panel.tone.icon.${tab.name.lowercase()}"
const val AI_TONE_ACTION_ROW_TAG = "ai.tone.action.row"
const val AI_PANEL_BACK_ACTION_TAG = "ai.panel.back.action"
const val AI_PANEL_SKELETON_TAG = "ai.panel.skeleton"
const val AI_PANEL_EMPTY_ACTION_TAG = "ai.panel.empty.action"
const val AI_PANEL_SELECT_TONE_ICON_TAG = "ai.panel.select.tone.icon"
private const val AI_RESULT_VARIANT_COUNT = 3

@Composable
fun AiPanel(
    state: AiUiState,
    tonesEnabled: Boolean = state.hasInput,
    strings: AiUiStrings,
    onDismiss: () -> Unit,
    onTabSelected: (AiToneTab) -> Unit,
    onCopyVariant: (AiStrength) -> Unit,
    onReplaceVariant: (AiStrength) -> Unit,
    onOpenDashboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AiPanelToolbar(
            state = state,
            tonesEnabled = tonesEnabled,
            onDismiss = onDismiss,
            onTabSelected = onTabSelected,
            strings = strings
        )

        val hasResult = state.variantResults.isNotEmpty() || state.result != null
        val contentModifier = Modifier
            .weight(1f, fill = true)
            .padding(horizontal = AddiyonSpacing.sm)
        if (!state.isPrivateField && !state.needsAuth && !state.hasInput) {
            AiPanelEmptyState(
                onContinueTyping = onDismiss,
                strings = strings,
                modifier = contentModifier
            )
        } else if (!state.isPrivateField && !state.needsAuth && state.selectedTab == null) {
            AiPanelSelectToneState(
                message = strings.aiSelectToneMessage,
                modifier = contentModifier
            )
        } else {
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
            ) {
                when {
                    state.isPrivateField -> {
                        AiPanelNotice(
                            icon = Icons.Filled.Lock,
                            title = strings.aiPrivateTitle,
                            message = strings.aiPrivateMessage,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            iconContainerColor = MaterialTheme.colorScheme.onErrorContainer,
                            iconContentColor = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(top = AddiyonSpacing.sm)
                        )
                    }

                    state.needsAuth -> {
                        AiPanelAuthCard(
                            state = state,
                            onOpenDashboard = onOpenDashboard,
                            strings = strings,
                            modifier = Modifier.padding(top = AddiyonSpacing.sm)
                        )
                    }

                    else -> {
                        if (state.isLoading || state.isQuotaLoading) {
                            SkeletonResults()
                        }

                        state.error?.let { error ->
                            ErrorCard(
                                error = error,
                                quotaRemaining = state.quota.remaining,
                                strings = strings
                            )
                        }

                        if (hasResult || state.variantErrors.isNotEmpty()) {
                            ResultContent(
                                state = state,
                                strings = strings,
                                onCopyVariant = onCopyVariant,
                                onReplaceVariant = onReplaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AddiyonSpacing.xs))
            }
        }
    }
}

@Composable
private fun AiPanelEmptyState(
    onContinueTyping: () -> Unit,
    strings: AiUiStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBubble(
            icon = Icons.Filled.TextFields,
            containerColor = MaterialTheme.addiyonColors.brandPrimary,
            contentColor = MaterialTheme.addiyonColors.onBrandPrimary,
            containerSize = AddiyonSizes.minimumTouchTarget,
            iconSize = AddiyonSizes.iconLarge
        )
        Text(
            text = strings.aiEmptyTitle,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = AddiyonSpacing.sm),
            textAlign = TextAlign.Center
        )
        Text(
            text = strings.aiEmptyMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AddiyonSpacing.xxs),
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = onContinueTyping,
            modifier = Modifier
                .padding(top = AddiyonSpacing.md)
                .height(AddiyonSizes.keyboardAction)
                .testTag(AI_PANEL_EMPTY_ACTION_TAG),
            shape = RoundedCornerShape(AddiyonRadii.pill),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.addiyonColors.brandPrimary
            ),
            border = BorderStroke(
                width = ButtonDefaults.outlinedButtonBorder().width,
                color = MaterialTheme.addiyonColors.brandPrimary
            )
        ) {
            Text(text = strings.aiEmptyAction)
        }
    }
}

@Composable
private fun AiPanelSelectToneState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBubble(
            icon = Icons.Outlined.Palette,
            containerColor = MaterialTheme.addiyonColors.brandPrimary,
            contentColor = MaterialTheme.addiyonColors.onBrandPrimary,
            containerSize = AddiyonSizes.minimumTouchTarget,
            iconSize = AddiyonSizes.iconLarge,
            modifier = Modifier.testTag(AI_PANEL_SELECT_TONE_ICON_TAG)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AddiyonSpacing.sm),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AiPanelToolbar(
    state: AiUiState,
    tonesEnabled: Boolean,
    onDismiss: () -> Unit,
    onTabSelected: (AiToneTab) -> Unit,
    strings: AiUiStrings
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AddiyonSpacing.xxs,
                end = AddiyonSpacing.sm,
                top = AddiyonSpacing.xs,
                bottom = AddiyonSpacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SuggestionChevronLeftButton(
            onClick = onDismiss,
            contentDescription = strings.back,
            testTag = AI_PANEL_BACK_ACTION_TAG,
            buttonSize = AddiyonSizes.keyboardAction,
            containerSize = AddiyonSizes.keyboardAction,
            iconSize = AddiyonSizes.iconLarge,
            iconTint = MaterialTheme.colorScheme.onBackground
        )
        if (!state.isPrivateField && !state.needsAuth) {
            AiToneRow(
                selectedTab = state.selectedTab,
                isLoading = state.isLoading,
                enabled = tonesEnabled,
                onTabSelected = onTabSelected,
                strings = strings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AiToneRow(
    selectedTab: AiToneTab?,
    isLoading: Boolean,
    enabled: Boolean,
    onTabSelected: (AiToneTab) -> Unit,
    strings: AiUiStrings,
    modifier: Modifier = Modifier
) {
    val toneShape = RoundedCornerShape(AddiyonRadii.pill)
    val toneGlowVisuals = toneGlowVisuals(isLoading)
    Row(
        modifier = modifier
            .height(AddiyonSizes.keyboardAction)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AddiyonSpacing.xs)
            .testTag(AI_TONE_ACTION_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiToneTab.DefaultTabs.forEach { tab ->
            val selected = selectedTab == tab
            val contentAlpha = if (enabled) 1f else DISABLED_TONE_ALPHA
            val iconColor = toneIconColor(tab).copy(alpha = contentAlpha)
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
                onClick = { onTabSelected(tab) },
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
                        imageVector = toneIcon(tab),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .size(AddiyonSizes.iconSmall)
                            .testTag(aiPanelToneIconTag(tab))
                    )
                    Text(
                        text = toneLabel(tab, strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
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
        if (phase <= 0.5f) 0.64f + phase * 0.56f else 1.2f - phase * 0.56f
    } else {
        0.48f
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

private const val DISABLED_TONE_ALPHA = 0.38f

private fun animatedToneGradient(
    colors: AddiyonAiToneGlowColors,
    phase: Float
): List<Color> = List(12) { index ->
    val position = (index / 11f + phase) % 1f
    val blend = if (position <= 0.5f) position * 2f else (1f - position) * 2f
    lerp(colors.start, colors.end, blend)
}

@Composable
private fun AiPanelAuthCard(
    state: AiUiState,
    onOpenDashboard: () -> Unit,
    strings: AiUiStrings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AddiyonRadii.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = AddiyonElevation.low)
    ) {
        Column(Modifier.padding(AddiyonSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(AddiyonSizes.iconLarge),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(AddiyonSizes.iconSmall)
                        )
                    }
                }
                Spacer(Modifier.width(AddiyonSpacing.xs))
                Text(
                    text = strings.aiAuthTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = strings.aiAuthMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AddiyonSpacing.xs)
            )
            state.authMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AddiyonSpacing.xs)
                )
            }
            Button(
                onClick = onOpenDashboard,
                enabled = !state.authSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AddiyonSpacing.sm)
                    .height(AddiyonSizes.keyboardAction),
                shape = RoundedCornerShape(AddiyonRadii.medium)
            ) {
                if (state.authSending) {
                    CircularProgressIndicator(modifier = Modifier.size(AddiyonSizes.iconSmall))
                    Spacer(Modifier.width(AddiyonSpacing.xs))
                    Text(text = strings.aiAuthSending, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(text = strings.aiAuthAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AiPanelNotice(
    icon: ImageVector,
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color,
    iconContainerColor: Color,
    iconContentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AddiyonRadii.medium),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = AddiyonElevation.none)
    ) {
        Row(
            modifier = Modifier.padding(AddiyonSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            IconBubble(
                icon = icon,
                containerColor = iconContainerColor,
                contentColor = iconContentColor
            )
            Spacer(Modifier.width(AddiyonSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = AddiyonSpacing.xxs)
                )
            }
        }
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    containerSize: Dp = AddiyonSizes.iconLarge,
    iconSize: Dp = AddiyonSizes.iconSmall,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SkeletonResults() {
    val transition = rememberInfiniteTransition(label = "aiSkeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AddiyonMotion.gentle * AI_RESULT_VARIANT_COUNT,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "aiSkeletonShimmer"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AI_PANEL_SKELETON_TAG),
        verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
    ) {
        repeat(AI_RESULT_VARIANT_COUNT) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AddiyonRadii.medium),
                color = MaterialTheme.addiyonColors.resultSurface,
                contentColor = MaterialTheme.addiyonColors.onResultSurface
            ) {
                Column(
                    modifier = Modifier.padding(AddiyonSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
                ) {
                    SkeletonLine(Modifier.fillMaxWidth(), phase)
                    SkeletonLine(Modifier.fillMaxWidth(0.82f), phase)
                    SkeletonLine(Modifier.fillMaxWidth(0.58f), phase)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(modifier: Modifier, phase: Float) {
    BoxWithConstraints(
        modifier = modifier
            .height(AddiyonSpacing.xs)
            .clip(RoundedCornerShape(AddiyonRadii.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = constraints.maxWidth.toFloat()
        val shimmerWidth = width * 0.45f
        val start = (width + shimmerWidth) * phase - shimmerWidth
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.addiyonColors.resultSurface.copy(alpha = 0f),
                            MaterialTheme.addiyonColors.resultSurface.copy(alpha = 0.85f),
                            MaterialTheme.addiyonColors.resultSurface.copy(alpha = 0f)
                        ),
                        start = Offset(start, 0f),
                        end = Offset(start + shimmerWidth, constraints.maxHeight.toFloat())
                    )
                )
        )
    }
}

@Composable
private fun ErrorCard(error: AiError, quotaRemaining: Int, strings: AiUiStrings) {
    val message = when (error) {
        is AiError.NeedsAuth -> strings.aiErrorNeedsAuth
        is AiError.QuotaExceeded -> strings.aiErrorQuotaFormat.format(quotaRemaining.coerceAtLeast(0))
        is AiError.NoText -> strings.aiErrorNoText
        is AiError.PrivateField -> strings.aiErrorPrivateField
        is AiError.Offline -> strings.aiErrorOffline
        is AiError.Server -> error.message
        is AiError.RateLimited -> error.message ?: strings.aiErrorRateLimited
        is AiError.Unknown -> strings.aiErrorUnknown
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AddiyonRadii.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = AddiyonElevation.none)
    ) {
        Row(
            modifier = Modifier.padding(AddiyonSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = strings.aiErrorDescription,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(AddiyonSizes.iconSmall)
            )
            Spacer(Modifier.width(AddiyonSpacing.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ResultContent(
    state: AiUiState,
    strings: AiUiStrings,
    onCopyVariant: (AiStrength) -> Unit,
    onReplaceVariant: (AiStrength) -> Unit
) {
    val variants = if (state.variantResults.isNotEmpty()) {
        state.variantResults
    } else {
        state.result?.let { mapOf(AiStrength.Balanced to it) }.orEmpty()
    }
    val orderedVariants = variants.entries.sortedBy { it.key.ordinal }
    orderedVariants.forEach { (strength, result) ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(aiPanelVariantTag(strength)),
            shape = RoundedCornerShape(AddiyonRadii.medium),
            color = MaterialTheme.addiyonColors.resultSurface,
            contentColor = MaterialTheme.addiyonColors.onResultSurface
        ) {
            Column(Modifier.padding(AddiyonSpacing.sm)) {
                SelectionContainer {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.addiyonColors.onResultSurface
                    )
                }
                if (result.truncated) {
                    Text(
                        text = strings.aiTruncated,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = AddiyonSpacing.xs)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { onCopyVariant(strength) },
                        modifier = Modifier
                            .size(AddiyonSizes.compact)
                            .testTag(aiPanelCopyTag(strength))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = strings.aiCopyDescription,
                            tint = MaterialTheme.addiyonColors.brandPrimary,
                            modifier = Modifier.size(AddiyonSizes.iconSmall)
                        )
                    }
                    IconButton(
                        onClick = { onReplaceVariant(strength) },
                        modifier = Modifier
                            .size(AddiyonSizes.compact)
                            .testTag(aiPanelReplaceTag(strength))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = strings.aiReplaceDescription,
                            tint = MaterialTheme.addiyonColors.brandPrimary,
                            modifier = Modifier.size(AddiyonSizes.iconSmall)
                        )
                    }
                }
            }
        }
    }

    state.variantErrors.entries.sortedBy { it.key.ordinal }.forEach { (strength, error) ->
        if (strength !in variants) {
            Text(
                text = variantErrorMessage(error, strings),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AddiyonSpacing.xxs)
            )
        }
    }
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

private fun variantErrorMessage(error: AiError, strings: AiUiStrings): String = when (error) {
    is AiError.Offline -> strings.aiVariantErrorOffline
    is AiError.Server -> error.message
    is AiError.RateLimited -> error.message ?: strings.aiVariantErrorTryAgain
    is AiError.QuotaExceeded -> strings.aiVariantErrorQuota
    is AiError.NeedsAuth -> strings.aiVariantErrorNeedsAuth
    is AiError.NoText -> strings.aiVariantErrorNoText
    is AiError.PrivateField -> strings.aiVariantErrorPrivateField
    is AiError.Unknown -> strings.aiVariantErrorUnavailable
}
