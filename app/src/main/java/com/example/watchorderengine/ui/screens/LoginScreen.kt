package com.example.watchorderengine.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.watchorderengine.ui.viewmodel.LoginViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onManualResetClick: () -> Unit = {},
    onCodeDetected: (String) -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = FirebaseAuth.getInstance()
    
    var isLoading by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetEmailSent by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableIntStateOf(0) }
    var clipboardLink by remember { mutableStateOf<String?>(null) }

    // Magic Clipboard Detection
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item?.text?.toString() ?: ""
                    // Check if it's a reset link and NOT the one we just processed
                    if (text.contains("oobCode=") && text.contains("resetPassword")) {
                        clipboardLink = text
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lock to default "Engine" colors for consistency with OpeningScreen
    val engineAccent = Color(0xFFFFBF3C)

    // Handle resend countdown
    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            kotlinx.coroutines.delay(1000)
            resendTimer--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {
            Text(
                "WATCH ORDER",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = engineAccent
            )
            Text(
                "ENGINE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(Modifier.height(48.dp))

            if (isSignUp) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = engineAccent,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = engineAccent,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = engineAccent,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = engineAccent,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = engineAccent,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = engineAccent,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            val currentError = errorMessage
            if (currentError != null) {
                Text(
                    text = currentError,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (resetEmailSent) {
                Text(
                    text = "Link sent! Update your password in the browser, then return here to login.",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onManualResetClick) {
                    Text("Link not opening the app? Paste it here.", color = engineAccent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = engineAccent)
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || (password.isBlank() && !isSignUp)) {
                            errorMessage = "Please fill all fields"
                            return@Button
                        }
                        if (isSignUp && username.isBlank()) {
                            errorMessage = "Please enter a display name"
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null
                        resetEmailSent = false
                        val task = if (isSignUp) {
                            auth.createUserWithEmailAndPassword(email, password)
                        } else {
                            auth.signInWithEmailAndPassword(email, password)
                        }

                        task.addOnCompleteListener { result ->
                            if (result.isSuccessful) {
                                if (isSignUp) {
                                    viewModel.saveUsername(username)
                                } else {
                                    viewModel.syncUsernameFromCloud()
                                }
                                onLoginSuccess()
                            } else {
                                errorMessage = result.exception?.message ?: "Authentication failed"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = engineAccent)
                ) {
                    Text(
                        if (isSignUp) "CREATE ACCOUNT" else "LOGIN",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null; resetEmailSent = false }) {
                        Text(
                            if (isSignUp) "Login instead" else "Create account",
                            color = engineAccent,
                            fontSize = 13.sp
                        )
                    }

                    if (!isSignUp) {
                        TextButton(
                            enabled = !isLoading && resendTimer == 0,
                            onClick = {
                                if (email.isBlank()) {
                                    errorMessage = "Enter your email to reset password"
                                    return@TextButton
                                }
                                
                                if (isLoading) return@TextButton
                                
                                isLoading = true
                                errorMessage = null
                                
                                val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                                    // Point to a clean path to avoid reserved path conflicts (Bug Fix)
                                    .setUrl("https://watch-order-engine.firebaseapp.com/success")
                                    .setHandleCodeInApp(true)
                                    .setAndroidPackageName(
                                        "com.example.watchorderengine",
                                        false,
                                        null
                                    )
                                    .build()

                                auth.sendPasswordResetEmail(email.trim(), actionCodeSettings).addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) {
                                        resetEmailSent = true
                                        resendTimer = 60
                                        errorMessage = null
                                    } else {
                                        val error = task.exception
                                        android.util.Log.e("AuthError", "Reset email failed", error)
                                        errorMessage = error?.message ?: "Failed to send reset email"
                                        resetEmailSent = false
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (resendTimer > 0) "Resend in ${resendTimer}s" else "Forgot Password?", 
                                color = if (resendTimer > 0) Color.Gray else Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!isSignUp) {
                    TextButton(onClick = onManualResetClick) {
                        Text("Having trouble with the reset link?", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                TextButton(onClick = {
                    isLoading = true
                    auth.signInAnonymously().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = task.exception?.message
                        }
                        isLoading = false
                    }
                }) {
                    Text("Continue as Guest", color = Color.Gray)
                }
            }
        }

        // Magic Clipboard Dialog
        if (clipboardLink != null) {
            AlertDialog(
                onDismissRequest = { clipboardLink = null },
                containerColor = Color(0xFF141B2D),
                titleContentColor = engineAccent,
                textContentColor = Color.White,
                title = { Text("Reset Link Detected", fontWeight = FontWeight.Black) },
                text = { Text("We found a password reset link in your clipboard. Would you like to use it now?") },
                confirmButton = {
                    TextButton(onClick = {
                        val link = clipboardLink ?: ""
                        // NEW: Handles both standard 'oobCode=' and encoded 'oobCode%3D'
                        val codeMatch = Regex("oobCode(?:=|%3D)([^&%\\s]+)").find(link)
                        val code = codeMatch?.groupValues?.get(1) ?: ""

                        clipboardLink = null
                        onCodeDetected(code)
                    }) {
                        Text("CONTINUE", color = engineAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clipboardLink = null }) {
                        Text("CANCEL", color = Color.Gray)
                    }
                }
            )
        }
    }
}
