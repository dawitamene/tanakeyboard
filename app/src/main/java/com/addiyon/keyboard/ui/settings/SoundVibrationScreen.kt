package com.addiyon.keyboard.ui.settings

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.AppPageTopBar

/**
 * Sound & vibration settings: two independent keypress toggles, persisted
 * via [KeyboardPrefs] so the IME service can honor them.
 */
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
    // A device with no vibrator (some tablets, TV boxes) can't honor the
    // vibrate toggle, so don't offer it at all.
    val hasVibrator = remember { deviceHasVibrator(context) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppPageTopBar(
                title = strings.preferences,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            GroupCard {
                if (hasVibrator) {
                    ToggleRow(
                        label = strings.vibrateOnKeypress,
                        checked = vibrate,
                        onCheckedChange = {
                            vibrate = it
                            KeyboardPrefs.setVibrateOnKeypress(context, it)
                        }
                    )
                }
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

private fun deviceHasVibrator(context: Context): Boolean {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return vibrator?.hasVibrator() == true
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
