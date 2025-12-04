package com.cs407.roadlens.camera

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
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
import androidx.lifecycle.LifecycleService

object DashCamActions {
    const val ACTION_PREPARE = "dc.PREPARE"
    const val ACTION_START   = "dc.START"
    const val ACTION_STOP    = "dc.STOP"
    const val ACTION_CRASH_DETECTED = "dc.CRASH_DETECTED"
    const val ACTION_UPDATE  = "dc.UPDATE_SETTINGS"
    const val EXTRA_SEG_MIN  = "segment_minutes" // legacy
    const val EXTRA_SEG_DURATION_MS = "segment_duration_ms"
    const val EXTRA_AUDIO    = "with_audio"
    const val EXTRA_SENSITIVITY = "sensitivity"
    const val ACTION_SEGMENT_SAVED = "dc.SEGMENT_SAVED"
    const val EXTRA_SEG_URI = "segment_uri"
    const val EXTRA_SEG_NAME = "segment_name"
    const val EXTRA_SEG_STARTED_AT = "segment_started_at"
    const val EXTRA_SEG_IS_ACCIDENT = "segment_is_accident"
}

class DashCamService : LifecycleService() {
    private lateinit var cameraProvider: ProcessCameraProvider
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var segmentDurationMs: Long = 180_000L // default 3 minutes
    private var withAudio = false
    private var loopActive = false
    private var crashSensitivity = CrashSensitivity.MEDIUM

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var segmentStopRunnable: Runnable? = null

    // Sensor & Location properties
    private lateinit var crashDetector: CrashDetector
    private var locationManager: LocationManager? = null
    private val locationListener = LocationListener { location ->
        crashDetector.onLocationUpdate(location)
    }

    // Track metadata for the current segment so we can broadcast it to the album.
    private var currentSegmentName: String? = null
    private var currentSegmentStartedAt: Long = 0L
    private var currentSegmentAccident: Boolean = false

    override fun onCreate() {
        super.onCreate()
        crashDetector = CrashDetector(this) { reason ->
            showToast("Crash detected: $reason")
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
                segmentDurationMs = intent.getLongExtra(
                    DashCamActions.EXTRA_SEG_DURATION_MS,
                    segmentDurationMs
                )
                // Fallback to old extra if present
                if (segmentDurationMs <= 0) {
                    val mins = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, 3)
                    segmentDurationMs = (mins.coerceAtLeast(1)) * 60_000L
                }
                withAudio = intent.getBooleanExtra(DashCamActions.EXTRA_AUDIO, withAudio)
                crashSensitivity = mapFloatToSensitivity(
                    intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, 0.5f)
                )
                loopActive = true
                startLoop()
                startCrashDetection()
            }
            DashCamActions.ACTION_STOP -> {
                stopLoop()
            }
            DashCamActions.ACTION_CRASH_DETECTED -> finalizeCurrentSegment(isAccident = true)
            DashCamActions.ACTION_UPDATE -> {
                val durationUpdate = intent.getLongExtra(DashCamActions.EXTRA_SEG_DURATION_MS, -1L)
                if (durationUpdate > 0) segmentDurationMs = durationUpdate
                val sensitivity = intent.getFloatExtra(DashCamActions.EXTRA_SENSITIVITY, -1f)
                if (sensitivity >= 0f) {
                    crashSensitivity = mapFloatToSensitivity(sensitivity)
                    stopCrashDetection()
                    startCrashDetection()
                }
            }
        }
        return START_STICKY
    }

    private fun prepareCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showToast("Camera permission required to start recording.")
            stopSelf()
            return
        }
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

    // --- Sensor/Location Methods ---
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startCrashDetection() {
        crashDetector.start(crashSensitivity)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
            locationManager = null
        } catch (_: SecurityException) {
            // Permission revoked while running; ignore
        }
    }
    // --- End Sensor/Location Methods ---

    private fun startLoop() {
        if (currentRecording != null) return
        loopActive = true
        startNextSegment()
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNextSegment() {
        if (!loopActive) return
        Log.d("Func Call", "startNextSegment")
        val capture = videoCapture
        if (capture == null) {
            // Camera not ready yet; retry shortly while loop is active.
            segmentStopRunnable?.let { mainHandler.removeCallbacks(it) }
            segmentStopRunnable = Runnable { startNextSegment() }
            mainHandler.postDelayed(segmentStopRunnable!!, 500L)
            return
        }
        val name = "DashCam_${timestamp()}.mp4"
        currentSegmentName = name
        currentSegmentStartedAt = System.currentTimeMillis()
        currentSegmentAccident = false

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DashCam")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val prepared = try {
            capture.output.prepareRecording(this, output)
        } catch (e: Exception) {
            Log.e("DashCamService", "Failed to prepare recording", e)
            showToast("Unable to start recording. Check storage permissions.")
            finalizeCurrentSegment()
            return
        }.apply {
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                this@DashCamService,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (withAudio && hasAudioPermission) {
                withAudioEnabled()
            } else if (withAudio && !hasAudioPermission) {
                showToast("Microphone permission missing; recording without audio.")
            }
        }

        currentRecording = prepared.start(mainExecutor()) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val outputUri = event.outputResults.outputUri
                val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                val savedName = currentSegmentName
                val startedAt = currentSegmentStartedAt
                if (outputUri != null && savedName != null && startedAt != 0L) {
                    sendBroadcast(
                        Intent(DashCamActions.ACTION_SEGMENT_SAVED).apply {
                            setPackage(packageName)
                            putExtra(DashCamActions.EXTRA_SEG_URI, outputUri.toString())
                            putExtra(DashCamActions.EXTRA_SEG_NAME, savedName)
                            putExtra(DashCamActions.EXTRA_SEG_STARTED_AT, startedAt)
                            putExtra(DashCamActions.EXTRA_SEG_DURATION_MS, durationMs)
                            putExtra(DashCamActions.EXTRA_SEG_IS_ACCIDENT, currentSegmentAccident)
                        }
                    )
                }
                if (isRecordingLoopActive()) startNextSegment()
            }
        }

        segmentStopRunnable?.let { mainHandler.removeCallbacks(it) }
        segmentStopRunnable = Runnable { finalizeCurrentSegment() }
        mainHandler.postDelayed(segmentStopRunnable!!, segmentDurationMs)
    }

    private fun finalizeCurrentSegment(isAccident: Boolean = false) {
        segmentStopRunnable?.let { mainHandler.removeCallbacks(it) }
        segmentStopRunnable = null
        currentSegmentAccident = isAccident

        currentRecording?.let {
            it.stop()
            currentRecording = null
        }
        if (isAccident) {
            loopActive = false
            stopCrashDetection()
            showToast("CRITICAL CLIP SAVED (Accident Detected) - recording stopped")
            // TODO: Notify ViewModel about the accident save (e.g., via broadcast/binding)
        }
    }

    private fun stopLoop() {
        loopActive = false
        val wasActive = currentRecording != null
        finalizeCurrentSegment()
        if (wasActive) showToast("Recording saved")
        stopCrashDetection()
    }

    private fun isRecordingLoopActive() = loopActive

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Enable notifications to start dashcam recording.")
            stopSelf()
            return
        }

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
        try {
            startForeground(42, notif)
        } catch (e: Exception) {
            Log.e("DashCamService", "startForeground failed", e)
            showToast("Unable to start recording. Please enable notifications.")
            stopSelf()
        }
    }

    private fun timestamp(): String =
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun mapFloatToSensitivity(value: Float): CrashSensitivity = when {
        value < 0.35f -> CrashSensitivity.LOW
        value < 0.7f -> CrashSensitivity.MEDIUM
        else -> CrashSensitivity.HIGH
    }

    private fun mainExecutor() = ContextCompat.getMainExecutor(this)
}
