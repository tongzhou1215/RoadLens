package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal Home screen. If you already have your own NavPage routing,
 * keep that and ignore this; this version makes no assumptions.
 */
@Composable
fun HomeScreen(
    cameraGranted: Boolean,
    onStartRecording: () -> Unit,
    onViewAlbum: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("RoadLens", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            Button(onClick = onStartRecording) {
                Text(if (cameraGranted) "Open Recording" else "Grant Camera in Settings")
            }
            Spacer(Modifier.height(12.dp))

            Button(onClick = onViewAlbum) { Text("Open Album") }
            Spacer(Modifier.height(12.dp))

            Button(onClick = onSettings) { Text("Settings") }
        }
    }
}
