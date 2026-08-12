package com.addiyon.keyboard.features.appshell

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

object KeyboardAppStatusReader {
    fun snapshot(context: Context): KeyboardAppStatus = try {
        val manager = context.getSystemService(InputMethodManager::class.java)
            ?: return KeyboardAppStatus(false, false)
        val ownIme = manager.enabledInputMethodList.firstOrNull {
            it.packageName == context.packageName
        } ?: return KeyboardAppStatus(false, false)
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.currentInputMethodInfo?.component == ownIme.component
        } else {
            val id = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            ComponentName.unflattenFromString(id) == ownIme.component
        }
        KeyboardAppStatus(enabled = true, isDefault = isDefault)
    } catch (_: Throwable) {
        KeyboardAppStatus(false, false)
    }
}

@Composable
fun rememberKeyboardAppStatus(): State<KeyboardAppStatus> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun read() = KeyboardAppStatusReader.snapshot(context)
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
