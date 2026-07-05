package com.addiyon.tanakeyboard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Whether TanaKeyboardService is enabled/default, read straight from
 * Settings.Secure -- the system is the only source of truth here, so there's
 * no cached/persisted flag to fall out of sync.
 *
 * IME ids are compared as [ComponentName]s, not raw strings, because the
 * settings values mix short (".TanaKeyboardService") and fully-qualified
 * class forms depending on OS version/OEM.
 */
object KeyboardStatus {
    private fun self(context: Context) =
        ComponentName(context, TanaKeyboardService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        val self = self(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    fun isDefault(context: Context): Boolean {
        val default = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return ComponentName.unflattenFromString(default) == self(context)
    }
}

data class KeyboardStatusSnapshot(val enabled: Boolean, val isDefault: Boolean)

/**
 * Re-reads [KeyboardStatus] on every ON_RESUME (user leaves to system
 * Settings and comes back) AND on window-focus regain -- the input method
 * picker is a system dialog that steals window focus without pausing the
 * activity, so focus regain is the only signal that the user just picked
 * a keyboard in it.
 */
@Composable
fun rememberKeyboardStatus(): State<KeyboardStatusSnapshot> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun read() = KeyboardStatusSnapshot(
        KeyboardStatus.isEnabled(context), KeyboardStatus.isDefault(context)
    )
    val state = remember { mutableStateOf(read()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) state.value = read()
    }

    return state
}
