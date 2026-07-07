package com.addiyon.tanakeyboard.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.addiyon.tanakeyboard.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.KeyboardStatusSnapshot
import com.addiyon.tanakeyboard.ui.feedback.FeedbackOptions
import com.addiyon.tanakeyboard.ui.feedback.openFeedbackTelegram
import com.addiyon.tanakeyboard.ui.feedback.sendFeedbackEmail
import com.addiyon.tanakeyboard.ui.i18n.LanguageToggle
import com.addiyon.tanakeyboard.ui.i18n.LocalAppStrings
import com.addiyon.tanakeyboard.ui.theme.PlaypenSansBrand

/**
 * App home. Header with the app name + placeholder logo and grouped lists of
 * entry points (each with a leading icon). Most rows are placeholders for now;
 * "Guide" opens the transliteration manual, "Test Keyboard" opens a scratch
 * input field. (The keyboard FAB is commented out for now.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    status: KeyboardStatusSnapshot,
    onOpenManual: () -> Unit,
    onOpenSoundVibration: () -> Unit,
    onOpenTestKeyboard: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenThemes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var showFeedback by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // FAB removed for now -- kept commented in case we bring it back.
    // // There's no editable field on this screen, so to pop the soft keyboard
    // // we focus a hidden BasicTextField (rendered off-view below) and ask the
    // // IME to show -- an IME can only appear against a focused editor.
    // val keyboardController = LocalSoftwareKeyboardController.current
    // val hiddenFocus = remember { FocusRequester() }
    //
    // fun openKeyboard() {
    //     hiddenFocus.requestFocus()
    //     keyboardController?.show()
    // }

    fun rateApp() {
        val pkg = context.packageName
        // market:// opens the Play Store app directly; web URL is the fallback.
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        if (context.packageManager.resolveActivity(market, 0) != null) {
            context.startActivity(market)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            )
        }
    }

    fun shareApp() {
        val link = "https://play.google.com/store/apps/details?id=${context.packageName}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, strings.shareTextFormat.format(link))
        }
        context.startActivity(Intent.createChooser(intent, strings.shareChooserTitle))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // floatingActionButton = {
        //     FloatingActionButton(
        //         onClick = { openKeyboard() },
        //         shape = CircleShape
        //     ) {
        //         Icon(Icons.Default.Keyboard, contentDescription = "Open keyboard")
        //     }
        // }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tana_icon),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Tana Keyboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PlaypenSansBrand,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.weight(1f))
                LanguageToggle()
            }

            GroupCard {
                SettingsItem(Icons.Default.Palette, strings.themes, onClick = onOpenThemes)
                SettingsItem(Icons.Default.MenuBook, strings.typingGuide, onClick = onOpenManual)
                SettingsItem(Icons.Default.Tune, strings.preferences, onClick = onOpenSoundVibration)
                SettingsItem(Icons.Default.Keyboard, strings.testKeyboard, onClick = onOpenTestKeyboard)
            }
            GroupCard {
                SettingsItem(Icons.Default.Share, strings.shareApp) { shareApp() }
                SettingsItem(Icons.Default.StarRate, strings.rateApp) { rateApp() }
            }
            GroupCard {
                SettingsItem(Icons.Default.Feedback, strings.feedback) { showFeedback = true }
                SettingsItem(Icons.Default.Info, strings.about, onClick = onOpenAbout)
            }
        }
    }

    if (showFeedback) {
        ModalBottomSheet(
            onDismissRequest = { showFeedback = false },
            sheetState = sheetState
        ) {
            Text(
                text = strings.sendFeedback,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            // Telegram first (with the real Telegram logo), then Email.
            FeedbackOptions(
                onTelegram = {
                    showFeedback = false
                    openFeedbackTelegram(context)
                },
                onEmail = {
                    showFeedback = false
                    sendFeedbackEmail(context, strings.feedbackEmailSubject)
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
