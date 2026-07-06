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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    modifier: Modifier = Modifier,
    // When non-null (screen opened from the keyboard), a back button is shown
    // in the header to return to the keyboard.
    onExit: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
            putExtra(Intent.EXTRA_TEXT, "Type Amharic easily with Tana Keyboard: $link")
        }
        context.startActivity(Intent.createChooser(intent, "Share Tana Keyboard"))
    }

    fun openEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Tana Keyboard feedback")
        }
        runCatching { context.startActivity(intent) }
    }

    fun openTelegram() {
        // tg:// deep-links straight into the app; https fallback for when
        // Telegram isn't installed. Username is a placeholder for now.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$TELEGRAM_USERNAME"))
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$TELEGRAM_USERNAME"))
            )
        }
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
            Header(onExit = onExit)
            Spacer(Modifier.height(8.dp))

            GroupCard {
                SettingsItem(Icons.Default.Palette, "Themes", onClick = onOpenThemes)
                SettingsItem(Icons.Default.MenuBook, "Typing Guide", onClick = onOpenManual)
                SettingsItem(Icons.Default.VolumeUp, "Sound and Vibration", onClick = onOpenSoundVibration)
                SettingsItem(Icons.Default.Keyboard, "Test Keyboard", onClick = onOpenTestKeyboard)
            }
            GroupCard {
                SettingsItem(Icons.Default.Share, "Share Tana Keyboard") { shareApp() }
                SettingsItem(Icons.Default.StarRate, "Rate Tana Keyboard") { rateApp() }
            }
            GroupCard {
                SettingsItem(Icons.Default.Feedback, "Feedback") { showFeedback = true }
                SettingsItem(Icons.Default.Info, "About", onClick = onOpenAbout)
            }
        }
    }

    if (showFeedback) {
        ModalBottomSheet(
            onDismissRequest = { showFeedback = false },
            sheetState = sheetState
        ) {
            Text(
                text = "Send feedback",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            SettingsItem(Icons.Default.Email, "Email") {
                showFeedback = false
                openEmail()
            }
            SettingsItem(Icons.AutoMirrored.Filled.Send, "Telegram") {
                showFeedback = false
                openTelegram()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private const val FEEDBACK_EMAIL = "tanakeyboard@addiyon.com"

// TODO: replace with the real Telegram username once provided.
private const val TELEGRAM_USERNAME = "tanakeyboard"

@Composable
private fun Header(onExit: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onExit != null) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to keyboard"
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        // Brand logo (the pre-rounded Tana mark).
        Image(
            painter = painterResource(R.drawable.ic_tana_icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Tana Keyboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
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
