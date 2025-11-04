package com.cs407.roadlens.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

import com.cs407.roadlens.ui.screen.HomeScreen
import com.cs407.roadlens.ui.screen.AlbumScreen
import com.cs407.roadlens.ui.screen.RecordingScreen
import com.cs407.roadlens.ui.screen.SettingsScreen

import com.cs407.roadlens.viewmodel.ViewModel as AppVM

/**
 * Navigation graph for Home / Album / Recording / Settings.
 * Enforces: user must grant camera in Settings before entering Recording.
 */
@Composable
fun NavPage(navController: NavHostController) {
    // Activity-scoped shared ViewModel (holds cameraGranted, etc.)
    val appVm: AppVM = viewModel<AppVM>()

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
                onBack = { navController.popBackStack() }
            )
        }

        // SETTINGS
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSave = { ui ->
                    // Write back to your global ViewModel
                    appVm.setCameraGranted(ui.grantCamera)
                    appVm.setLocationGranted(ui.grantLocation)
                    appVm.setSaveGps(ui.saveGps)
                    appVm.setLoopDuration(ui.loopDuration)
                    appVm.setCrashSensitivity(ui.crashSensitivity)
                    appVm.setEmergencyContact(ui.emergencyContact)

                    // Go back after saving
                    navController.popBackStack()
                }
            )
        }

        // RECORDING (button states & hints depend on cameraGranted)
        composable("recording") {
            RecordingScreen(
                cameraAllowed = appVm.settings.cameraGranted,
                accelActive = true,
                onToggleRecording = { /* TODO: hook CameraX later */ },
                onManualSave = { /* TODO: protect/save current clip */ },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
