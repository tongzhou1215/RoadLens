package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.roadlens.viewmodel.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onBack: () -> Unit = {},
    onSave: (AppSettings) -> Unit = {}
) {
    // ---- Local UI state ----
    var grantCamera by rememberSaveable { mutableStateOf(currentSettings.cameraGranted) }
    var grantLocation by rememberSaveable { mutableStateOf(currentSettings.locationGranted) }
    var saveGps by rememberSaveable { mutableStateOf(currentSettings.saveGps) }
    var crashSensitivity by rememberSaveable { mutableFloatStateOf(currentSettings.crashSensitivity) } // 0f..1f

    val baseDurationOptions = listOf("30 Seconds", "1 Minute", "3 Minutes", "5 Minutes")
    val durationOptions = remember(currentSettings.loopDuration) {
        if (currentSettings.loopDuration in baseDurationOptions) baseDurationOptions
        else baseDurationOptions + currentSettings.loopDuration
    }
    var loopDuration by rememberSaveable {
        mutableStateOf(
            currentSettings.loopDuration.takeIf { it.isNotBlank() } ?: baseDurationOptions[1]
        )
    }
    var durationExpanded by remember { mutableStateOf(false) }

    var emergencyContact by rememberSaveable { mutableStateOf(currentSettings.emergencyContact) }

    // 如果外部 currentSettings 变化，同步表单
    LaunchedEffect(currentSettings) {
        grantCamera = currentSettings.cameraGranted
        grantLocation = currentSettings.locationGranted
        saveGps = currentSettings.saveGps
        crashSensitivity = currentSettings.crashSensitivity
        loopDuration = currentSettings.loopDuration.takeIf { it in durationOptions } ?: durationOptions.first()
        emergencyContact = currentSettings.emergencyContact
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                AppSettings(
                                    cameraGranted = grantCamera,
                                    locationGranted = grantLocation,
                                    saveGps = saveGps,
                                    loopDuration = loopDuration,
                                    crashSensitivity = crashSensitivity,
                                    emergencyContact = emergencyContact
                                )
                            )
                        }
                    ) { Text("Save") }
                }
            )
        },
        bottomBar = {
            Surface(color = Color(0xFFF6F6F8), shadowElevation = 6.dp) {
                Button(
                    onClick = {
                        onSave(
                            AppSettings(
                                cameraGranted = grantCamera,
                                locationGranted = grantLocation,
                                saveGps = saveGps,
                                loopDuration = loopDuration,
                                crashSensitivity = crashSensitivity,
                                emergencyContact = emergencyContact
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save Changes")
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera
            item {
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Camera Permission", fontSize = 16.sp)
                            Text("Allow camera access for recording", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = grantCamera, onCheckedChange = { grantCamera = it })
                    }
                }
            }

            // Location
            item {
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Location Permission", fontSize = 16.sp)
                            Text("Allow location access for GPS tagging", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = grantLocation, onCheckedChange = { grantLocation = it })
                    }
                }
            }

            // Save GPS
            item {
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save GPS with Clips", fontSize = 16.sp)
                            Text("Attach GPS coordinates to saved clips", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = saveGps, onCheckedChange = { saveGps = it })
                    }
                }
            }

            // Loop Duration (dropdown)
            item {
                SettingCard {
                    Text("Loop Duration", fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = durationExpanded,
                        onExpandedChange = { durationExpanded = !durationExpanded }
                    ) {
                        OutlinedTextField(
                            value = loopDuration,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = durationExpanded,
                            onDismissRequest = { durationExpanded = false }
                        ) {
                            durationOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        loopDuration = opt
                                        durationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Crash Sensitivity (slider)
            item {
                SettingCard {
                    Text("Crash Sensitivity", fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = crashSensitivity,
                        onValueChange = { crashSensitivity = it },
                        valueRange = 0f..1f
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Low", color = Color.Gray, fontSize = 12.sp)
                        Text(String.format("%.2f", crashSensitivity), color = Color.Gray, fontSize = 12.sp)
                        Text("High", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            // Emergency Contact
            item {
                SettingCard {
                    Text("Emergency Contact", fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = emergencyContact,
                        onValueChange = { emergencyContact = it },
                        placeholder = { Text("Phone / Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Spacer(Modifier.height(64.dp)) } // 给底部按钮留空间
        }
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7F7FA),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
