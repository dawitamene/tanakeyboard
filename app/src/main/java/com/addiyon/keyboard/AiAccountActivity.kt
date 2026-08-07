package com.addiyon.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.addiyon.keyboard.ai.AiRepository
import com.addiyon.keyboard.ai.AiServiceFactory
import com.addiyon.keyboard.ui.ai.AiAuthBottomSheet
import com.addiyon.keyboard.ui.ai.AuthStep
import com.addiyon.keyboard.ui.ai.AiDashboardContent
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class AiAccountActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            setTheme(R.style.Theme_AddiyonKeyboard)
            super.onCreate(savedInstanceState)
            applyAddiyonEdgeToEdge()
            val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_AUTH

            setContent {
                ProvideAppLocalization {
                    AddiyonBrandTheme(isDarkTheme = isSystemInDarkTheme()) {
                        var mode by remember { mutableStateOf(initialMode) }
                        var authEmail by remember { mutableStateOf(KeyboardPrefs.aiEmail(this@AiAccountActivity) ?: "") }
                        var authPassword by remember { mutableStateOf("") }
                        var authName by remember { mutableStateOf("") }
                        var authOtp by remember { mutableStateOf("") }
                        var authSending by remember { mutableStateOf(false) }
                        var authMessage by remember { mutableStateOf<String?>(null) }
                        var authStep by remember { mutableStateOf(AuthStep.Email) }
                        var pendingOtpToken by remember { mutableStateOf<String?>(null) }
                        val scope = rememberCoroutineScope()
                        val repo = remember { AiRepository(AiServiceFactory.create()) }

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
                                        onEmailChanged = { authEmail = it; authMessage = null },
                                        password = authPassword,
                                        onPasswordChanged = { authPassword = it; authMessage = null },
                                        name = authName,
                                        onNameChanged = { authName = it; authMessage = null },
                                        otp = authOtp,
                                        onOtpChanged = { authOtp = it; authMessage = null },
                                        sending = authSending,
                                        message = authMessage,
                                        step = authStep,
                                        onContinueWithGoogle = {
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                try {
                                                    val idToken = getGoogleIdToken()
                                                    if (idToken == null) {
                                                        authMessage = "Google Sign-In cancelled"
                                                        return@launch
                                                    }
                                                    val res = withContext(Dispatchers.IO) { repo.googleToken(idToken) }
                                                    res.onSuccess { r ->
                                                        KeyboardPrefs.setAiJwt(this@AiAccountActivity, r.token)
                                                        r.user?.email?.let { KeyboardPrefs.setAiEmail(this@AiAccountActivity, it) }
                                                        mode = MODE_DASHBOARD
                                                    }.onFailure { t ->
                                                        authMessage = t.message ?: "Google sign-in failed"
                                                    }
                                                } catch (e: Exception) {
                                                    authMessage = e.message ?: "Google sign-in failed"
                                                } finally {
                                                    authSending = false
                                                }
                                            }
                                        },
                                        onContinueEmail = {
                                            val email = authEmail.trim()
                                            if (!email.contains("@") || !email.contains(".")) {
                                                authMessage = "Enter a valid email"
                                                return@AiAuthBottomSheet
                                            }
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.authContinue(email) }
                                                res.onSuccess { r ->
                                                    KeyboardPrefs.setAiEmail(this@AiAccountActivity, email)
                                                    when (r.nextStep.lowercase()) {
                                                        "password" -> { authStep = AuthStep.Password; authMessage = null }
                                                        "otp", "otp_sent", "otp_required" -> {
                                                            val otpRes = withContext(Dispatchers.IO) { repo.sendOtp(email) }
                                                            otpRes.onSuccess {
                                                                authStep = AuthStep.Otp
                                                                authMessage = "Code sent to $email"
                                                            }.onFailure { t -> authMessage = t.message ?: "Failed to send code" }
                                                        }
                                                        else -> { authStep = AuthStep.Otp; authMessage = r.nextStep }
                                                    }
                                                }.onFailure { t -> authMessage = t.message ?: "Failed" }
                                                authSending = false
                                            }
                                        },
                                        onLogin = {
                                            val email = authEmail.trim()
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.login(email, authPassword) }
                                                res.onSuccess { r ->
                                                    KeyboardPrefs.setAiJwt(this@AiAccountActivity, r.token)
                                                    r.user?.email?.let { KeyboardPrefs.setAiEmail(this@AiAccountActivity, it) }
                                                    mode = MODE_DASHBOARD
                                                }.onFailure { t -> authMessage = t.message ?: "Login failed" }
                                                authSending = false
                                            }
                                        },
                                        onSendOtp = {
                                            val email = authEmail.trim()
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.sendOtp(email) }
                                                res.onSuccess { authMessage = "Code resent" }.onFailure { t -> authMessage = t.message ?: "Failed" }
                                                authSending = false
                                            }
                                        },
                                        onVerifyOtp = {
                                            val email = authEmail.trim()
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.verifyOtp(email, authOtp) }
                                                res.onSuccess { r ->
                                                    pendingOtpToken = r.token
                                                    authStep = AuthStep.Register
                                                    authMessage = null
                                                }.onFailure { t -> authMessage = t.message ?: "Invalid code" }
                                                authSending = false
                                            }
                                        },
                                        onRegister = {
                                            if (authName.trim().length < 2) { authMessage = "Name must be 2-50 chars"; return@AiAuthBottomSheet }
                                            if (authPassword.length < 8) { authMessage = "Password must be at least 8 chars"; return@AiAuthBottomSheet }
                                            val token = pendingOtpToken ?: run { authMessage = "Verify code first"; return@AiAuthBottomSheet }
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.register(token, authName.trim(), authPassword) }
                                                res.onSuccess { r ->
                                                    KeyboardPrefs.setAiJwt(this@AiAccountActivity, r.token)
                                                    r.user?.email?.let { KeyboardPrefs.setAiEmail(this@AiAccountActivity, it) }
                                                    mode = MODE_DASHBOARD
                                                }.onFailure { t -> authMessage = t.message ?: "Registration failed" }
                                                authSending = false
                                            }
                                        },
                                        onBackToEmail = { authStep = AuthStep.Email; authMessage = null },
                                        onDismiss = { finish() }
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

    private suspend fun getGoogleIdToken(): String? {
        return try {
            val serverClientId = getString(R.string.default_web_client_id)
            if (serverClientId.isBlank() || serverClientId.startsWith("YOUR")) return null
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val manager = CredentialManager.create(this)
            val result = manager.getCredential(this, request)
            val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
            cred.idToken
        } catch (e: GetCredentialException) {
            null
        } catch (_: Exception) {
            null
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
