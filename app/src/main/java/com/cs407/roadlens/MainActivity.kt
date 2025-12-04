package com.cs407.roadlens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
    private val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    private var cameraPermissionGranted by mutableStateOf(false)
    private var hasRequestedCameraPermission by mutableStateOf(false)
    private var cameraPermissionPermanentlyDenied by mutableStateOf(false)

    private var locationPermissionGranted by mutableStateOf(false)
    private var hasRequestedLocationPermission by mutableStateOf(false)
    private var locationPermissionPermanentlyDenied by mutableStateOf(false)

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedCameraPermission = true
            cameraPermissionGranted = granted
            cameraPermissionPermanentlyDenied = !granted && !shouldShowRequestPermissionRationale(cameraPermission)
            if (!granted) {
                Toast.makeText(this, "Camera permission is required to record", Toast.LENGTH_LONG).show()
            }
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedLocationPermission = true
            locationPermissionGranted = granted
            locationPermissionPermanentlyDenied =
                !granted && !shouldShowRequestPermissionRationale(locationPermission)
            if (!granted) {
                Toast.makeText(this, "Location permission is required to tag GPS data", Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshCameraPermissionState()
        refreshLocationPermissionState()
        requestNotificationPermissionIfNeeded()
        setContent {
            RoadLensTheme {
                val navController = rememberNavController()
                NavPage(
                    navController = navController,
                    cameraPermissionGranted = cameraPermissionGranted,
                    cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                    locationPermissionGranted = locationPermissionGranted,
                    locationPermissionPermanentlyDenied = locationPermissionPermanentlyDenied,
                    onRequestCameraPermission = { requestCameraPermission.launch(cameraPermission) },
                    onRequestLocationPermission = { requestLocationPermission.launch(locationPermission) },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCameraPermissionState()
        refreshLocationPermissionState()
    }

    private fun refreshCameraPermissionState() {
        val granted = hasPermission(cameraPermission)
        cameraPermissionGranted = granted
        cameraPermissionPermanentlyDenied =
            !granted && hasRequestedCameraPermission && !shouldShowRequestPermissionRationale(cameraPermission)
    }

    private fun refreshLocationPermissionState() {
        val granted = hasPermission(locationPermission)
        locationPermissionGranted = granted
        locationPermissionPermanentlyDenied =
            !granted && hasRequestedLocationPermission && !shouldShowRequestPermissionRationale(locationPermission)
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(notificationPermission)
        ) {
            requestNotificationPermission.launch(notificationPermission)
        }
    }

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
