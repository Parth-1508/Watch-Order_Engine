package com.example.watchorderengine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResetSuccessScreen(
    onDone: () -> Unit
) {
    val engineAccent = Color(0xFFFFBF3C)
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                "PASSWORD RESET COMPLETE",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = engineAccent,
                textAlign = TextAlign.Center
            )
            
            Text(
                "Your password has been updated. You can now close any browser tabs and log in to the Engine.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = engineAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BACK TO LOGIN", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
