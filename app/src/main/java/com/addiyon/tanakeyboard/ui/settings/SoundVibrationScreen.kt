package com.addiyon.tanakeyboard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.ui.i18n.LocalAppStrings

/**
 * Sound & vibration settings: two independent keypress toggles, persisted
 * via [KeyboardPrefs] so the IME service can honor them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundVibrationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var vibrate by remember { mutableStateOf(KeyboardPrefs.vibrateOnKeypress(context)) }
    var sound by remember { mutableStateOf(KeyboardPrefs.soundOnKeypress(context)) }
    var numberRow by remember { mutableStateOf(KeyboardPrefs.numberRow(context)) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.preferences) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            GroupCard {
                ToggleRow(
                    label = strings.vibrateOnKeypress,
                    checked = vibrate,
                    onCheckedChange = {
                        vibrate = it
                        KeyboardPrefs.setVibrateOnKeypress(context, it)
                    }
                )
                ToggleRow(
                    label = strings.soundOnKeypress,
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        KeyboardPrefs.setSoundOnKeypress(context, it)
                    }
                )
                ToggleRow(
                    label = strings.numberRow,
                    checked = numberRow,
                    onCheckedChange = {
                        numberRow = it
                        KeyboardPrefs.setNumberRow(context, it)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
