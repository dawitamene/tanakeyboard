package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.features.appshell.KeyboardPageTopBar
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val AI_DASHBOARD_USAGE_TAG = "ai.dashboard.usage"
const val AI_DASHBOARD_ACCOUNT_TAG = "ai.dashboard.account"

@Composable
fun AiDashboardContent(
    strings: AiUiStrings,
    accountStore: AiAccountStore,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSwitchToAuth: () -> Unit,
    quotaLoader: (suspend (String?, String) -> Result<AiQuota>)? = null
) {
    val locale = Locale.ENGLISH
    var jwt by remember(accountStore) { mutableStateOf(accountStore.jwt()) }
    var quota by remember(accountStore) { mutableStateOf(accountStore.quota()) }
    val email = accountStore.email()
    val isLoggedIn = !jwt.isNullOrBlank()
    val limit = quota.limit
    val remaining = quota.remaining
    val remainingPercent = aiRemainingPercentage(quota)
    val progress = remainingPercent / 100f
    val resetDescription = formatAiUsageReset(
        nowMillis = System.currentTimeMillis(),
        timeZone = TimeZone.getDefault(),
        locale = locale,
        strings = strings
    )

    LaunchedEffect(jwt) {
        val loader = quotaLoader ?: return@LaunchedEffect
        val anonId = accountStore.anonymousId()
        val result = withContext(Dispatchers.IO) { loader(jwt, anonId) }
        result.onSuccess { freshQuota ->
            accountStore.saveQuota(freshQuota)
            quota = freshQuota
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = strings.aiAccountTitle,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        strings.aiWorkspaceTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isLoggedIn) {
                            strings.aiWorkspaceConnectedDescription
                        } else {
                            strings.aiWorkspaceDisconnectedDescription
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AI_DASHBOARD_USAGE_TAG)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    strings.aiUsageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$remainingPercent%",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        strings.aiUsageRemaining,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    String.format(
                        locale,
                        strings.aiUsageTokensRemainingFormat,
                        remaining,
                        limit
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    resetDescription,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AI_DASHBOARD_ACCOUNT_TAG)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    strings.aiAccountSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isLoggedIn) email ?: strings.aiAccountFallback else strings.aiAnonymousAccess,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isLoggedIn) {
                    Text(
                        text = strings.aiAccountDisconnectedDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoggedIn) {
                OutlinedButton(
                    onClick = {
                        accountStore.setPhraseCompletionsEnabled(false)
                        accountStore.clearJwt()
                        jwt = null
                        onLogout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AddiyonSizes.minimumTouchTarget),
                    shape = RoundedCornerShape(AddiyonRadii.small)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.aiSignOut)
                }
            } else {
                Button(
                    onClick = onSwitchToAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.aiSignInAction)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

}
