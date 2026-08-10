package com.addiyon.keyboard.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.addiyon.keyboard.ai.AiError
import com.addiyon.keyboard.ai.AiStrength
import com.addiyon.keyboard.ai.AiToneTab
import com.addiyon.keyboard.ai.AiUiState
import com.addiyon.keyboard.ui.SuggestionChevronLeftButton
import com.addiyon.keyboard.ui.design.AddiyonElevation
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.design.addiyonColors
import com.addiyon.keyboard.ui.i18n.AppStrings
import com.addiyon.keyboard.ui.i18n.LocalAppStrings

fun aiPanelVariantTag(strength: AiStrength): String = "ai.panel.variant.${strength.name.lowercase()}"
fun aiPanelCopyTag(strength: AiStrength): String = "ai.panel.copy.${strength.name.lowercase()}"
fun aiPanelReplaceTag(strength: AiStrength): String = "ai.panel.replace.${strength.name.lowercase()}"
const val AI_PANEL_SKELETON_TAG = "ai.panel.skeleton"
const val AI_PANEL_EMPTY_ACTION_TAG = "ai.panel.empty.action"
private const val AI_RESULT_VARIANT_COUNT = 3

@Composable
fun AiPanel(
    state: AiUiState,
    onDismiss: () -> Unit,
    onTabSelected: (AiToneTab) -> Unit,
    onCopyVariant: (AiStrength) -> Unit,
    onReplaceVariant: (AiStrength) -> Unit,
    onSendLink: () -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AiPanelToolbar(
            state = state,
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
                            onSendLink = onSendLink,
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
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBubble(
            icon = Icons.Filled.TextFields,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            shape = RoundedCornerShape(AddiyonRadii.medium)
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AiPanelToolbar(
    state: AiUiState,
    onDismiss: () -> Unit,
    onTabSelected: (AiToneTab) -> Unit,
    strings: AppStrings
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = AddiyonSpacing.xs,
                bottom = AddiyonSpacing.sm
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AddiyonSizes.minimumTouchTarget)
                .padding(
                    start = AddiyonSpacing.xxs,
                    end = AddiyonSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuggestionChevronLeftButton(
                onClick = onDismiss,
                contentDescription = strings.back
            )
            if (!state.isPrivateField && !state.needsAuth) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
                ) {
                    AiToneTab.DefaultTabs.forEach { tab ->
                        val selected = state.selectedTab == tab
                        val containerColor = if (selected) {
                            MaterialTheme.addiyonColors.brandPrimary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        val contentColor = if (selected) {
                            MaterialTheme.addiyonColors.onBrandPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            selected = selected,
                            onClick = { onTabSelected(tab) },
                            shape = RoundedCornerShape(AddiyonRadii.pill),
                            color = containerColor,
                            contentColor = contentColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(AddiyonSizes.compact)
                                    .padding(horizontal = AddiyonSpacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
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
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AiPanelAuthCard(
    state: AiUiState,
    onSendLink: () -> Unit,
    strings: AppStrings,
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
                onClick = onSendLink,
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
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(AddiyonSizes.iconLarge)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(AddiyonSizes.iconSmall)
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
                color = MaterialTheme.addiyonColors.aiResultSurface,
                contentColor = MaterialTheme.addiyonColors.onAiResultSurface
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
                            MaterialTheme.addiyonColors.aiResultSurface.copy(alpha = 0f),
                            MaterialTheme.addiyonColors.aiResultSurface.copy(alpha = 0.85f),
                            MaterialTheme.addiyonColors.aiResultSurface.copy(alpha = 0f)
                        ),
                        start = Offset(start, 0f),
                        end = Offset(start + shimmerWidth, constraints.maxHeight.toFloat())
                    )
                )
        )
    }
}

@Composable
private fun ErrorCard(error: AiError, quotaRemaining: Int, strings: AppStrings) {
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
    strings: AppStrings,
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
            color = MaterialTheme.addiyonColors.aiResultSurface,
            contentColor = MaterialTheme.addiyonColors.onAiResultSurface
        ) {
            Column(Modifier.padding(AddiyonSpacing.sm)) {
                SelectionContainer {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.addiyonColors.onAiResultSurface
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

private fun toneLabel(tab: AiToneTab, strings: AppStrings): String = when (tab) {
    AiToneTab.Humanize -> strings.aiToneHumanize
    AiToneTab.Professional -> strings.aiToneProfessional
    AiToneTab.Casual -> strings.aiToneCasual
    AiToneTab.Formal -> strings.aiToneFormal
    AiToneTab.Friendly -> strings.aiToneFriendly
    AiToneTab.FixGrammar -> strings.aiToneFixGrammar
    AiToneTab.Shorten -> strings.aiToneShorten
    AiToneTab.Summarize -> strings.aiToneSummarize
}

private fun variantErrorMessage(error: AiError, strings: AppStrings): String = when (error) {
    is AiError.Offline -> strings.aiVariantErrorOffline
    is AiError.Server -> error.message
    is AiError.RateLimited -> error.message ?: strings.aiVariantErrorTryAgain
    is AiError.QuotaExceeded -> strings.aiVariantErrorQuota
    is AiError.NeedsAuth -> strings.aiVariantErrorNeedsAuth
    is AiError.NoText -> strings.aiVariantErrorNoText
    is AiError.PrivateField -> strings.aiVariantErrorPrivateField
    is AiError.Unknown -> strings.aiVariantErrorUnavailable
}
