package com.cs407.roadlens.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cs407.roadlens.ui.screen.AlbumScreen
import com.cs407.roadlens.ui.screen.ContactsScreen
import com.cs407.roadlens.ui.screen.HomeScreen
import com.cs407.roadlens.ui.screen.RecordingRoute
import com.cs407.roadlens.ui.screen.SettingsScreen
import com.cs407.roadlens.viewmodel.factories.EmergencyContactViewModelFactory
import com.cs407.roadlens.viewmodel.ViewModel as RLViewModel
import com.cs407.roadlens.viewmodel.EmergencyContactViewModel as ECViewModel

@Composable
fun NavPage(
    navController: NavHostController,
    cameraPermissionGranted: Boolean,
    cameraPermissionPermanentlyDenied: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    // ONE shared app ViewModel
    val vm: RLViewModel = viewModel()
    val ctx = LocalContext.current
    val emergencyContactViewModel: ECViewModel = viewModel(
        factory = EmergencyContactViewModelFactory(ctx.applicationContext)
    )

    // Initialize VM and keep camera permission in sync
    LaunchedEffect(Unit) { vm.ensureInitialized(ctx) }
    LaunchedEffect(cameraPermissionGranted) { vm.setCameraGranted(cameraPermissionGranted) }

    val clips by vm.clips.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                cameraGranted = cameraPermissionGranted,
                cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                onStartRecording = { navController.navigate("recording") },
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAppSettings = onOpenAppSettings,
                onViewAlbum = { navController.navigate("album") },
                onSettings = { navController.navigate("settings") },
                onContacts = { navController.navigate("contacts") }
            )
        }

        composable("recording") {
            RecordingRoute(
                vm = vm,
                onBack = { navController.popBackStack() } // go back to Home
            )
        }

        composable("album") {
            AlbumScreen(
                clips = clips,
                onBack = { navController.popBackStack() },
                onDeleteClips = { ids -> vm.deleteClips(ctx, ids) }
            )
        }

        composable("settings") {
            SettingsScreen(
                currentSettings = vm.settings,
                cameraPermissionGranted = cameraPermissionGranted,
                cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAppSettings = onOpenAppSettings,
                onBack = { navController.popBackStack() },
                onSave = { newSettings ->
                    // Push changes into the VM, then go back
                    vm.setLocationGranted(newSettings.locationGranted)
                    vm.setSaveGps(newSettings.saveGps)
                    vm.setLoopDuration(newSettings.loopDuration)
                    vm.setCrashSensitivity(newSettings.crashSensitivity)
                    navController.popBackStack()
                }
            )
        }

        composable("contacts") {

            ContactsScreen(
                viewModel = emergencyContactViewModel,
                onBack = { navController.popBackStack() },
            )

        }

    }
}
