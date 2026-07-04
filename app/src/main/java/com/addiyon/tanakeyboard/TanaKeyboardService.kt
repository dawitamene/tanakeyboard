package com.addiyon.tanakeyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class TanaKeyboardService : InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    // ----------------------------
// Lifecycle (UNCHANGED)
// ----------------------------
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

// ----------------------------
// KEYBOARD STATE (ADDED ONLY)
// ----------------------------

    var isAmharic by mutableStateOf(false)
        private set

    var isShiftEnabled by mutableStateOf(false)
        private set

    var isCapsLock by mutableStateOf(false)
        private set

    // Tracked manually instead of relying on Compose's isSystemInDarkTheme(),
    // because an InputMethodService's window doesn't reliably deliver
    // configuration updates into the Compose tree the way an Activity does.
    // We read the current mode on creation and again whenever
    // onConfigurationChanged fires, so the keyboard UI can react to the
    // system dark/light toggle even while it's open.
    var isDarkTheme by mutableStateOf(false)
        private set

    private fun updateDarkThemeFromConfiguration(configuration: Configuration) {
        val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun toggleLanguage() {
        isAmharic = !isAmharic
    }

    fun toggleShift() {
        isShiftEnabled = !isShiftEnabled
    }

    fun setShift(enabled: Boolean) {
        isShiftEnabled = enabled
    }

    fun toggleCaps() {
        isCapsLock = !isCapsLock
    }

    fun resetShift() {
        isShiftEnabled = false
    }

// ----------------------------
// IME LIFECYCLE (UNCHANGED)
// ----------------------------

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        return TanaKeyboardView(this)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        updateDarkThemeFromConfiguration(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDarkThemeFromConfiguration(newConfig)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        // Catch any theme change that happened while the keyboard was hidden.
        updateDarkThemeFromConfiguration(resources.configuration)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

}