package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchorderengine.ui.theme.LocalAppTheme
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ResetPasswordScreen(
    oobCode: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val theme = LocalAppTheme.current
    val auth = FirebaseAuth.getInstance()
    
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    // Lock to default "Engine" colors
    val engineAccent = Color(0xFFFFBF3C)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "NEW PASSWORD",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = engineAccent
            )
            Text(
                "Secure your Engine account",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(48.dp))

            if (isSuccess) {
                Text(
                    "Password changed successfully!",
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = onSuccess,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = engineAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("GO TO LOGIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = engineAccent,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = engineAccent,
                        unfocusedBorderColor = Color.Gray,
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
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = engineAccent)
                } else {
                    Button(
                        onClick = {
                            if (oobCode.isBlank()) {
                                errorMessage = "Invalid or missing security code. Please request a new link."
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null
                            
                            auth.confirmPasswordReset(oobCode, newPassword)
                                .addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) {
                                        isSuccess = true
                                    } else {
                                        val exception = task.exception
                                        android.util.Log.e("ResetPassword", "Error", exception)
                                        errorMessage = exception?.message ?: "Failed to reset password. The link may be expired."
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = engineAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("UPDATE PASSWORD", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }
}
