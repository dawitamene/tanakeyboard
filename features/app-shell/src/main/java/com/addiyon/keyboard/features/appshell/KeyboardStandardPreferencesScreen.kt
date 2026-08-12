package com.addiyon.keyboard.features.appshell

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addiyon.keyboard.ui.settings.KeyboardPrefs

enum class KeyboardPreferenceSetting {
    VIBRATION,
    SOUND,
    NUMBER_ROW
}

data class KeyboardPreferencesCopy(
    val title: String,
    val back: String,
    val keyboardHeight: String,
    val vibration: String,
    val sound: String,
    val numberRow: String
)

@Composable
fun KeyboardStandardPreferencesScreen(
    copy: KeyboardPreferencesCopy,
    onBack: () -> Unit,
    onOpenKeyboardHeight: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingChanged: (KeyboardPreferenceSetting, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var vibrate by remember { mutableStateOf(KeyboardPrefs.vibrateOnKeypress(context)) }
    var sound by remember { mutableStateOf(KeyboardPrefs.soundOnKeypress(context)) }
    var numberRow by remember { mutableStateOf(KeyboardPrefs.numberRow(context)) }
    val hasVibrator = remember { deviceHasVibrator(context) }
    val toggles = buildList {
        if (hasVibrator) {
            add(KeyboardPreferenceToggle(copy.vibration, vibrate) {
                vibrate = it
                KeyboardPrefs.setVibrateOnKeypress(context, it)
                onSettingChanged(KeyboardPreferenceSetting.VIBRATION, it)
            })
        }
        add(KeyboardPreferenceToggle(copy.sound, sound) {
            sound = it
            KeyboardPrefs.setSoundOnKeypress(context, it)
            onSettingChanged(KeyboardPreferenceSetting.SOUND, it)
        })
        add(KeyboardPreferenceToggle(copy.numberRow, numberRow) {
            numberRow = it
            KeyboardPrefs.setNumberRow(context, it)
            onSettingChanged(KeyboardPreferenceSetting.NUMBER_ROW, it)
        })
    }
    KeyboardPreferencesScreen(
        title = copy.title,
        backContentDescription = copy.back,
        links = listOf(KeyboardPreferenceLink(copy.keyboardHeight, onOpenKeyboardHeight)),
        toggles = toggles,
        onBack = onBack,
        modifier = modifier
    )
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
