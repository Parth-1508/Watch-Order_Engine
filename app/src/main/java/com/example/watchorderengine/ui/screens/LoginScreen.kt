package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val theme = LocalAppTheme.current
    val auth = FirebaseAuth.getInstance()
    var isLoading by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                color = theme.accent
            )
            Text(
                "ENGINE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = theme.accent,
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
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = theme.accent,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = theme.accent)
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill all fields"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        val task = if (isSignUp) {
                            auth.createUserWithEmailAndPassword(email, password)
                        } else {
                            auth.signInWithEmailAndPassword(email, password)
                        }

                        task.addOnCompleteListener { result ->
                            if (result.isSuccessful) {
                                onLoginSuccess()
                            } else {
                                errorMessage = result.exception?.message ?: "Authentication failed"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text(
                        if (isSignUp) "CREATE ACCOUNT" else "LOGIN",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        if (isSignUp) "Already have an account? Login" else "New user? Create account",
                        color = theme.accent
                    )
                }

                Spacer(Modifier.height(16.dp))

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
    }
}
