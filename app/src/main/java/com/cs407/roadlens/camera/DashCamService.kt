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
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission

object DashCamActions {
    const val ACTION_PREPARE = "dc.PREPARE"
    const val ACTION_START   = "dc.START"
    const val ACTION_STOP    = "dc.STOP"
    const val ACTION_CRASH_DETECTED = "dc.CRASH_DETECTED" // <-- NEW
    const val ACTION_UPDATE  = "dc.UPDATE_SETTINGS"
    const val EXTRA_SEG_MIN  = "segment_minutes"
    const val EXTRA_AUDIO    = "with_audio"
    const val EXTRA_SENSITIVITY = "sensitivity" // <-- NEW
}

class DashCamService : LifecycleService() {
    private lateinit var cameraProvider: ProcessCameraProvider
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var segMinutes = 3
    private var withAudio = false
    private var loopActive = false
    private var crashSensitivity = 0.25f // <-- NEW

    // Sensor & Location properties
    private lateinit var crashDetector: CrashDetector // <-- NEW
    private var locationManager: LocationManager? = null // <-- NEW
    private val locationListener = LocationListener { location -> // <-- NEW
        crashDetector.onLocationUpdate(location)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize the Crash Detector, which will call us back via a service intent
        crashDetector = CrashDetector(this) {
            // Crash confirmed: immediately send an action to self to finalize the clip
            startService(Intent(this, DashCamService::class.java).setAction(DashCamActions.ACTION_CRASH_DETECTED))
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
                crashSensitivity = intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, crashSensitivity) // <-- NEW
                startLoop()
                startCrashDetection() // <-- NEW
            }
            DashCamActions.ACTION_STOP -> {
                stopLoop()
                stopCrashDetection() // <-- NEW
            }
            DashCamActions.ACTION_CRASH_DETECTED -> finalizeCurrentSegment(isAccident = true) // <-- NEW
            DashCamActions.ACTION_UPDATE -> {
                segMinutes = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, segMinutes)
                // apply other updates if needed
                // If crash sensitivity changes, update the detector immediately
                // crashSensitivity = intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, crashSensitivity)
                // crashDetector.start(crashSensitivity)
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

            // Bind to lifecycle (no UI preview needed for backend)
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    // --- Sensor/Location Methods ---
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startCrashDetection() {
        // Start Accelerometer monitoring
        crashDetector.start(crashSensitivity)

        // Start GPS speed monitoring if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                // Request updates every 1 second, with minimal distance change (1m)
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener,
                    Looper.getMainLooper() // Specify Looper for callbacks
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
            locationManager = null
        } catch (e: SecurityException) {
            // Ignore if permission was revoked while running
        }
    }
    // --- End Sensor/Location Methods ---


    private fun startLoop() {
        // If already recording, ignore or restart
        if (currentRecording != null) return
        startCrashDetection() // Ensure crash detection is running
        startNextSegment()
    }


    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNextSegment() {
        Log.d("Func Call", "startNextSegment")
        val capture = videoCapture ?: return
        val name = "DashCam_${timestamp()}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DashCam")
            // TODO: Add GPS coordinates here if 'saveGps' is enabled
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val prepared = capture.output.prepareRecording(this, output).apply {
            if (withAudio) withAudioEnabled()
        }

        // Start recording and schedule a stop at X minutes
        currentRecording = prepared.start(mainExecutor()) { event ->
            if (event is VideoRecordEvent.Finalize) {
                // Segment saved. Start the next one if still in loop mode.
                if (isRecordingLoopActive()) startNextSegment()
            }
        }

        // Stop current segment after segMinutes
        mainHandler().postDelayed({ finalizeCurrentSegment() }, segMinutes * 60_000L)
    }

    private fun finalizeCurrentSegment(isAccident: Boolean = false) { // <-- UPDATED
        currentRecording?.let {
            // Will trigger VideoRecordEvent.Finalize callback
            it.stop()
            currentRecording = null
        }
        if (isAccident) {
            showToast("CRITICAL CLIP SAVED (Accident Detected)")
            // TODO: Notify ViewModel about the accident save (e.g., via broadcast/binding)
        }
    private fun finalizeCurrentSegment() {
        currentRecording?.stop()
    }

    private fun stopLoop() {
        // Stop current, don’t chain next
        val wasActive = currentRecording != null
        finalizeCurrentSegment()
        if (wasActive) showToast("Recording saved")
        stopCrashDetection() // Ensure sensors stop when loop stops
    }

    private fun isRecordingLoopActive() = true // flip to false in stopLoop() if you add a flag

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

    private fun timestamp(): String =
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun mainExecutor() = ContextCompat.getMainExecutor(this)
    private fun mainHandler() = Handler(Looper.getMainLooper())
}

