package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
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

            if (isLoading) {
                CircularProgressIndicator(color = theme.accent)
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            auth.signInAnonymously().addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    onLoginSuccess()
                                }
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("START ENGINE (Guest)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
