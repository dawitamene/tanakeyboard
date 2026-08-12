package com.addiyon.keyboard.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.features.ai.R
import com.addiyon.keyboard.features.appshell.KeyboardPageTopBar
import com.addiyon.keyboard.ui.design.AddiyonInputField
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSizes
import com.addiyon.keyboard.ui.design.AddiyonSpacing

const val AI_AUTH_GOOGLE_ACTION_TAG = "ai.auth.google.action"
const val AI_AUTH_EMAIL_FIELD_TAG = "ai.auth.email.field"
const val AI_AUTH_PRIMARY_ACTION_TAG = "ai.auth.primary.action"

enum class AuthStep { Email, Password, Otp, Register }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAuthBottomSheet(
    email: String,
    onEmailChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    name: String,
    onNameChanged: (String) -> Unit,
    otp: String,
    onOtpChanged: (String) -> Unit,
    sending: Boolean,
    message: String?,
    step: AuthStep,
    onContinueWithGoogle: () -> Unit,
    onContinueEmail: () -> Unit,
    onLogin: () -> Unit,
    onSendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onRegister: () -> Unit,
    onBackToEmail: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = step != AuthStep.Email, onBack = onBackToEmail)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeyboardPageTopBar(
                title = "Sign in to AI",
                onBack = onDismiss,
                backContentDescription = "Close sign in"
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "TextRevamp AI",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Make every message sound like you",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    "Sign in to rephrase text, keep your account connected, and use your daily allowance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (step != AuthStep.Email) {
                OutlinedButton(
                    onClick = onBackToEmail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AddiyonSizes.formControl),
                    shape = RoundedCornerShape(AddiyonRadii.pill)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Back to email")
                }
            }

            when (step) {
                AuthStep.Email -> EmailStep(
                    email = email,
                    onEmailChanged = onEmailChanged,
                    sending = sending,
                    message = message,
                    onContinueWithGoogle = onContinueWithGoogle,
                    onContinueEmail = onContinueEmail
                )
                AuthStep.Password -> PasswordStep(
                    email = email,
                    password = password,
                    onPasswordChanged = onPasswordChanged,
                    sending = sending,
                    message = message,
                    onLogin = onLogin
                )
                AuthStep.Otp -> OtpStep(
                    email = email,
                    otp = otp,
                    onOtpChanged = onOtpChanged,
                    sending = sending,
                    message = message,
                    onVerifyOtp = onVerifyOtp,
                    onSendOtp = onSendOtp
                )
                AuthStep.Register -> RegisterStep(
                    email = email,
                    name = name,
                    onNameChanged = onNameChanged,
                    password = password,
                    onPasswordChanged = onPasswordChanged,
                    sending = sending,
                    message = message,
                    onRegister = onRegister
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmailStep(
    email: String,
    onEmailChanged: (String) -> Unit,
    sending: Boolean,
    message: String?,
    onContinueWithGoogle: () -> Unit,
    onContinueEmail: () -> Unit
) {
    OutlinedButton(
        onClick = onContinueWithGoogle,
        enabled = !sending,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AddiyonSizes.formControl)
            .testTag(AI_AUTH_GOOGLE_ACTION_TAG),
        shape = RoundedCornerShape(AddiyonRadii.pill),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.width(10.dp))
        Text("Continue with Google", fontWeight = FontWeight.Medium)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "OR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
    AuthFieldLabel("Email")
    AddiyonInputField(
        value = email,
        onValueChange = onEmailChanged,
        placeholder = "Enter your email address",
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AI_AUTH_EMAIL_FIELD_TAG)
    )
    MessageText(message)
    Box(modifier = Modifier.padding(top = AddiyonSpacing.xs)) {
        AuthActionButton(text = "Continue", sending = sending, onClick = onContinueEmail)
    }
}

@Composable
private fun PasswordStep(
    email: String,
    password: String,
    onPasswordChanged: (String) -> Unit,
    sending: Boolean,
    message: String?,
    onLogin: () -> Unit
) {
    Text(
        "Signing in as $email",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    AuthFieldLabel("Password")
    AddiyonInputField(
        value = password,
        onValueChange = onPasswordChanged,
        placeholder = "Password",
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    MessageText(message)
    AuthActionButton(text = "Log in", sending = sending, onClick = onLogin)
}

@Composable
private fun OtpStep(
    email: String,
    otp: String,
    onOtpChanged: (String) -> Unit,
    sending: Boolean,
    message: String?,
    onVerifyOtp: () -> Unit,
    onSendOtp: () -> Unit
) {
    Text(
        "Code sent to $email",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    val otpFocusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { otpFocusRequester.requestFocus() }
    ) {
        BasicTextField(
            value = otp,
            onValueChange = { value -> onOtpChanged(value.filter { it.isDigit() }.take(6)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(otpFocusRequester),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val char = otp.getOrNull(index)?.toString().orEmpty()
                        val isFocused = otp.length == index
                        val borderColor = when {
                            char.isNotEmpty() -> MaterialTheme.colorScheme.primary
                            isFocused -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AddiyonSizes.formControl)
                                .clip(RoundedCornerShape(AddiyonRadii.card))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.5.dp,
                                    borderColor,
                                    RoundedCornerShape(AddiyonRadii.card)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                char,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (isFocused && char.isEmpty()) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(20.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
    MessageText(message)
    AuthActionButton(text = "Verify", sending = sending, onClick = onVerifyOtp)
    OutlinedButton(
        onClick = onSendOtp,
        enabled = !sending,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AddiyonSizes.formControl),
        shape = RoundedCornerShape(AddiyonRadii.pill)
    ) { Text("Resend code") }
}

@Composable
private fun RegisterStep(
    email: String,
    name: String,
    onNameChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    sending: Boolean,
    message: String?,
    onRegister: () -> Unit
) {
    Text(
        "Create an account for $email",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    AuthFieldLabel("Full name")
    AddiyonInputField(
        value = name,
        onValueChange = onNameChanged,
        placeholder = "Full name",
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    AuthFieldLabel("Password")
    AddiyonInputField(
        value = password,
        onValueChange = onPasswordChanged,
        placeholder = "At least 8 characters",
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    MessageText(message)
    AuthActionButton(text = "Create account", sending = sending, onClick = onRegister)
}

@Composable
private fun AuthFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun MessageText(message: String?) {
    if (message == null) return
    val isError = listOf("valid", "failed", "error", "invalid", "banned", "suspended", "too many").any {
        message.contains(it, ignoreCase = true)
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun AuthActionButton(text: String, sending: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !sending,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AddiyonSizes.formControl)
            .testTag(AI_AUTH_PRIMARY_ACTION_TAG),
        shape = RoundedCornerShape(AddiyonRadii.pill)
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}
