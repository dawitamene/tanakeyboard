package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.addiyon.keyboard.ai.AiCompletionUiState
import com.addiyon.keyboard.ui.design.AddiyonBorders
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing

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
            AiCompletionUiState.Debouncing -> CompletionStatus(
                label = strings.aiPhraseCompletionIdle,
                loading = false
            )
            AiCompletionUiState.Loading -> CompletionStatus(
                label = strings.aiPhraseCompletionLoading,
                loading = true
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
private fun CompletionStatus(label: String, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = AddiyonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(AddiyonSizes.iconSmall),
                strokeWidth = AddiyonBorders.selectedTone,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AddiyonSizes.iconSmall)
            )
        }
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
