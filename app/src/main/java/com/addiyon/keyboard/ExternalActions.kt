package com.addiyon.keyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

internal object ExternalActionRunner {
    fun run(
        canLaunch: () -> Boolean,
        launch: () -> Unit,
        onFailure: () -> Unit
    ): Boolean {
        val available = try {
            canLaunch()
        } catch (_: Throwable) {
            false
        }
        if (!available) {
            onFailure()
            return false
        }
        return try {
            launch()
            true
        } catch (_: Throwable) {
            onFailure()
            false
        }
    }
}

object ExternalActions {
    fun canResolve(context: Context, intent: Intent): Boolean = try {
        intent.component != null ||
            context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) != null
    } catch (_: Throwable) {
        false
    }

    fun start(
        context: Context,
        intent: Intent,
        failureMessage: String
    ): Boolean = ExternalActionRunner.run(
        canLaunch = { canResolve(context, intent) },
        launch = { context.startActivity(intent) },
        onFailure = { showFailure(context, failureMessage) }
    )

    fun openInputMethodSettings(context: Context): Boolean =
        start(
            context,
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            "Keyboard settings are unavailable on this device."
        )

    fun showInputMethodPicker(context: Context): Boolean = ExternalActionRunner.run(
        canLaunch = {
            context.getSystemService(InputMethodManager::class.java) != null
        },
        launch = {
            requireNotNull(context.getSystemService(InputMethodManager::class.java))
                .showInputMethodPicker()
        },
        onFailure = {
            showFailure(context, "The keyboard switcher is unavailable on this device.")
        }
    )

    private fun showFailure(context: Context, message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
        }
    }
}
