package com.addiyon.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.feedback.FeedbackOptions
import com.addiyon.keyboard.ui.feedback.openFeedbackTelegram
import com.addiyon.keyboard.ui.feedback.sendFeedbackEmail
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme
import com.addiyon.keyboard.ui.AppPageTopBar

/**
 * Standalone host for "Send feedback", opened from the keyboard toolbar's
 * feedback icon (replacing the old in-keyboard bottom sheet). Offers the same
 * two choices -- Telegram (first) and Email -- and finishes when one is picked
 * or the user backs out.
 */
class FeedbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideAppLocalization {
                AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                    FeedbackScreen(onBack = { finish() }, onPicked = { finish() })
                }
            }
        }
    }
}

@Composable
private fun FeedbackScreen(onBack: () -> Unit, onPicked: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppPageTopBar(
                title = strings.sendFeedback,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp)
        ) {
            FeedbackOptions(
                onTelegram = {
                    openFeedbackTelegram(context)
                    onPicked()
                },
                onEmail = {
                    sendFeedbackEmail(context, strings.feedbackEmailSubject)
                    onPicked()
                }
            )
        }
    }
}
