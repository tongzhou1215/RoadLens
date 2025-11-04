package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun HomeScreen(
    onViewAlbum: () -> Unit = {},  // <-- for navigation
    onStartRecording: () -> Unit = {},  // placeholder for future
    onSettings: () -> Unit = {},         // placeholder for future
    cameraGranted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Top section (title area) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF4F2F7))
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // App title
            Text(
                text = "Roadlens",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF16A34A),
                textAlign = TextAlign.Center
            )

            // Subtitle
            Text(
                text = "Mobile Dashcam & Accident Reporter",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // --- Buttons ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Recording
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    )
                ) {
                    Text("Start Recording", fontWeight = FontWeight.Bold)
                }

                // View Album
                FilledTonalButton(
                    onClick = onViewAlbum,  // <-- Navigates to AlbumScreen
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("View Album", fontWeight = FontWeight.Medium)
                }

                // Settings
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Settings", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // --- Bottom status bar ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F2F7))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GPS: ", fontWeight = FontWeight.SemiBold)
                    Text("Ready", color = Color(0xFF16A34A))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Storage: ", fontWeight = FontWeight.SemiBold)
                    Text("15% Free (1.2 GB)", color = Color(0xFFE57373))
                }
            }
        }
    }
}
