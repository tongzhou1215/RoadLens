package com.cs407.roadlens.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// Global App Settings
data class AppSettings(
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val saveGps: Boolean = false,
    val loopDuration: String = "1 Minute",
    val crashSensitivity: Float = 0.25f,
    val emergencyContact: String = ""
)

class ViewModel : androidx.lifecycle.ViewModel() {
    var settings by mutableStateOf(AppSettings())
        private set

    fun setCameraGranted(v: Boolean) { settings = settings.copy(cameraGranted = v) }
    fun setLocationGranted(v: Boolean) { settings = settings.copy(locationGranted = v) }
    fun setSaveGps(v: Boolean) { settings = settings.copy(saveGps = v) }
    fun setLoopDuration(v: String) { settings = settings.copy(loopDuration = v) }
    fun setCrashSensitivity(v: Float) { settings = settings.copy(crashSensitivity = v) }
    fun setEmergencyContact(v: String) { settings = settings.copy(emergencyContact = v) }
}
