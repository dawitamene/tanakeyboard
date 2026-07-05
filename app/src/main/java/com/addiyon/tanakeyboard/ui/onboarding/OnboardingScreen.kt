package com.addiyon.tanakeyboard.ui.onboarding

import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.KeyboardStatusSnapshot

/**
 * First-run walkthrough: explain the app, then guide the user through
 * enabling the keyboard and switching to it. Auto-advances to Home only
 * once the keyboard is the default -- advancing on merely "enabled" would
 * skip the guided step 2. A manual Continue/Skip button is the fallback.
 */
@Composable
fun OnboardingScreen(
    status: KeyboardStatusSnapshot,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(status.isDefault) {
        if (status.isDefault) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Welcome to Tana Keyboard",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Type Amharic (Ge'ez) using Latin transliteration -- " +
                "for example, typing \"selam\" gives you ሰላም. " +
                "Two quick steps to get started:",
            style = MaterialTheme.typography.bodyMedium
        )

        OnboardingStep(
            stepNumber = 1,
            title = "Enable Tana Keyboard",
            description = "Turn it on in your system's input method settings.",
            done = status.enabled,
            buttonLabel = "Open Settings",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        OnboardingStep(
            stepNumber = 2,
            title = "Switch to Tana Keyboard",
            description = "Pick it from the keyboard switcher to start typing.",
            done = status.isDefault,
            buttonLabel = "Switch Keyboard",
            onClick = {
                (context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        )

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone
        ) {
            Text(if (status.enabled) "Continue" else "Skip for now")
        }
    }
}

@Composable
private fun OnboardingStep(
    stepNumber: Int,
    title: String,
    description: String,
    done: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Step $stepNumber: $title",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}
