package com.cs407.roadlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.cs407.roadlens.navigation.NavPage
import com.cs407.roadlens.ui.theme.RoadLensTheme   // <-- use this import

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoadLensTheme {                      // <-- wrap your UI with this
                val navController = rememberNavController()
                NavPage(navController)            // Home ↔ Album works here
            }
        }
    }
}
