package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Minimal Home screen. If you already have your own NavPage routing,
 * keep that and ignore this; this version makes no assumptions.
 */
@Composable
fun HomeScreen(
    cameraGranted: Boolean,
    cameraPermissionPermanentlyDenied: Boolean,
    onStartRecording: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onViewAlbum: () -> Unit,
    onSettings: () -> Unit,
    onContacts: () -> Unit,
) {
    var showPermissionDialog by remember { mutableStateOf(false) }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0E1B2C), Color(0xFF122C4F), Color(0xFF0F172A))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Roadlens",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5E7EB)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Mobile Dashcam & Accident Reporter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9CA3AF),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = {
                            if (cameraGranted) {
                                onStartRecording()
                            } else {
                                showPermissionDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF60A5FA),
                            contentColor = Color(0xFF0B1224)
                        )
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Start Recording", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = onViewAlbum,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = Color(0xFFE5E7EB)
                        )
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "View Album", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = onContacts,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = Color(0xFFE5E7EB)
                        )
                    ) {
                        Icon(Icons.Rounded.Contacts, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Emergency Contacts", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = Color(0xFFE5E7EB)
                        )
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Settings", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            CameraStatusBanner(cameraGranted = cameraGranted)
        }
    }

    if (showPermissionDialog) {
        val message = if (cameraPermissionPermanentlyDenied) {
            "Camera access has been denied. Please enable the permission from the system settings to start recording."
        } else {
            "Roadlens needs access to your camera before recording can begin."
        }

        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        if (cameraPermissionPermanentlyDenied) {
                            onOpenAppSettings()
                        } else {
                            onRequestCameraPermission()
                        }
                    }
                ) {
                    Text(if (cameraPermissionPermanentlyDenied) "Open Settings" else "Grant Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CameraStatusBanner(cameraGranted: Boolean) {
    val (icon, label, tint) = if (cameraGranted) {
        Triple(Icons.Rounded.CheckCircle, "Camera permission granted", Color(0xFF34D399))
    } else {
        Triple(Icons.Rounded.WarningAmber, "Camera permission not granted", Color(0xFFFBBF24))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE5E7EB)
            )
        }
    }
}
