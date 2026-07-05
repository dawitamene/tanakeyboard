package com.addiyon.tanakeyboard

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
 * Whether TanaKeyboardService is enabled/default, asked of the live system
 * state -- there's no cached/persisted flag to fall out of sync.
 *
 * Reads go through [InputMethodManager]'s public API, NOT raw
 * Settings.Secure reads: Android 14 put ENABLED_INPUT_METHODS (and friends)
 * behind a readable-settings allowlist, so `Settings.Secure.getString` on
 * them throws SecurityException for apps targeting SDK 34+ -- which crashed
 * MainActivity on launch on any Android 14+ device. (The one exception is
 * [isDefault] on pre-34 devices, where DEFAULT_INPUT_METHOD is still
 * readable and there's no public "current IME" API yet.)
 *
 * IME ids are compared as [ComponentName]s, not raw strings, because the
 * system mixes short (".TanaKeyboardService") and fully-qualified class
 * forms depending on OS version/OEM.
 */
object KeyboardStatus {
    private fun self(context: Context) =
        ComponentName(context, TanaKeyboardService::class.java)

    private fun imm(context: Context) =
        context.getSystemService(InputMethodManager::class.java)

    fun isEnabled(context: Context): Boolean {
        val self = self(context)
        return imm(context)?.enabledInputMethodList
            ?.any { it.component == self } == true
    }

    fun isDefault(context: Context): Boolean {
        val self = self(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            imm(context)?.currentInputMethodInfo?.component == self
        } else {
            val default = try {
                Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
                )
            } catch (e: SecurityException) {
                null
            } ?: return false
            ComponentName.unflattenFromString(default) == self
        }
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
