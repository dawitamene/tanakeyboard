package com.addiyon.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.addiyon.keyboard.ai.AiRepository
import com.addiyon.keyboard.ai.AiServiceFactory
import com.addiyon.keyboard.ui.ai.AiAuthBottomSheet
import com.addiyon.keyboard.ui.ai.AiDashboardContent
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiAccountActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            setTheme(R.style.Theme_AddiyonKeyboard)
            super.onCreate(savedInstanceState)
            applyAddiyonEdgeToEdge()
            val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_AUTH
            val tokenFromIntent = intent.data?.getQueryParameter("token")
                ?: intent.getStringExtra(EXTRA_TOKEN)

            setContent {
                ProvideAppLocalization {
                    AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                        var mode by remember { mutableStateOf(initialMode) }
                        var authEmail by remember { mutableStateOf(KeyboardPrefs.aiEmail(this@AiAccountActivity) ?: "") }
                        var authSending by remember { mutableStateOf(false) }
                        var authMessage by remember { mutableStateOf<String?>(null) }
                        var showEmailField by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        val jwt = KeyboardPrefs.aiJwt(this@AiAccountActivity)
                        val isLoggedIn = !jwt.isNullOrBlank()

                        LaunchedEffect(tokenFromIntent) {
                            if (!tokenFromIntent.isNullOrBlank()) {
                                KeyboardPrefs.setAiJwt(this@AiAccountActivity, tokenFromIntent)
                                authMessage = "Authenticated"
                                mode = MODE_DASHBOARD
                            }
                        }

                        LaunchedEffect(mode, isLoggedIn) {
                            if (mode == MODE_DASHBOARD && !isLoggedIn) {
                                mode = MODE_AUTH
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (mode) {
                                MODE_DASHBOARD -> {
                                    AiDashboardContent(
                                        onBack = { finish() },
                                        onLogout = {
                                            KeyboardPrefs.setAiJwt(this@AiAccountActivity, null)
                                            mode = MODE_AUTH
                                        },
                                        onSwitchToAuth = { mode = MODE_AUTH }
                                    )
                                }
                                else -> {
                                    AiAuthBottomSheet(
                                        email = authEmail,
                                        onEmailChanged = { authEmail = it },
                                        sending = authSending,
                                        message = authMessage,
                                        showEmailField = showEmailField,
                                        isLoggedIn = isLoggedIn,
                                        onContinueWithGoogle = {
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        kotlinx.coroutines.delay(800)
                                                    }
                                                    authMessage = "Google Sign-In coming soon — use email for now"
                                                } finally {
                                                    authSending = false
                                                }
                                            }
                                        },
                                        onEnterEmailClick = {
                                            showEmailField = true
                                        },
                                        onSendLink = {
                                            val email = authEmail.trim()
                                            if (!email.contains("@") || !email.contains(".")) {
                                                authMessage = "Enter a valid email"
                                                return@AiAuthBottomSheet
                                            }
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val repo = AiRepository(AiServiceFactory.create())
                                                val res = withContext(Dispatchers.IO) {
                                                    repo.issueMagicLink(email, "addiyon://auth/callback")
                                                }
                                                res.onSuccess { r ->
                                                    KeyboardPrefs.setAiEmail(this@AiAccountActivity, email)
                                                    authMessage = if (r.devLink != null) "Link sent (dev): ${r.devLink}" else "Check your email for the link"
                                                }.onFailure { t ->
                                                    authMessage = t.message ?: "Failed to send link"
                                                }
                                                authSending = false
                                            }
                                        },
                                        onDismiss = { finish() },
                                        onGoToDashboard = { mode = MODE_DASHBOARD }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            SafeLog.e(oom, "AiAccountActivity onCreate OOM")
            finish()
        } catch (t: Throwable) {
            SafeLog.e(t, "AiAccountActivity onCreate")
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val token = intent.data?.getQueryParameter("token") ?: intent.getStringExtra(EXTRA_TOKEN)
        if (!token.isNullOrBlank()) {
            KeyboardPrefs.setAiJwt(this, token)
        }
    }

    companion object {
        const val EXTRA_MODE = "ai_mode"
        const val EXTRA_TOKEN = "ai_token"
        const val MODE_AUTH = "auth"
        const val MODE_DASHBOARD = "dashboard"
    }
}
