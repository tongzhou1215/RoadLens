package com.cs407.roadlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cs407.roadlens.ui.screen.HomeScreen
import com.cs407.roadlens.ui.screen.AlbumScreen
import com.cs407.roadlens.ui.screen.RecordingScreen
import com.cs407.roadlens.ui.screen.SettingsScreen
import com.cs407.roadlens.viewmodel.AppSettings
import com.cs407.roadlens.viewmodel.RecordingStopReason
import com.cs407.roadlens.viewmodel.ViewModel as AppVM

/**
 * Navigation graph for Home / Album / Recording / Settings.
 * Uses AppSettings as the SettingsScreen save payload (no SettingsUiState).
 */
@Composable
fun NavPage(navController: NavHostController) {
    val appVm: AppVM = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        appVm.ensureInitialized(context)
    }

    val recordingState by appVm.recordingState.collectAsState()
    val clips by appVm.clips.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // HOME
        composable("home") {
            HomeScreen(
                cameraGranted = appVm.settings.cameraGranted,
                onStartRecording = {
                    if (appVm.settings.cameraGranted) {
                        navController.navigate("recording")
                    } else {
                        navController.navigate("settings")
                    }
                },
                onViewAlbum = { navController.navigate("album") },
                onSettings = { navController.navigate("settings") }
            )
        }

        // ALBUM
        composable("album") {
            AlbumScreen(
                clips = clips,
                onBack = { navController.popBackStack() },
                onDeleteClips = { ids -> appVm.deleteClips(context, ids) }
            )
        }

        // SETTINGS (save returns AppSettings)
        composable("settings") {
            SettingsScreen(
                currentSettings = appVm.settings,
                onBack = { navController.popBackStack() },
                onSave = { newSettings: AppSettings ->
                    appVm.setCameraGranted(newSettings.cameraGranted)
                    appVm.setLocationGranted(newSettings.locationGranted)
                    appVm.setSaveGps(newSettings.saveGps)
                    appVm.setLoopDuration(newSettings.loopDuration)
                    appVm.setCrashSensitivity(newSettings.crashSensitivity)
                    appVm.setEmergencyContact(newSettings.emergencyContact)
                    navController.popBackStack()
                }
            )
        }

        // RECORDING
        composable("recording") {
            RecordingScreen(
                uiState = recordingState,
                cameraAllowed = appVm.settings.cameraGranted,
                accelActive = true,
                onToggleRecording = {
                    if (recordingState.isRecording) {
                        appVm.stopRecording(context, RecordingStopReason.MANUAL)
                    } else {
                        appVm.startRecording(context)
                    }
                },
                onManualSave = {
                    if (recordingState.isRecording) {
                        appVm.manualClip(context)
                    }
                },
                onAutoStopAcknowledged = { appVm.acknowledgeAutoStop() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
