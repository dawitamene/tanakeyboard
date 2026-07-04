package com.addiyon.tanakeyboard

import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.ui.theme.CustomKeyboardTheme
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            // A normal Activity DOES get reliable configuration updates,
            // so isSystemInDarkTheme() is fine to use here. The keyboard's
            // own IME window (TanaKeyboardView) tracks dark mode manually
            // instead -- see TanaKeyboardService.isDarkTheme for why.
            CustomKeyboardTheme(isDarkTheme = isSystemInDarkTheme()) {
                Scaffold { innerPadding ->
                    TanaKeyboardTest(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TanaKeyboardTest(modifier: Modifier = Modifier) {

    val context = LocalContext.current
    val (text, setText) = remember {
        mutableStateOf(TextFieldValue(""))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 128.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                )
            }
        ) {
            Text("Open Keyboard Settings")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                (context.getSystemService(INPUT_METHOD_SERVICE)
                        as InputMethodManager)
                    .showInputMethodPicker()
            }
        ) {
            Text("Show Keyboard Picker")
        }

        Text("Test your keyboard")

        OutlinedTextField(
            value = text,
            onValueChange = setText,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        )
    }
}