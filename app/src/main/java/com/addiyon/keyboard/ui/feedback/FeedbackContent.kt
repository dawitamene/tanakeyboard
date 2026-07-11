package com.addiyon.keyboard.ui.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.R
import com.addiyon.keyboard.ui.i18n.LocalAppStrings

/** Feedback destinations, shared by every place that offers "Send feedback". */
const val FEEDBACK_EMAIL = "keyboard@addiyon.com"
const val TELEGRAM_USERNAME = "addiyonkeyboard"

object FeedbackTestTags {
    const val TELEGRAM = "feedback.telegram"
    const val EMAIL = "feedback.email"
}

/**
 * Launches the mail composer to the feedback address. [extraFlags] lets a
 * Service caller add FLAG_ACTIVITY_NEW_TASK; from an Activity it's 0.
 */
fun sendFeedbackEmail(context: Context, subject: String, extraFlags: Int = 0) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        addFlags(extraFlags)
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Opens the feedback Telegram: the `tg://` deep link into the installed app,
 * falling back to the `t.me` web link when Telegram isn't installed.
 */
fun openFeedbackTelegram(context: Context, extraFlags: Int = 0) {
    val deep = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$TELEGRAM_USERNAME"))
        .addFlags(extraFlags)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$TELEGRAM_USERNAME"))
        .addFlags(extraFlags)
    val target = if (context.packageManager.resolveActivity(deep, 0) != null) deep else web
    runCatching { context.startActivity(target) }
}

/**
 * The two feedback options as a vertical list, Telegram FIRST (it's the
 * preferred channel) with its real brand icon, then Email. Reused by the
 * standalone [com.addiyon.keyboard.FeedbackActivity] and the Settings
 * feedback sheet so both stay in sync.
 */
@Composable
fun FeedbackOptions(
    onTelegram: () -> Unit,
    onEmail: () -> Unit
) {
    val strings = LocalAppStrings.current
    // Telegram first, with the real Telegram logo (rendered in its own brand
    // colors via Image, not tinted like a monochrome Material icon).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FeedbackTestTags.TELEGRAM)
            .clickable(onClick = onTelegram)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_telegram),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = strings.telegram,
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
            text = strings.email,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
