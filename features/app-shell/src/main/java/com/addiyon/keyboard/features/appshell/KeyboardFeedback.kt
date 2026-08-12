package com.addiyon.keyboard.features.appshell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.design.AddiyonContentSection

const val FEEDBACK_EMAIL = "keyboard@addiyon.com"
const val TELEGRAM_USERNAME = "addiyonsupport"

fun feedbackEmailUri(): String = "mailto:$FEEDBACK_EMAIL"

fun feedbackTelegramDeepLink(): String =
    "tg://resolve?domain=$TELEGRAM_USERNAME"

fun feedbackTelegramWebLink(): String =
    "https://t.me/$TELEGRAM_USERNAME"

object FeedbackTestTags {
    const val TELEGRAM = "feedback.telegram"
    const val EMAIL = "feedback.email"
}

data class KeyboardFeedbackCopy(
    val title: String,
    val back: String,
    val telegram: String,
    val email: String,
    val emailSubject: String
)

fun sendFeedbackEmail(context: Context, subject: String, extraFlags: Int = 0) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(feedbackEmailUri())).apply {
        addFlags(extraFlags)
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    KeyboardExternalActions.startDirect(
        context,
        intent,
        context.getString(R.string.email_unavailable)
    )
}

fun openFeedbackTelegram(context: Context, extraFlags: Int = 0) {
    val deep = Intent(Intent.ACTION_VIEW, Uri.parse(feedbackTelegramDeepLink()))
        .addFlags(extraFlags)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(feedbackTelegramWebLink()))
        .addFlags(extraFlags)
    if (!KeyboardExternalActions.tryStartDirect(context, deep)) {
        KeyboardExternalActions.startDirect(
            context,
            web,
            context.getString(R.string.browser_unavailable)
        )
    }
}

@Composable
fun KeyboardFeedbackOptions(
    telegramLabel: String,
    emailLabel: String,
    onTelegram: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    AddiyonContentSection(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FeedbackTestTags.TELEGRAM)
                .clickable(onClick = onTelegram)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_telegram_app_shell),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = telegramLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FeedbackTestTags.EMAIL)
                .clickable(onClick = onEmail)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = emailLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun KeyboardFeedbackScreen(
    copy: KeyboardFeedbackCopy,
    onBack: () -> Unit,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = copy.title,
                onBack = onBack,
                backContentDescription = copy.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp)
        ) {
            KeyboardFeedbackOptions(
                telegramLabel = copy.telegram,
                emailLabel = copy.email,
                onTelegram = {
                    openFeedbackTelegram(context)
                    onPicked()
                },
                onEmail = {
                    sendFeedbackEmail(context, copy.emailSubject)
                    onPicked()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}
