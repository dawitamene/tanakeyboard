package com.addiyon.keyboard.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.R

private val BorderGray = Color(0xFFD9D9D9)
private val DividerGray = Color(0xFFE8E8E8)
private val TextDark = Color(0xFF121212)
private val HintGray = Color(0xFF8E8E8E)
private val OrGray = Color(0xFF8A8A8A)

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        contentColor = TextDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Login / Register",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                lineHeight = 28.sp
            )
            Spacer(Modifier.height(24.dp))

            when (step) {
                AuthStep.Email -> {
                    OutlinedButton(
                        onClick = onContinueWithGoogle,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderGray),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = TextDark)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(10.dp))
                        Text(text = "Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Divider(modifier = Modifier.weight(1f), thickness = 1.dp, color = DividerGray)
                        Text(text = "OR", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OrGray, modifier = Modifier.padding(horizontal = 16.dp))
                        Divider(modifier = Modifier.weight(1f), thickness = 1.dp, color = DividerGray)
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Email", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChanged,
                            placeholder = { Text(text = "Enter your email address", fontSize = 15.sp, color = HintGray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderGray, unfocusedBorderColor = BorderGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, cursorColor = TextDark, focusedTextColor = TextDark, unfocusedTextColor = TextDark)
                        )
                        if (message != null) {
                            val isError = message.contains("valid", true) || message.contains("Failed", true) || message.contains("error", true)
                            Text(text = message, fontSize = 13.sp, color = if (isError) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onContinueEmail,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text(text = "Continue", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                AuthStep.Password -> {
                    Text(text = email, fontSize = 14.sp, color = HintGray, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChanged,
                        placeholder = { Text("Password", color = HintGray) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderGray, unfocusedBorderColor = BorderGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                    if (message != null) Text(text = message, fontSize = 13.sp, color = Color(0xFFD32F2F), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLogin, enabled = !sending, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                        if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White) else Text("Log in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBackToEmail, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("Back") }
                }
                AuthStep.Otp -> {
                    Text(text = "Code sent to $email", fontSize = 14.sp, color = HintGray, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = otp,
                        onValueChange = onOtpChanged,
                        placeholder = { Text("6-digit code", color = HintGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderGray, unfocusedBorderColor = BorderGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                    if (message != null) Text(text = message, fontSize = 13.sp, color = Color(0xFFD32F2F), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onVerifyOtp, enabled = !sending, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                        if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White) else Text("Verify", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onSendOtp, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Resend") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onBackToEmail, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Back") }
                    }
                }
                AuthStep.Register -> {
                    Text(text = "Create account for $email", fontSize = 14.sp, color = HintGray, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(value = name, onValueChange = onNameChanged, placeholder = { Text("Full name", color = HintGray) }, singleLine = true, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderGray, unfocusedBorderColor = BorderGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = password, onValueChange = onPasswordChanged, placeholder = { Text("Password (min 8)", color = HintGray) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BorderGray, unfocusedBorderColor = BorderGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
                    if (message != null) Text(text = message, fontSize = 13.sp, color = Color(0xFFD32F2F), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRegister, enabled = !sending, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                        if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White) else Text("Create account", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBackToEmail, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("Back to email") }
                }
            }
        }
    }
}
