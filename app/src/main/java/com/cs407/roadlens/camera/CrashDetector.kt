package com.cs407.roadlens.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.getSystemService
import kotlin.math.sqrt

// Crash callback
typealias CrashCallback = () -> Unit

// Three crash sensitivity modes
enum class CrashSensitivity {
    LOW,      // Least sensitive (requires strong impact)
    MEDIUM,   // Balanced
    HIGH      // Most sensitive (triggers more easily)
}

class CrashDetector(
    private val context: Context,
    private val onCrash: CrashCallback
) : SensorEventListener {

    // Sensitivity thresholds (m/s^2)
    private val lowThreshold = 28.0    // ~2.8G
    private val mediumThreshold = 20.0 // ~2.0G
    private val highThreshold = 14.0   // ~1.4G

    private var crashGThreshold: Double = mediumThreshold

    private val speedDropThresholdMps = 4.5 // ~10 MPH drop

    private val sensorManager: SensorManager = context.getSystemService()!!
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val recentSpeeds = mutableListOf<Float>()
    private var lastAccidentTime: Long = 0
    private val minTimeBetweenAccidentsMs = 5000L // debounce

    /**
     * Start crash detection with LOW, MEDIUM, or HIGH sensitivity.
     */
    fun start(sensitivity: CrashSensitivity) {
        crashGThreshold = when (sensitivity) {
            CrashSensitivity.LOW -> lowThreshold
            CrashSensitivity.MEDIUM -> mediumThreshold
            CrashSensitivity.HIGH -> highThreshold
        }

        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        recentSpeeds.clear()
    }

    fun onLocationUpdate(location: Location) {
        val speedMps = location.speed
        recentSpeeds.add(speedMps)
        while (recentSpeeds.size > 30) {
            recentSpeeds.removeAt(0)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastAccidentTime < minTimeBetweenAccidentsMs) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val totalAcceleration = sqrt(ax * ax + ay * ay + az * az).toDouble()

        if (totalAcceleration > crashGThreshold) {

            // Case A — speed data available
            if (recentSpeeds.size >= 5) {
                val maxSpeed = recentSpeeds.maxOrNull() ?: 0f
                val minSpeed = recentSpeeds.minOrNull() ?: 0f
                val speedDrop = maxSpeed - minSpeed

                if (speedDrop > speedDropThresholdMps) {
                    lastAccidentTime = now
                    onCrash()
                }

            } else {
                // Case B — No speed data, rely on an extreme G spike
                if (totalAcceleration > 25.0) {
                    lastAccidentTime = now
                    onCrash()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
