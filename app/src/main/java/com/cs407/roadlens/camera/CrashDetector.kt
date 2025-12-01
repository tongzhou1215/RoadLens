package com.cs407.roadlens.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.getSystemService
import kotlin.collections.ArrayDeque
import kotlin.math.sqrt

// Crash callback
typealias CrashCallback = (String) -> Unit

enum class CrashSensitivity {
    LOW,      // Least sensitive (requires strong impact)
    MEDIUM,   // Balanced
    HIGH      // Most sensitive (triggers more easily)
}

class CrashDetector(
    private val context: Context,
    private val onCrash: CrashCallback
) : SensorEventListener {

    private data class SensitivityProfile(
        val accelPeak: Double,
        val speedDropMps: Float,
        val requiresSpeedHistory: Boolean,
        val rotationTriggerEnabled: Boolean,
        val rotationTriggerRad: Double
    )

    // Sensitivity thresholds (m/s^2) where ~9.8 ~ 1G
    private val profileMap = mapOf(
        CrashSensitivity.LOW to SensitivityProfile(
            accelPeak = 32.0,          // ~3.2G impact needed
            speedDropMps = 5.5f,       // Require notable speed drop
            requiresSpeedHistory = true,
            rotationTriggerEnabled = false,
            rotationTriggerRad = Double.MAX_VALUE
        ),
        CrashSensitivity.MEDIUM to SensitivityProfile(
            accelPeak = 24.0,          // ~2.4G
            speedDropMps = 3.5f,
            requiresSpeedHistory = false,
            rotationTriggerEnabled = false,
            rotationTriggerRad = Double.MAX_VALUE
        ),
        CrashSensitivity.HIGH to SensitivityProfile(
            accelPeak = 16.0,          // ~1.6G
            speedDropMps = 2.0f,
            requiresSpeedHistory = false,
            rotationTriggerEnabled = true, // enable flip trigger
            rotationTriggerRad = Math.PI   // ~180 degrees
        )
    )

    private var currentProfile: SensitivityProfile = profileMap[CrashSensitivity.MEDIUM]!!

    private val speedDropThresholdMps = 4.5 // ~10 MPH drop
    private val rotationWindowMs = 1200L
    private val minSpinRateRadPerSec = 2.8  // ignore tiny rotations

    private val sensorManager: SensorManager? = context.getSystemService()
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val recentSpeeds = mutableListOf<Float>()
    private var lastAccidentTime: Long = 0
    private val minTimeBetweenAccidentsMs = 5000L // debounce

    private var lastGyroTimestampNs: Long = 0L
    private var rotationWindowStartMs: Long = 0L
    private var cumulativeRotationRad: Double = 0.0
    private val accelSamples: ArrayDeque<Pair<Long, Double>> = ArrayDeque()
    private val accelWindowMs = 200L // small window to capture short spikes

    /**
     * Start crash detection with LOW, MEDIUM, or HIGH sensitivity.
     */
    fun start(sensitivity: CrashSensitivity) {
        val manager = sensorManager ?: return

        currentProfile = profileMap[sensitivity] ?: currentProfile

        accelerometer?.let {
            manager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        gyroscope?.let {
            manager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        recentSpeeds.clear()
        lastGyroTimestampNs = 0L
        rotationWindowStartMs = 0L
        cumulativeRotationRad = 0.0
        accelSamples.clear()
    }

    fun onLocationUpdate(location: Location) {
        val speedMps = location.speed

        recentSpeeds.add(speedMps)
        while (recentSpeeds.size > 30) {
            recentSpeeds.removeAt(0)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastAccidentTime < minTimeBetweenAccidentsMs) return

        when (event.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event, nowMs)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event, nowMs)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleAccelerometer(event: SensorEvent, nowMs: Long) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val totalAcceleration = sqrt(ax * ax + ay * ay + az * az).toDouble()
        val profile = currentProfile

        // Maintain a short sliding window to filter noise and catch sharp spikes.
        accelSamples.addLast(nowMs to totalAcceleration)
        while (accelSamples.isNotEmpty() && nowMs - accelSamples.first().first > accelWindowMs) {
            accelSamples.removeFirst()
        }
        val peakAccel = accelSamples.maxOfOrNull { it.second } ?: totalAcceleration

        if (peakAccel > profile.accelPeak) {
            val hasSpeedHistory = recentSpeeds.size >= 3
            val maxSpeed = recentSpeeds.maxOrNull() ?: 0f
            val minSpeed = recentSpeeds.minOrNull() ?: 0f
            val speedDrop = maxSpeed - minSpeed
            val largeImpact = peakAccel > profile.accelPeak * 1.2

            val speedGate = when {
                profile.requiresSpeedHistory && !hasSpeedHistory -> false
                else -> speedDrop > profile.speedDropMps || speedDrop > speedDropThresholdMps / 2
            }

            if (speedGate || largeImpact) {
                triggerCrash(
                    nowMs,
                    "Impact spike detected (${String.format("%.1f", peakAccel / 9.81)}g)"
                )
            }
        }
    }

    private fun handleGyroscope(event: SensorEvent, nowMs: Long) {
        val timestampNs = event.timestamp
        if (lastGyroTimestampNs == 0L) {
            lastGyroTimestampNs = timestampNs
            rotationWindowStartMs = nowMs
            return
        }

        val dtSeconds = (timestampNs - lastGyroTimestampNs) / 1_000_000_000.0
        lastGyroTimestampNs = timestampNs

        val wx = event.values[0].toDouble()
        val wy = event.values[1].toDouble()
        val wz = event.values[2].toDouble()
        val omegaMagnitude = sqrt(wx * wx + wy * wy + wz * wz)

        if (omegaMagnitude < minSpinRateRadPerSec) return
        if (!currentProfile.rotationTriggerEnabled) return

        if (rotationWindowStartMs == 0L) {
            rotationWindowStartMs = nowMs
            cumulativeRotationRad = 0.0
        }

        if (nowMs - rotationWindowStartMs > rotationWindowMs) {
            rotationWindowStartMs = nowMs
            cumulativeRotationRad = 0.0
        }

        cumulativeRotationRad += omegaMagnitude * dtSeconds

        if (cumulativeRotationRad >= currentProfile.rotationTriggerRad) {
            triggerCrash(nowMs, "Device rotation exceeded 180 deg")
        }
    }

    private fun triggerCrash(nowMs: Long, reason: String) {
        lastAccidentTime = nowMs
        rotationWindowStartMs = nowMs
        cumulativeRotationRad = 0.0
        onCrash(reason)
    }
}
