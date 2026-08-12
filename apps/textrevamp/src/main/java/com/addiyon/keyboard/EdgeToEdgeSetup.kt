package com.addiyon.keyboard

import android.app.Activity
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Switches the activity into edge-to-edge without pulling in the deprecated
 * [androidx.activity.EdgeToEdge] API path (which still calls
 * [android.view.Window.setStatusBarColor],
 * [android.view.Window.setNavigationBarColor], and sets
 * [android.view.WindowManager.LayoutParams.layoutInDisplayCutoutMode] to
 * `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, all deprecated on Android 15
 * and flagged by Play Console's pre-launch report).
 *
 * System-bar icon appearance is forced to match the activity's current
 * night-mode resource configuration so status/navigation icons stay readable
 * on top of the Addiyon brand surface. `enableEdgeToEdge` used to do this
 * detection automatically via `SystemBarStyle.detectDarkMode`; doing it
 * explicitly keeps the same behaviour without the deprecated calls.
 */
internal fun Activity.applyAddiyonEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val isDarkNightMode = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, window.decorView).run {
        isAppearanceLightStatusBars = !isDarkNightMode
        isAppearanceLightNavigationBars = !isDarkNightMode
    }
}
