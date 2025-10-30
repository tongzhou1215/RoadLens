package com.cs407.roadlens.feature.home

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
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ---- Main area (fills all space above the bottom bar) ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF4F2F7))        // light background
                .padding(horizontal = 32.dp, vertical = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Move title down a little
            Spacer(Modifier.height(28.dp))

            // Title + subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Roadlens",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF16A34A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Mobile Dashcam & Accident Reporter",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Push buttons to the vertical middle
            Spacer(Modifier.weight(1f))

            // Buttons block (centered vertically)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    )
                ) { Text("Start Recording", fontWeight = FontWeight.Bold) }

                FilledTonalButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("View Album", fontWeight = FontWeight.Medium) }

                FilledTonalButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Settings", fontWeight = FontWeight.Medium) }
            }

            // Balance space below buttons to keep them centered
            Spacer(Modifier.weight(1f))
        }

        // ---- Bottom status bar ----
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F2F7))
        ) {
            Row(
                Modifier
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
