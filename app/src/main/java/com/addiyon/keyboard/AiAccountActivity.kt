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
                                        onSwitchToAuth = { mode = MODE_AUTH },
                                        quotaLoader = { jwt, anonId -> repo.quota(jwt, anonId) }
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
                                                    val tokenResult = getGoogleIdTokenResult()
                                                    val idToken = tokenResult.token
                                                    if (idToken == null) {
                                                        authMessage = tokenResult.errorMessage
                                                        return@launch
                                                    }
                                                    val res = withContext(Dispatchers.IO) { repo.googleToken(idToken) }
                                                    res.onSuccess { r ->
                                                        KeyboardPrefs.setAiJwt(this@AiAccountActivity, r.token)
                                                        r.user?.email?.let { KeyboardPrefs.setAiEmail(this@AiAccountActivity, it) }
                                                        mode = MODE_DASHBOARD
                                                    }.onFailure { t ->
                                                        val err = repo.parseAiError(t)
                                                        authMessage = when (err) {
                                                            is com.addiyon.keyboard.ai.AiError.RateLimited -> "Too many attempts — wait ${err.retryAfter ?: 60}s"
                                                            is com.addiyon.keyboard.ai.AiError.Server -> err.message.substringAfter("Server(").substringBeforeLast(")").ifBlank { t.message }
                                                            else -> t.message
                                                        } ?: "Google sign-in failed"
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
                                                            authStep = AuthStep.Otp
                                                            authMessage = "Code sent to $email"
                                                        }
                                                        else -> { authStep = AuthStep.Otp; authMessage = r.nextStep }
                                                    }
                                                }.onFailure { t ->
                                                    val err = repo.parseAiError(t)
                                                    authMessage = when (err) {
                                                        is com.addiyon.keyboard.ai.AiError.RateLimited -> "Too many attempts — wait ${err.retryAfter ?: 60}s"
                                                        is com.addiyon.keyboard.ai.AiError.Server -> err.message.substringAfter("Server(").substringBeforeLast(")").ifBlank { t.message }
                                                        else -> t.message
                                                    } ?: "Failed"
                                                }
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
                                                }.onFailure { t ->
                                                    val err = repo.parseAiError(t)
                                                    authMessage = when (err) {
                                                        is com.addiyon.keyboard.ai.AiError.RateLimited -> "Too many attempts — wait ${err.retryAfter ?: 60}s"
                                                        is com.addiyon.keyboard.ai.AiError.Server -> {
                                                            val raw = err.message.substringAfter("Server(").substringBeforeLast(")")
                                                            when {
                                                                raw.contains("Invalid email or password", true) -> "Invalid email or password"
                                                                raw.contains("banned", true) -> "This account has been banned"
                                                                raw.contains("suspended", true) -> raw
                                                                else -> raw.ifBlank { t.message }
                                                            }
                                                        }
                                                        else -> t.message
                                                    } ?: "Login failed"
                                                }
                                                authSending = false
                                            }
                                        },
                                        onSendOtp = {
                                            val email = authEmail.trim()
                                            scope.launch {
                                                authSending = true
                                                authMessage = null
                                                val res = withContext(Dispatchers.IO) { repo.sendOtp(email) }
                                                res.onSuccess { authMessage = "Code resent" }.onFailure { t ->
                                                    val err = repo.parseAiError(t)
                                                    authMessage = when (err) {
                                                        is com.addiyon.keyboard.ai.AiError.RateLimited -> "Too many codes — wait ${err.retryAfter ?: 60}s"
                                                        else -> t.message
                                                    } ?: "Failed"
                                                }
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
                                                }.onFailure { t ->
                                                    val err = repo.parseAiError(t)
                                                    authMessage = when (err) {
                                                        is com.addiyon.keyboard.ai.AiError.Server -> err.message.substringAfter("Server(").substringBeforeLast(")").ifBlank { t.message }
                                                        else -> t.message
                                                    } ?: "Invalid code"
                                                }
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

    private data class GoogleTokenResult(val token: String?, val errorMessage: String)

    private suspend fun getGoogleIdTokenResult(): GoogleTokenResult {
        val serverClientId = getString(R.string.default_web_client_id)
        if (serverClientId.isBlank() || serverClientId.startsWith("YOUR")) {
            return GoogleTokenResult(null, "Google Sign-In not configured — check default_web_client_id")
        }
        val manager = CredentialManager.create(this)
        fun buildOption(filterByAuthorized: Boolean, autoSelect: Boolean) = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorized)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(autoSelect)
            .build()

        suspend fun tryRequest(option: GetGoogleIdOption): String? {
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = manager.getCredential(this, request)
            val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
            return cred.idToken.takeIf { !it.isNullOrBlank() }
        }

        return try {
            var token: String? = null
            var lastError: GetCredentialException? = null
            try {
                token = tryRequest(buildOption(false, false))
            } catch (e: GetCredentialException) {
                lastError = e
                val lower = (e.message ?: "").lowercase()
                val isNoCred = e is androidx.credentials.exceptions.NoCredentialException || lower.contains("no credential")
                if (isNoCred) {
                    try {
                        token = tryRequest(buildOption(true, false))
                    } catch (e2: GetCredentialException) {
                        lastError = e2
                    }
                }
            }
            if (!token.isNullOrBlank()) return GoogleTokenResult(token, "")
            throw lastError ?: Exception("Google Sign-In failed — no ID token")
        } catch (e: GetCredentialException) {
            val msg = e.message ?: ""
            val lower = msg.lowercase()
            val isNoCred = e is androidx.credentials.exceptions.NoCredentialException || lower.contains("no credential") || lower.contains("no account")
            val isCancelled = lower.contains("canceled") || lower.contains("cancelled") || lower.contains("dismissed") || lower.contains("user cancelled")
            val isInterrupted = lower.contains("interrupted")
            val userMessage = when {
                isNoCred -> "No Google account available — ensure a Google account is added, Google Play Services is updated, and the app's SHA-1 is registered in Google Cloud Console. (${e.message})"
                isCancelled -> "Google Sign-In cancelled"
                isInterrupted -> "Google Sign-In interrupted — try again"
                lower.contains("network") -> "Network error during Google Sign-In — check connection and try again"
                else -> "Google Sign-In failed: ${e.message ?: "unknown error"}"
            }
            SafeLog.e(e, "getGoogleIdToken failed: ${e::class.simpleName} ${e.message}")
            GoogleTokenResult(null, userMessage)
        } catch (e: Exception) {
            SafeLog.e(e, "getGoogleIdToken failed")
            GoogleTokenResult(null, e.message ?: "Google Sign-In failed")
        }
    }

    @Deprecated("Use getGoogleIdTokenResult")
    private suspend fun getGoogleIdToken(): String? = getGoogleIdTokenResult().token

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
