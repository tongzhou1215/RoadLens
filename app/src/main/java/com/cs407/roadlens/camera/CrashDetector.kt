package com.cs407.roadlens.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.getSystemService
import kotlin.math.sqrt

// Callback for when a crash is detected
typealias CrashCallback = () -> Unit

class CrashDetector(
    private val context: Context,
    private val onCrash: CrashCallback
) : SensorEventListener {

    // Configuration derived from user sensitivity (0.0 to 1.0)
    // Low Sensitivity (e.g., 0.25) should mean a high G threshold.
    private var crashGThreshold: Double = 15.0 // ~1.5G by default (in m/s^2)
    private val speedDropThresholdMps = 4.5 // ~10 MPH drop (in m/s)

    // Sensor State
    private val sensorManager: SensorManager = context.getSystemService()!!
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Speed History and G-Force History
    private val recentSpeeds = mutableListOf<Float>() // in m/s
    private var lastAccidentTime: Long = 0
    private val minTimeBetweenAccidentsMs = 5000L // 5 seconds debounce

    fun start(sensitivity: Float) {
        // Adjust threshold: Sensitivity 0.0=3G (30m/s^2), 1.0=1.2G (12m/s^2)
        crashGThreshold = 30.0 - (18.0 * sensitivity)

        accelerometer?.let {
            // Use SENSOR_DELAY_GAME for frequent, but not power-intensive, updates
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        recentSpeeds.clear()
    }

    fun onLocationUpdate(location: Location) {
        val speedMps = location.speed // Android Location returns speed in m/s

        // Update speed history (keep only the last 3 seconds of data)
        recentSpeeds.add(speedMps)
        while (recentSpeeds.size > 30) { // Assuming ~10 updates/sec * 3 seconds
            recentSpeeds.removeAt(0)
        }
    }

    // --- SensorEventListener Implementation ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastAccidentTime < minTimeBetweenAccidentsMs) {
            return // Debounce rapid triggers
        }

        // 1. Calculate the magnitude of the G-force vector
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val totalAcceleration = sqrt(ax*ax + ay*ay + az*az).toDouble()

        // 2. Check against the dynamic threshold
        if (totalAcceleration > crashGThreshold) {

            // Log for debugging: println("High G-Force detected: $totalAcceleration m/s^2")

            // Case A: Speed data available - Use correlation check
            if (recentSpeeds.size >= 5) {
                val maxSpeed = recentSpeeds.maxOrNull() ?: 0f
                val minSpeed = recentSpeeds.minOrNull() ?: 0f
                val speedDrop = maxSpeed - minSpeed

                if (speedDrop > speedDropThresholdMps) {
                    // Confirmed Crash- High G-force combined and significant speed drop
                    lastAccidentTime = now
                    onCrash()
                }
            } else {
                // Case B: No speed data (e.g., GPS not ready) - Use G-force alone if extremely high
                if (totalAcceleration > 25.0) { // more then 2.5G, assume a severe crash
                    lastAccidentTime = now
                    onCrash()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // to be implemented
    }
}