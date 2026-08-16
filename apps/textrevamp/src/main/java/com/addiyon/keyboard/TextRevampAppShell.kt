package com.addiyon.keyboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addiyon.keyboard.ai.AiPreferences
import com.addiyon.keyboard.features.appshell.KeyboardAppShellCustomization
import com.addiyon.keyboard.features.appshell.KeyboardGuideSection
import com.addiyon.keyboard.features.appshell.KeyboardSettingsGroup
import com.addiyon.keyboard.features.appshell.KeyboardShellMenuEntry
import com.addiyon.keyboard.features.appshell.KeyboardShellMenuTarget
import com.addiyon.keyboard.features.appshell.KeyboardTourPage
import com.addiyon.keyboard.features.appshell.packKeyboardAppShellConfig
import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.ui.design.AddiyonSpacing

@Composable
internal fun textRevampAppShellConfig(product: KeyboardProduct) =
    packKeyboardAppShellConfig(product, textRevampShellCustomization())

@Composable
private fun textRevampShellCustomization(): KeyboardAppShellCustomization {
    val context = LocalContext.current
    val strings = LocalTextRevampStrings.current
    val isLoggedIn = !AiPreferences(context).jwt().isNullOrBlank()
    val usageEntry = KeyboardShellMenuEntry(
        icon = Icons.Default.AutoAwesome,
        label = strings.aiUsage,
        group = KeyboardSettingsGroup.PRIMARY,
        target = KeyboardShellMenuTarget.Action {
            context.openAiAccount(
                if (isLoggedIn) AiAccountActivity.MODE_DASHBOARD else AiAccountActivity.MODE_AUTH
            )
        }
    )
    val customInstructionsEntry = KeyboardShellMenuEntry(
        icon = Icons.Outlined.Edit,
        label = strings.aiCustomInstructions,
        group = KeyboardSettingsGroup.PRIMARY,
        target = KeyboardShellMenuTarget.Action {
            context.openAiAccount(AiAccountActivity.MODE_CUSTOM_TONE)
        }
    )
    return KeyboardAppShellCustomization(
        privacyPolicyUrl = TEXTREVAMP_PRIVACY_POLICY_URL,
        featurePrimaryEntries = listOf(usageEntry, customInstructionsEntry),
        featureGuideSections = listOf(
            KeyboardGuideSection(strings.aiRephraseTitle, strings.aiRephraseSubtitle)
        ),
        featureTourPages = listOf(
            KeyboardTourPage(
                icon = Icons.Default.AutoAwesome,
                title = strings.aiRephraseTitle,
                description = strings.aiTourDescription,
                example = strings.aiTourExample
            )
        ),
        headerActions = {
            if (isLoggedIn) {
                Spacer(Modifier.width(AddiyonSpacing.sm))
                IconButton(
                    onClick = { context.openAiAccount(AiAccountActivity.MODE_DASHBOARD) }
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = strings.aiAccountTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

private const val TEXTREVAMP_PRIVACY_POLICY_URL =
    "https://keyboard.addiyon.com/textrevamp-privacy.html"
