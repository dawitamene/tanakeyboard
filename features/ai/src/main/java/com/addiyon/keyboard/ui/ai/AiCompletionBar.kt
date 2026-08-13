package com.addiyon.keyboard.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.addiyon.keyboard.ai.AiCompletionUiState
import com.addiyon.keyboard.ui.design.AddiyonMotion
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import kotlin.math.abs

const val AI_COMPLETION_BAR_TAG = "ai.completion.bar"
const val AI_COMPLETION_INSERT_TAG = "ai.completion.insert"
const val AI_COMPLETION_DISMISS_TAG = "ai.completion.dismiss"

@Composable
fun AiCompletionBar(
    state: AiCompletionUiState,
    strings: AiUiStrings,
    onInsert: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is AiCompletionUiState.Hidden) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddiyonSizes.keyboardAction)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(AI_COMPLETION_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            AiCompletionUiState.Hidden -> Unit
            AiCompletionUiState.Idle,
            AiCompletionUiState.Debouncing -> CompletionIdleStatus(
                label = strings.aiPhraseCompletionIdle
            )
            AiCompletionUiState.Loading -> CompletionLoadingDots(
                label = strings.aiPhraseCompletionLoading
            )
            is AiCompletionUiState.Ready -> {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            onClickLabel = strings.aiPhraseCompletionInsert,
                            role = Role.Button,
                            onClick = onInsert
                        )
                        .padding(start = AddiyonSpacing.sm)
                        .testTag(AI_COMPLETION_INSERT_TAG),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AddiyonSizes.iconSmall)
                    )
                    Spacer(Modifier.width(AddiyonSpacing.xs))
                    Text(
                        text = state.completion.trimStart(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(AddiyonSizes.keyboardAction)
                        .clickable(
                            onClickLabel = strings.aiPhraseCompletionDismiss,
                            role = Role.Button,
                            onClick = onDismiss
                        )
                        .testTag(AI_COMPLETION_DISMISS_TAG),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.aiPhraseCompletionDismiss,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AddiyonSizes.iconMedium)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionIdleStatus(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = AddiyonSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AddiyonSizes.iconSmall)
        )
        Spacer(Modifier.width(AddiyonSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompletionLoadingDots(label: String) {
    val transition = rememberInfiniteTransition(label = "phraseCompletionLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AddiyonMotion.gentle * 2,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phraseCompletionLoadingPhase"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val distance = abs(phase - index)
            val wrappedDistance = minOf(distance, 3f - distance)
            val dotAlpha = 0.35f + 0.65f * (1f - wrappedDistance.coerceIn(0f, 1f))
            Box(
                modifier = Modifier
                    .size(AddiyonSpacing.xxs)
                    .alpha(dotAlpha)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            if (index < 2) Spacer(Modifier.width(AddiyonSpacing.xxs))
        }
    }
}
