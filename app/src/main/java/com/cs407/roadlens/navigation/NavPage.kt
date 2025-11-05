package com.cs407.roadlens.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cs407.roadlens.ui.screen.AlbumScreen
import com.cs407.roadlens.ui.screen.HomeScreen
import com.cs407.roadlens.ui.screen.RecordingRoute
import com.cs407.roadlens.ui.screen.SettingsScreen
import com.cs407.roadlens.viewmodel.AppSettings
import com.cs407.roadlens.viewmodel.RecordingClip
import com.cs407.roadlens.viewmodel.ClipKind
import com.cs407.roadlens.viewmodel.ClipUploadStatus

@Composable
fun NavPage(navController: NavHostController) {
    // Minimal, in-graph state so the app renders without a factory/ViewModel.
    var settings by remember {
        mutableStateOf(
            AppSettings(
                cameraGranted = false,
                locationGranted = false,
                saveGps = true,
                loopDuration = "1 Minute",
                crashSensitivity = 0.5f,
                emergencyContact = ""
            )
        )
    }

    // Example empty album list (structure matches your AlbumScreen)
    var clips by remember { mutableStateOf<List<RecordingClip>>(emptyList()) }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                cameraGranted = settings.cameraGranted,
                onStartRecording = {
                    if (settings.cameraGranted) {
                        navController.navigate("recording")
                    } else {
                        navController.navigate("settings")
                    }
                },
                onViewAlbum = { navController.navigate("album") },
                onSettings = { navController.navigate("settings") }
            )
        }

        composable("recording") {
            RecordingRoute()
        }

        composable("album") {
            AlbumScreen(
                clips = clips,
                onBack = { navController.popBackStack() },
                onDeleteClips = { ids ->
                    clips = clips.filterNot { it.id in ids }
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                currentSettings = settings,
                onBack = { navController.popBackStack() },
                onSave = { newSettings ->
                    settings = newSettings
                    navController.popBackStack()
                }
            )
        }
    }
}
