package com.addiyon.keyboard.features.appshell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

internal object KeyboardExternalActionRunner {
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

object KeyboardExternalActions {
    const val DEFAULT_PRIVACY_POLICY_URL = "https://keyboard.addiyon.com/privacy.html"

    fun canResolve(context: Context, intent: Intent): Boolean = try {
        intent.component != null ||
            context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) != null
    } catch (_: Throwable) {
        false
    }

    fun start(context: Context, intent: Intent, failureMessage: String): Boolean =
        KeyboardExternalActionRunner.run(
            canLaunch = { canResolve(context, intent) },
            launch = { context.startActivity(intent) },
            onFailure = { showFailure(context, failureMessage) }
        )

    fun startDirect(context: Context, intent: Intent, failureMessage: String): Boolean =
        KeyboardExternalActionRunner.run(
            canLaunch = { true },
            launch = { context.startActivity(intent) },
            onFailure = { showFailure(context, failureMessage) }
        )

    fun tryStartDirect(context: Context, intent: Intent): Boolean =
        KeyboardExternalActionRunner.run(
            canLaunch = { true },
            launch = { context.startActivity(intent) },
            onFailure = {}
        )

    fun openInputMethodSettings(
        context: Context,
        failureMessage: String = context.getString(R.string.keyboard_settings_unavailable)
    ): Boolean = start(
        context,
        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
        failureMessage
    )

    fun openPrivacyPolicy(
        context: Context,
        url: String = DEFAULT_PRIVACY_POLICY_URL,
        extraFlags: Int = 0,
        failureMessage: String = context.getString(R.string.browser_unavailable)
    ): Boolean = startDirect(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(extraFlags),
        failureMessage
    )

    fun showInputMethodPicker(
        context: Context,
        failureMessage: String = context.getString(R.string.keyboard_switcher_unavailable)
    ): Boolean = KeyboardExternalActionRunner.run(
        canLaunch = { context.getSystemService(InputMethodManager::class.java) != null },
        launch = {
            requireNotNull(context.getSystemService(InputMethodManager::class.java))
                .showInputMethodPicker()
        },
        onFailure = { showFailure(context, failureMessage) }
    )

    fun shareApplication(
        context: Context,
        shareText: String,
        chooserTitle: String,
        failureMessage: String = context.getString(R.string.share_unavailable)
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        return start(context, Intent.createChooser(intent, chooserTitle), failureMessage)
    }

    fun rateApplication(
        context: Context,
        failureMessage: String = context.getString(R.string.app_store_unavailable)
    ): Boolean {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}")
        )
        if (canResolve(context, market)) return start(context, market, failureMessage)
        return start(
            context,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
            ),
            failureMessage
        )
    }

    private fun showFailure(context: Context, message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
        }
    }
}
