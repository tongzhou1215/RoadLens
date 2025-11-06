package com.cs407.roadlens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.cs407.roadlens.ui.theme.RoadLensTheme
import com.cs407.roadlens.navigation.NavPage

class MainActivity : ComponentActivity() {

    private val cameraPermission = Manifest.permission.CAMERA

    private var cameraPermissionGranted by mutableStateOf(false)
    private var hasRequestedCameraPermission by mutableStateOf(false)
    private var cameraPermissionPermanentlyDenied by mutableStateOf(false)

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedCameraPermission = true
            cameraPermissionGranted = granted
            cameraPermissionPermanentlyDenied = !granted && !shouldShowRequestPermissionRationale(cameraPermission)
            if (!granted) {
                Toast.makeText(this, "Camera permission is required to record", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshCameraPermissionState()
        setContent {
            RoadLensTheme {
                val navController = rememberNavController()
                NavPage(
                    navController = navController,
                    cameraPermissionGranted = cameraPermissionGranted,
                    cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                    onRequestCameraPermission = { requestCameraPermission.launch(cameraPermission) },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCameraPermissionState()
    }

    private fun refreshCameraPermissionState() {
        val granted = hasPermission(cameraPermission)
        cameraPermissionGranted = granted
        cameraPermissionPermanentlyDenied =
            !granted && hasRequestedCameraPermission && !shouldShowRequestPermissionRationale(cameraPermission)
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}