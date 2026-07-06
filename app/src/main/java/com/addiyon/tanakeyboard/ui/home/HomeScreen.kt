package com.addiyon.tanakeyboard.ui.home

import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.KeyboardStatusSnapshot

@Composable
fun HomeScreen(
    status: KeyboardStatusSnapshot,
    onOpenManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (text, setText) = remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (status.isDefault) {
                        "Tana Keyboard is active"
                    } else if (status.enabled) {
                        "Enabled, but not your default keyboard"
                    } else {
                        "Tana Keyboard is not enabled"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (!status.enabled) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    ) {
                        Text("Enable in Settings")
                    }
                } else if (!status.isDefault) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        onClick = {
                            (context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                                .showInputMethodPicker()
                        }
                    ) {
                        Text("Switch Keyboard")
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        ) {
            Text("Keyboard Settings")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenManual
        ) {
            Text("Transliteration Manual")
        }

        Text("Try it out", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = text,
            onValueChange = setText,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Type \"selam\" → ሰላም") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            minLines = 3,
            maxLines = 5
        )
    }
}
