package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.roadlens.viewmodel.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    cameraPermissionGranted: Boolean,
    cameraPermissionPermanentlyDenied: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit = {},
    onSave: (AppSettings) -> Unit = {}
) {
    // ---- Local UI state ----
    val sanitizedCurrent = remember(currentSettings, cameraPermissionGranted) {
        currentSettings.copy(cameraGranted = cameraPermissionGranted)
    }

    var grantLocation by rememberSaveable { mutableStateOf(sanitizedCurrent.locationGranted) }
    var saveGps by rememberSaveable { mutableStateOf(sanitizedCurrent.saveGps) }
    var crashSensitivity by rememberSaveable { mutableFloatStateOf(sanitizedCurrent.crashSensitivity) } // 0f..1f

    val baseDurationOptions = listOf("30 Seconds", "1 Minute", "3 Minutes", "5 Minutes")
    val durationOptions = remember(sanitizedCurrent.loopDuration) {
        if (sanitizedCurrent.loopDuration in baseDurationOptions) baseDurationOptions
        else baseDurationOptions + sanitizedCurrent.loopDuration
    }
    var loopDuration by rememberSaveable {
        mutableStateOf(
            sanitizedCurrent.loopDuration.takeIf { it.isNotBlank() } ?: baseDurationOptions[1]
        )
    }
    var durationExpanded by remember { mutableStateOf(false) }

    var emergencyContact by rememberSaveable { mutableStateOf(sanitizedCurrent.emergencyContact) }

    // 如果外部 currentSettings 变化，同步表单
    LaunchedEffect(sanitizedCurrent) {
        grantLocation = sanitizedCurrent.locationGranted
        saveGps = sanitizedCurrent.saveGps
        crashSensitivity = sanitizedCurrent.crashSensitivity
        loopDuration = sanitizedCurrent.loopDuration.takeIf { it in durationOptions } ?: durationOptions.first()
        emergencyContact = sanitizedCurrent.emergencyContact
    }

    val pendingSettings = remember(
        grantLocation,
        saveGps,
        loopDuration,
        crashSensitivity,
        emergencyContact,
        cameraPermissionGranted
    ) {
        AppSettings(
            cameraGranted = cameraPermissionGranted,
            locationGranted = grantLocation,
            saveGps = saveGps,
            loopDuration = loopDuration,
            crashSensitivity = crashSensitivity,
            emergencyContact = emergencyContact
        )
    }

    val hasChanges = pendingSettings != sanitizedCurrent

    fun saveAndClose() {
        if (hasChanges) {
            onSave(pendingSettings)
        } else {
            onBack()
        }
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
                        enabled = hasChanges,
                        onClick = { onSave(pendingSettings) }
                    ) { Text("Save") }
                }
            )
        },
        bottomBar = {
            Surface(color = Color(0xFFF6F6F8), shadowElevation = 6.dp) {
                Button(
                    onClick = ::saveAndClose,
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (hasChanges) "Save Changes" else "All Changes Saved")
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { SectionHeader(title = "Permissions") }

            // Camera
            item {
                SettingCard {
                    Text("Camera", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    val cameraMessage = when {
                        cameraPermissionGranted -> "Camera access is enabled."
                        cameraPermissionPermanentlyDenied -> "Camera access is blocked. Enable the permission from system settings to record."
                        else -> "Camera access is required to start recording."
                    }
                    Text(cameraMessage, color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (cameraPermissionGranted) {
                        StatusPill(
                            icon = Icons.Rounded.CheckCircle,
                            label = "Permission granted",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        val actionLabel = if (cameraPermissionPermanentlyDenied) "Open App Settings" else "Grant Permission"
                        Button(
                            onClick = if (cameraPermissionPermanentlyDenied) onOpenAppSettings else onRequestCameraPermission,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel)
                        }
                    }
                }
            }

            // Location
            item {
                SettingCard {
                    SettingToggleRow(
                        title = "Location",
                        subtitle = "Allow RoadLens to access GPS for tagging recordings",
                        checked = grantLocation,
                        onToggle = { grantLocation = it }
                    )
                }
            }

            // Save GPS
            item {
                SettingCard {
                    SettingToggleRow(
                        title = "Save GPS with clips",
                        subtitle = "Attach GPS coordinates when clips are exported",
                        checked = saveGps,
                        onToggle = { saveGps = it }
                    )
                }
            }

            item { SectionHeader(title = "Recording") }

            // Loop Duration (dropdown)
            item {
                SettingCard {
                    Text("Loop duration", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                            placeholder = { Text("Select duration") }
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
                    Text("Crash sensitivity", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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

            item { SectionHeader(title = "Safety") }

            // Emergency Contact
            item {
                SettingCard {
                    Text("Emergency contact", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF8D8DA5),
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(label, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
