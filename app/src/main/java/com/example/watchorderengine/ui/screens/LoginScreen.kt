package com.example.watchorderengine.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.example.watchorderengine.R
import com.example.watchorderengine.ui.viewmodel.LoginViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

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

    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                isLoading = true
                viewModel.signInWithCredential(credential) { success, error ->
                    isLoading = false
                    if (success) onLoginSuccess()
                    else errorMessage = error
                }
            } catch (e: ApiException) {
                errorMessage = "Google sign in failed: ${e.message} (Status Code: ${e.statusCode})"
            }
        }
    }

    // Magic Clipboard Detection
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item?.text?.toString() ?: ""
                    if (text.contains("oobCode=") && text.contains("resetPassword")) {
                        clipboardLink = text
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val engineAccent = Color(0xFFFFC300)

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
            Text(stringResource(R.string.login_title_watch_order), fontSize = 32.sp, fontWeight = FontWeight.Black, color = engineAccent)
            Text(stringResource(R.string.login_title_engine), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            
            Spacer(Modifier.height(48.dp))

            if (isSignUp) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.login_display_name)) },
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
                label = { Text(stringResource(R.string.login_email)) },
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
                label = { Text(stringResource(R.string.login_password)) },
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

            if (errorMessage != null) {
                Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            if (resetEmailSent) {
                Text(stringResource(R.string.login_link_sent), color = Color(0xFF4ADE80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onManualResetClick) {
                    Text(stringResource(R.string.login_link_not_opening), color = engineAccent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = engineAccent)
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || (password.isBlank() && !isSignUp)) {
                            errorMessage = context.getString(R.string.login_fill_all_fields)
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        val task = if (isSignUp) auth.createUserWithEmailAndPassword(email, password)
                                   else auth.signInWithEmailAndPassword(email, password)

                        task.addOnCompleteListener { result ->
                            if (result.isSuccessful) {
                                if (isSignUp) viewModel.saveUsername(username)
                                else viewModel.syncUsernameFromCloud()
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
                    Text(if (isSignUp) stringResource(R.string.login_create_account) else stringResource(R.string.login_button_login), color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                // Social Logins
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken("103547777861-dnhefnhft99dnhugodfbtcbtis13jd3b.apps.googleusercontent.com")
                                .requestEmail()
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            // Force sign out and clear session to ensure the account picker always appears
                            isLoading = true
                            client.signOut().addOnCompleteListener {
                                client.revokeAccess().addOnCompleteListener {
                                    isLoading = false
                                    googleSignInLauncher.launch(client.signInIntent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.login_google), fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null; resetEmailSent = false }) {
                        Text(if (isSignUp) stringResource(R.string.login_instead) else stringResource(R.string.login_create_account_small), color = engineAccent, fontSize = 13.sp)
                    }

                    if (!isSignUp) {
                        TextButton(
                            enabled = !isLoading && resendTimer == 0,
                            onClick = {
                                if (email.isBlank()) { errorMessage = context.getString(R.string.login_enter_email_to_reset); return@TextButton }
                                isLoading = true
                                auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { 
                                    isLoading = false
                                    if (it.isSuccessful) { resetEmailSent = true; resendTimer = 60 }
                                    else errorMessage = it.exception?.message
                                }
                            }
                        ) {
                            Text(if (resendTimer > 0) stringResource(R.string.login_resend_in, resendTimer) else stringResource(R.string.login_forgot_password), color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }

                TextButton(onClick = {
                    isLoading = true
                    auth.signInAnonymously().addOnCompleteListener { task ->
                        if (task.isSuccessful) onLoginSuccess()
                        else errorMessage = task.exception?.message
                        isLoading = false
                    }
                }) {
                    Text(stringResource(R.string.login_continue_as_guest), color = Color.Gray)
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
                title = { Text(stringResource(R.string.login_reset_link_detected), fontWeight = FontWeight.Black) },
                text = { Text(stringResource(R.string.login_reset_link_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        val link = clipboardLink ?: ""
                        val code = Regex("oobCode(?:=|%3D)([^&%\\s]+)").find(link)?.groupValues?.get(1) ?: ""
                        clipboardLink = null
                        onCodeDetected(code)
                    }) {
                        Text(stringResource(R.string.login_continue), color = engineAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clipboardLink = null }) {
                        Text(stringResource(R.string.login_cancel), color = Color.Gray)
                    }
                }
            )
        }
    }
}
