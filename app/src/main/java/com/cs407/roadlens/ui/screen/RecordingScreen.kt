package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Temporary UI-only Recording Screen.
 * This screen visually represents the camera preview,
 * live recording indicator, timer, and bottom controls.
 */
@Composable
fun RecordingScreen(
    cameraAllowed: Boolean = true,     // from ViewModel
    accelActive: Boolean = true,       // placeholder for sensor state
    onToggleRecording: (Boolean) -> Unit = {},
    onManualSave: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    // Recording state and timer counter
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    // Simple timer logic: increases every second while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1.seconds)
                elapsedSeconds++
            }
        }
    }

    // Stop recording if camera permission is revoked
    LaunchedEffect(cameraAllowed) {
        if (!cameraAllowed && isRecording) {
            isRecording = false
            onToggleRecording(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        // ----- Preview area (gradient placeholder) -----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF26333F),
                            Color(0xFF1E2A35),
                            Color(0xFF1B262F)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            // Top bar: speed/GPS left, Live indicator right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("0 MPH", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "GPS: Active",
                        color = Color(0xFF52D273),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isRecording) Color(0xFFED4245) else Color(0xFF64748B))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRecording) "LIVE REC" else "PREVIEW",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Optional back button (if navigation passes a callback)
            onBack?.let { back ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 6.dp, start = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x66121315))
                        .clickable { back() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("← Back", color = Color.White, fontSize = 12.sp)
                }
            }

            // Timer capsule at bottom center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC111315))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatHms(elapsedSeconds),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // ----- Bottom control bar -----
        Surface(
            color = Color(0xFFF7F7FA),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: ACCEL state
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ACCEL", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (accelActive) "Active" else "Paused",
                            color = if (accelActive) Color(0xFF16A34A) else Color.Gray,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Center: large circular record button
                    val recordEnabled = cameraAllowed
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (recordEnabled) Color(0xFFFF7A00)
                                else Color(0xFFFF7A00).copy(alpha = 0.35f)
                            )
                            .border(2.dp, Color(0xFFFFA552), CircleShape)
                            .let { base ->
                                if (recordEnabled) {
                                    base.clickable {
                                        isRecording = !isRecording
                                        if (!isRecording) elapsedSeconds = 0
                                        onToggleRecording(isRecording)
                                    }
                                } else base
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏺", color = Color.White, fontSize = 22.sp)
                    }

                    // Right: MANUAL Save Clip
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = cameraAllowed) { onManualSave() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val manualColor =
                            if (cameraAllowed) Color(0xFFEA4335) else Color(0xFFEA4335).copy(alpha = 0.4f)
                        Text("MANUAL", color = manualColor, fontWeight = FontWeight.SemiBold)
                        Text("Save Clip", color = manualColor, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Show hint when camera is not granted
                if (!cameraAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Camera permission required. Go to Settings.",
                        color = Color(0xFFEA4335),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Format seconds into HH:MM:SS */
private fun formatHms(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
