package com.cs407.roadlens.camera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.MediaStore
import android.location.LocationListener
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.lifecycle.LifecycleService
import android.os.Handler
import android.widget.Toast
import androidx.annotation.RequiresPermission

object DashCamActions {
    const val ACTION_PREPARE = "dc.PREPARE"
    const val ACTION_START   = "dc.START"
    const val ACTION_STOP    = "dc.STOP"
    const val ACTION_CRASH_DETECTED = "dc.CRASH_DETECTED"
    const val ACTION_UPDATE  = "dc.UPDATE_SETTINGS"
    const val EXTRA_SEG_MIN  = "segment_minutes"
    const val EXTRA_AUDIO    = "with_audio"
    const val EXTRA_SENSITIVITY = "sensitivity"
}

class DashCamService : LifecycleService() {
    private lateinit var cameraProvider: ProcessCameraProvider
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var segMinutes = 3
    private var withAudio = false
    private var crashSensitivity = 0.25f

    private lateinit var crashDetector: CrashDetector
    private var locationManager: LocationManager? = null
    private val locationListener = LocationListener { location ->
        crashDetector.onLocationUpdate(location)
    }

    override fun onCreate() {
        super.onCreate()
        crashDetector = CrashDetector(this) {
            startService(
                Intent(this, DashCamService::class.java)
                    .setAction(DashCamActions.ACTION_CRASH_DETECTED)
            )
        }
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            DashCamActions.ACTION_PREPARE -> prepareCamera()
            DashCamActions.ACTION_START -> {
                segMinutes = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, segMinutes)
                withAudio = intent.getBooleanExtra(DashCamActions.EXTRA_AUDIO, withAudio)
                crashSensitivity = intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, crashSensitivity)
                startLoop()
            }
            DashCamActions.ACTION_STOP -> stopLoop()
            DashCamActions.ACTION_CRASH_DETECTED -> finalizeCurrentSegment(isAccident = true)
            DashCamActions.ACTION_UPDATE -> {
                segMinutes = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, segMinutes)
                crashSensitivity = intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, crashSensitivity)
                // Update crash detector immediately
                val sensitivity = when {
                    crashSensitivity <= 0.33f -> CrashSensitivity.LOW
                    crashSensitivity <= 0.66f -> CrashSensitivity.MEDIUM
                    else -> CrashSensitivity.HIGH
                }
                crashDetector.start(sensitivity)
            }
        }
        return START_STICKY
    }

    private fun prepareCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.FHD, Quality.HD),
                        FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                    )
                ).build()
            videoCapture = VideoCapture.withOutput(recorder)

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startCrashDetection() {
        val sensitivity = when {
            crashSensitivity <= 0.33f -> CrashSensitivity.LOW
            crashSensitivity <= 0.66f -> CrashSensitivity.MEDIUM
            else -> CrashSensitivity.HIGH
        }

        crashDetector.start(sensitivity)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                showToast("Location monitoring failed: Permission missing.")
            }
        }
    }

    private fun stopCrashDetection() {
        crashDetector.stop()
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: SecurityException) {
        } finally {
            locationManager = null
        }
    }

    private fun startLoop() {
        if (currentRecording != null) return
        startCrashDetection()
        startNextSegment()
    }

    private fun finalizeCurrentSegment(isAccident: Boolean = false) {
        currentRecording?.let {
            it.stop()
            currentRecording = null
        }
        if (isAccident) {
            showToast("CRITICAL CLIP SAVED (Accident Detected)")
        }
    }

    private fun stopLoop() {
        val wasActive = currentRecording != null
        finalizeCurrentSegment()
        if (wasActive) showToast("Recording saved")
        stopCrashDetection()
    }

    private fun startInForeground() {
        val chanId = "dashcam_rec"
        val chanName = "DashCam Recording"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(chanId, chanName, NotificationManager.IMPORTANCE_LOW)
        )
        val notif = NotificationCompat.Builder(this, chanId)
            .setContentTitle("DashCam ready")
            .setContentText("Waiting for start")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
        startForeground(42, notif)
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun timestamp(): String =
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun mainExecutor() = ContextCompat.getMainExecutor(this)
    private fun mainHandler() = Handler(Looper.getMainLooper())

    private fun startNextSegment() {
        val capture = videoCapture ?: return
        val name = "DashCam_${timestamp()}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DashCam")
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val prepared = capture.output.prepareRecording(this, output).apply {
            if (withAudio) withAudioEnabled()
        }

        currentRecording = prepared.start(mainExecutor()) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (isRecordingLoopActive()) startNextSegment()
            }
        }

        mainHandler().postDelayed({ finalizeCurrentSegment() }, segMinutes * 60_000L)
    }

    private fun isRecordingLoopActive() = true
}
