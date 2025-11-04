package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings screen (UI only, no KeyboardOptions).
 * - Back arrow (top-left)
 * - Toggles for Camera/GPS
 * - Crash sensitivity slider (Low..High)
 * - Loop duration dropdown
 * - Save GPS toggle
 * - Emergency contact text field (plain)
 * - Bottom fixed "Save Changes" button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSave: (SettingsUiState) -> Unit = {}
) {
    // ---- Local UI state (temporary form values) ----
    var grantCamera by remember { mutableStateOf(false) }
    var grantLocation by remember { mutableStateOf(false) }
    var saveGps by remember { mutableStateOf(false) }

    var crashSensitivity by remember { mutableFloatStateOf(0.25f) } // 0f..1f

    val durationOptions = listOf("30 Seconds", "1 Minute", "3 Minutes", "5 Minutes")
    var loopDuration by remember { mutableStateOf(durationOptions[1]) }
    var durationExpanded by remember { mutableStateOf(false) }

    var emergencyContact by remember { mutableStateOf("") } // plain text field, no KeyboardOptions

    // ---- Layout ----
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "App Settings",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Camera toggle
            item {
                SettingCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Grant Camera Access", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Checkbox(checked = grantCamera, onCheckedChange = { grantCamera = it })
                    }
                }
            }

            // Location toggle
            item {
                SettingCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Grant Location (GPS) Access", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Checkbox(checked = grantLocation, onCheckedChange = { grantLocation = it })
                    }
                }
            }

            // Crash sensitivity
            item {
                SettingCard {
                    Text("Crash Sensitivity", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "based on the change of speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = crashSensitivity,
                        onValueChange = { crashSensitivity = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Low", style = MaterialTheme.typography.bodySmall)
                        Text("High", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Loop duration dropdown
            item {
                SettingCard {
                    Text("Loop Clip Duration", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = durationExpanded,
                        onExpandedChange = { durationExpanded = !durationExpanded }
                    ) {
                        OutlinedTextField(
                            value = loopDuration,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Duration") },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = durationExpanded,
                            onDismissRequest = { durationExpanded = false }
                        ) {
                            durationOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        loopDuration = option
                                        durationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Save GPS toggle
            item {
                SettingCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Save GPS Location", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Checkbox(checked = saveGps, onCheckedChange = { saveGps = it })
                    }
                }
            }

            // Emergency contact (plain OutlinedTextField, no KeyboardOptions)
            item {
                SettingCard {
                    Text("Emergency Contact", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emergencyContact,
                        onValueChange = { emergencyContact = it },
                        placeholder = { Text("0000000000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Bottom fixed Save button
        Surface(color = Color.White, tonalElevation = 2.dp) {
            Button(
                onClick = {
                    onSave(
                        SettingsUiState(
                            grantCamera = grantCamera,
                            grantLocation = grantLocation,
                            crashSensitivity = crashSensitivity,
                            loopDuration = loopDuration,
                            saveGps = saveGps,
                            emergencyContact = emergencyContact
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF7A00),
                    contentColor = Color.White
                )
            ) {
                Text("Save Changes", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Reusable card look for each settings block. */
@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** UI state passed back to caller when Save is pressed. */
data class SettingsUiState(
    val grantCamera: Boolean,
    val grantLocation: Boolean,
    val crashSensitivity: Float,
    val loopDuration: String,
    val saveGps: Boolean,
    val emergencyContact: String
)
