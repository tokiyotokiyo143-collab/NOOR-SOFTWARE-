package com.example.ui.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LocalNoorColors
import com.example.ui.theme.noorCard

@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (String, String) -> Unit,
    onSendReset: (String, (Boolean, String) -> Unit) -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isForgotMode by remember { mutableStateOf(false) }
    var resetFeedback by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.noor_software_logo_1795681598224),
                contentDescription = "Noor Software Logo",
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Noor Software",
                color = palette.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "AN-NOOR Mobile Service Point",
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(22.dp))

            if (!isForgotMode) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("owner or staff email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signin_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signin_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = palette.statusRed,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signin_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        isForgotMode = true
                        resetFeedback = null
                    },
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text("Forgot password?", color = palette.accent)
                }
            } else {
                Text(
                    text = "Reset Password",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("you@gmail.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (!resetFeedback.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(resetFeedback!!, color = palette.statusAmber, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSendReset(email) { ok, msg ->
                            resetFeedback = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text("Send Reset Link", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { isForgotMode = false }) {
                    Text("Back to Sign In", color = palette.textSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Shop Owner & active staff created in Settings → Users can sign in offline or online. All offline bills sync automatically when internet returns.",
                color = palette.textMuted,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun VerifyOwnerEmailScreen(
    email: String,
    onSendVerification: ((String) -> Unit) -> Unit,
    onCheckVerified: ((String) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    val palette = LocalNoorColors.current
    var infoMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.MarkEmailUnread,
                contentDescription = "Verify Email",
                tint = palette.accent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Verify Owner Email", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(email, color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Owner email verification is required once before Firestore unlocks Owner admin permissions. Tap \"Send Verification Email\", confirm the link in your inbox, then tap \"I've Verified — Continue\".",
                color = palette.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            if (!infoMsg.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(infoMsg!!, color = palette.statusAmber, fontSize = 12.5.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSendVerification { infoMsg = it } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Send Verification Email", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onCheckVerified { infoMsg = it } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("I've Verified — Continue", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut) {
                Text("Sign Out", color = palette.textSecondary)
            }
        }
    }
}

@Composable
fun AccessBlockedScreen(
    email: String,
    reason: String,
    onSignOut: () -> Unit
) {
    val palette = LocalNoorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = "Access Blocked",
                tint = palette.statusRed,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Access Blocked", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(email, color = palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(reason, color = palette.statusAmber, fontSize = 13.5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Back to Sign In", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PinLockOverlay(
    onUnlock: (String) -> Boolean,
    onSignOut: () -> Unit
) {
    val palette = LocalNoorColors.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.98f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .noorCard(palette, radius = 20.dp, highlighted = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Device App PIN Lock", color = palette.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Enter your 4–6 digit PIN to unlock the register.", color = palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pin = it },
                label = { Text("4–6 Digit PIN Code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("pin_unlock_input"),
                shape = RoundedCornerShape(12.dp)
            )
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = palette.statusRed, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!onUnlock(pin)) {
                        error = "Incorrect PIN. Try again."
                        pin = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pin_unlock_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut) {
                Text("Sign Out", color = palette.textSecondary)
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    val palette = LocalNoorColors.current
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text("Change Password", fontWeight = FontWeight.Bold, color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (success) {
                    Text(message.orEmpty(), color = palette.statusGreen, fontSize = 13.5.sp)
                } else {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current Password") },
                        placeholder = { Text("Enter current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password (min 6 characters)") },
                        placeholder = { Text("Enter new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm New Password") },
                        placeholder = { Text("Re-enter new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!message.isNullOrBlank()) {
                        Text(message!!, color = palette.statusRed, fontSize = 12.5.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (success) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Done", color = Color.White) }
            } else {
                Button(
                    onClick = {
                        onSubmit(current, newPass, confirm) { ok, msg ->
                            success = ok
                            message = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Update Password", color = Color.White) }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
            }
        }
    )
}
