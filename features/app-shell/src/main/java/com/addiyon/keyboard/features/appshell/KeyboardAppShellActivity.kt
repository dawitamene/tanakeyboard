package com.addiyon.keyboard.features.appshell

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.addiyon.keyboard.features.appshell.review.PlayReviewPlatform
import com.addiyon.keyboard.features.appshell.review.ReviewPromptController
import com.addiyon.keyboard.features.appshell.update.InAppUpdateController
import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme

abstract class KeyboardAppShellActivity : ComponentActivity() {
    protected abstract val keyboardProduct: KeyboardProduct

    private var destinationRequest by mutableStateOf<String?>(null)
    private var updateReadyToInstall by mutableStateOf(false)
    private var updateController: InAppUpdateController? = null
    private var reviewController: ReviewPromptController? = null

    protected open val contentThemeResource: Int = R.style.Theme_KeyboardAppShell

    @Composable
    protected abstract fun keyboardAppShellConfig(): KeyboardAppShellConfig

    @Composable
    protected open fun ProvideProductComposition(content: @Composable () -> Unit) {
        content()
    }

    @Composable
    protected open fun AppShellOverlay() = Unit

    protected open fun onCreateAppShell(savedInstanceState: Bundle?) = Unit

    protected open fun onResumeAppShell() = Unit

    protected open fun onDestroyAppShell() = Unit

    protected open fun onSettingsShown() = Unit

    protected open fun onOnboardingCompleted() = Unit

    protected open fun onAppShellFailure(failure: Throwable) = Unit

    protected open fun onAppShellPlatformFailure(operation: String, failure: Throwable) {
        onAppShellFailure(failure)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationRequest = intent.getStringExtra(EXTRA_OPEN_DESTINATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (contentThemeResource != 0) setTheme(contentThemeResource)
        try {
            super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            applySystemBarContrast()
            initializePlayLifecycle(savedInstanceState == null)
            onCreateAppShell(savedInstanceState)
            destinationRequest = intent?.getStringExtra(EXTRA_OPEN_DESTINATION)
            setContent {
                ProvideProductComposition {
                    AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                        val config = keyboardAppShellConfig()
                        require(config.product == keyboardProduct)
                        val status by rememberKeyboardAppStatus()
                        KeyboardAppShell(
                            config = config,
                            status = status,
                            requestedDestinationId = destinationRequest,
                            onDestinationRequestConsumed = { destinationRequest = null },
                            onFinish = ::finish,
                            onOnboardingCompleted = ::onOnboardingCompleted,
                            onSettingsShown = ::dispatchSettingsShown,
                            overlay = {
                                AppShellOverlay()
                                KeyboardUpdateReadyBar(
                                    visible = updateReadyToInstall,
                                    onInstall = { updateController?.completeUpdate() }
                                )
                            }
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            reportPlatformFailure("appShell", failure)
            renderFallback()
        }
    }

    private fun applySystemBarContrast() {
        val darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).run {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    override fun onResume() {
        super.onResume()
        updateController?.onResume()
        onResumeAppShell()
    }

    override fun onDestroy() {
        reviewController?.onDestroy()
        updateController?.onDestroy()
        onDestroyAppShell()
        super.onDestroy()
    }

    private fun initializePlayLifecycle(freshLaunch: Boolean) {
        updateController = runCatching {
            InAppUpdateController(
                activity = this,
                freshLaunch = freshLaunch,
                onReadyToInstall = { updateReadyToInstall = true },
                onFailure = { operation, failure ->
                    reportPlatformFailure("inAppUpdate.$operation", failure)
                }
            )
        }.onFailure {
            reportPlatformFailure("inAppUpdate.create", it)
        }.getOrNull()
        reviewController = runCatching {
            ReviewPromptController(
                sessions = { KeyboardPrefs.usageSessions(this) },
                alreadyPrompted = { KeyboardPrefs.reviewPrompted(this) },
                markPrompted = { KeyboardPrefs.setReviewPrompted(this) },
                platform = PlayReviewPlatform(this),
                hostIsActive = { !isFinishing && !isDestroyed },
                onFailure = { reportPlatformFailure("inAppReview", it) }
            )
        }.onFailure {
            reportPlatformFailure("inAppReview.create", it)
        }.getOrNull()
    }

    private fun dispatchSettingsShown() {
        reviewController?.onNaturalMoment()
        onSettingsShown()
    }

    private fun reportPlatformFailure(operation: String, failure: Throwable) {
        runCatching { onAppShellPlatformFailure(operation, failure) }
    }

    private fun renderFallback() {
        try {
            setContent {
                AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                    Text(getString(R.string.keyboard_shell_error))
                }
            }
        } catch (_: Throwable) {
            finish()
        }
    }

    companion object {
        const val EXTRA_OPEN_DESTINATION = "open_screen"
    }
}
