package com.cs407.roadlens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.cs407.roadlens.ui.theme.RoadLensTheme
import com.cs407.roadlens.navigation.NavPage

class MainActivity : ComponentActivity() {

    // Adjust this list if you also capture audio, etc.
    private val basePermissions = arrayOf(
        Manifest.permission.CAMERA
    )

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantMap ->
            val allGranted = grantMap.values.all { it }
            if (!allGranted) {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
            // Continue to UI regardless; screens can react to missing perms.
            renderUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on 13+ if you use a ForegroundService with a notification.
        val perms = buildList {
            addAll(basePermissions)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        if (perms.all { hasPermission(it) }) {
            renderUi()
        } else {
            requestPermissions.launch(perms)
        }
    }

    private fun renderUi() {
        setContent {
            RoadLensTheme {
                val navController = rememberNavController()
                NavPage(navController = navController)
            }
        }
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
