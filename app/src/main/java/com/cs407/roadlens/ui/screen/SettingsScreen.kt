package com.cs407.roadlens.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.roadlens.viewmodel.AppSettings
import kotlin.math.abs

// --- Sensitivity Mapping ---
private val sensitivityMap = mapOf(
    "Low" to 0.2f,    // Less likely to trigger (Requires higher G-force)
    "Normal" to 0.5f, // Standard setting
    "High" to 0.8f    // More likely to trigger (Lower G-force threshold)
)

private fun floatToSensitivityString(value: Float): String {
    // Finds the closest string option to the saved float value
    return sensitivityMap.minByOrNull { (_, f) -> abs(f - value) }?.key ?: "Normal"
}
// ---------------------------

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

    // --- UPDATED STATE: Use String for display ---
    var crashSensitivityString by rememberSaveable {
        mutableStateOf(floatToSensitivityString(sanitizedCurrent.crashSensitivity))
    }

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

    // 如果外部 currentSettings 变化，同步表单
    LaunchedEffect(sanitizedCurrent) {
        grantLocation = sanitizedCurrent.locationGranted
        saveGps = sanitizedCurrent.saveGps
        crashSensitivityString = floatToSensitivityString(sanitizedCurrent.crashSensitivity) // Sync new state
        loopDuration = sanitizedCurrent.loopDuration.takeIf { it in durationOptions } ?: durationOptions.first()
    }

    val pendingSettings = remember(
        grantLocation,
        saveGps,
        loopDuration,
        crashSensitivityString, // Depend on string state
        cameraPermissionGranted
    ) {
        AppSettings(
            cameraGranted = cameraPermissionGranted,
            locationGranted = grantLocation,
            saveGps = saveGps,
            loopDuration = loopDuration,
            // --- CONVERT STRING BACK TO FLOAT FOR MODEL ---
            crashSensitivity = sensitivityMap[crashSensitivityString] ?: 0.5f
            // ----------------------------------------------
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

            // --- UPDATED: Crash Sensitivity (Discrete Control) ---
            item {
                SettingCard {
                    Text("Crash sensitivity", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))

                    val options = listOf("Low", "Normal", "High")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min) // Required for vertical dividers/equal heights
                            .background(Color.White, RoundedCornerShape(12.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        options.forEachIndexed { index, option ->
                            val isSelected = option == crashSensitivityString
                            SensitivityOption(
                                label = option,
                                isSelected = isSelected,
                                onClick = { crashSensitivityString = option },
                                isFirst = index == 0,
                                isLast = index == options.lastIndex
                            )
                            if (index < options.lastIndex) {
                                Divider(
                                    color = Color(0xFFE0E0E0),
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (crashSensitivityString) {
                            "High" -> "High sensitivity is more likely to auto-save, even during hard braking."
                            "Low" -> "Low sensitivity requires a major impact before auto-saving a critical clip."
                            else -> "Normal sensitivity balances false alarms against accident detection."
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            // -----------------------------------------------------

            item { Spacer(Modifier.height(64.dp)) } // 给底部按钮留空间
        }
    }
}

// --- NEW COMPOSABLE FOR SEGMENTED CONTROL OPTION ---
@Composable
private fun RowScope.SensitivityOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val cornerShape = when {
        isFirst -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
        isLast -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = cornerShape,
        tonalElevation = 0.dp,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}
// --------------------------------------------------

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
