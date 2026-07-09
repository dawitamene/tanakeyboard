package com.addiyon.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Transparent, no-UI activity that requests RECORD_AUDIO on behalf of
 * [AddiyonKeyboardService]. An InputMethodService has no window of its own and
 * cannot call requestPermissions() directly -- this is the standard
 * workaround (the same one AOSP/Gboard-style keyboards use): launch a tiny
 * Activity that only exists to ask, then finish.
 *
 * Finishes immediately either way; it never starts recognition itself. The
 * service re-checks the permission the next time the user taps the mic
 * button (see AddiyonKeyboardService.onVoiceInput()) -- there's no callback
 * threaded back from here, since a Service can't be waited on.
 */
class VoicePermissionActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            finish()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
