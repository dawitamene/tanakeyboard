package com.addiyon.tanakeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.ui.feedback.FeedbackOptions
import com.addiyon.tanakeyboard.ui.feedback.openFeedbackTelegram
import com.addiyon.tanakeyboard.ui.feedback.sendFeedbackEmail
import com.addiyon.tanakeyboard.ui.i18n.LocalAppStrings
import com.addiyon.tanakeyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.tanakeyboard.ui.theme.TanaBrandTheme

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
                TanaBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                    FeedbackScreen(onBack = { finish() }, onPicked = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackScreen(onBack: () -> Unit, onPicked: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.sendFeedback) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
