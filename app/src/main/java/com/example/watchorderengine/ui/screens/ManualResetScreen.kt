package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ManualResetScreen(
    onCodeExtracted: (String) -> Unit,
    onBack: () -> Unit
) {
    val engineAccent = Color(0xFFFFBF3C)
    var linkText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PASTE RESET LINK", fontSize = 22.sp, fontWeight = FontWeight.Black, color = engineAccent)
            Text(
                "Copy the link from your email and paste it below to continue safely inside the app.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            OutlinedTextField(
                value = linkText,
                onValueChange = { linkText = it; errorMessage = null },
                label = { Text("Reset Link") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://watch-order-engine.firebaseapp.com/...", fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = engineAccent,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            val currentError = errorMessage
            if (currentError != null) {
                Text(currentError, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val trimmedLink = linkText.trim()
                    
                    // NEW: Handles both standard 'oobCode=' and encoded 'oobCode%3D'
                    val codeMatch = Regex("oobCode(?:=|%3D)([^&%\\s]+)").find(trimmedLink)
                    val code = codeMatch?.groupValues?.get(1)

                    if (code.isNullOrBlank()) {
                        errorMessage = "Could not find the security code in that link. Please make sure you copied the full address."
                    } else {
                        onCodeExtracted(code)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = engineAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentPaste, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("CONTINUE TO RESET", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("Back to Login", color = Color.Gray)
            }
        }
    }
}
