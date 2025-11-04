package com.cs407.roadlens.camera

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Intent
import android.os.Looper
import android.provider.MediaStore
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
import java.util.logging.Handler

object DashCamActions {
    const val ACTION_PREPARE = "dc.PREPARE"
    const val ACTION_START   = "dc.START"
    const val ACTION_STOP    = "dc.STOP"
    const val ACTION_UPDATE  = "dc.UPDATE_SETTINGS"
    const val EXTRA_SEG_MIN  = "segment_minutes"
    const val EXTRA_AUDIO    = "with_audio"
}

@AndroidEntryPoint
class DashCamService : LifecycleService() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private var segMinutes = 3       // default; updated from ACTION_START/UPDATE
    private var withAudio = false

    override fun onCreate() {
        super.onCreate()
        startInForeground() // notification with actions if you want
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            DashCamActions.ACTION_PREPARE -> prepareCamera()
            DashCamActions.ACTION_START -> {
                segMinutes = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, segMinutes)
                withAudio = intent.getBooleanExtra(DashCamActions.EXTRA_AUDIO, withAudio)
                startLoop()
            }
            DashCamActions.ACTION_STOP -> stopLoop()
            DashCamActions.ACTION_UPDATE -> {
                segMinutes = intent.getIntExtra(DashCamActions.EXTRA_SEG_MIN, segMinutes)
                // apply other updates if needed
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

    private fun startLoop() {
        // If already recording, ignore or restart
        if (currentRecording != null) return
        startNextSegment()
    }

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

    private fun finalizeCurrentSegment() {
        currentRecording?.let {
            // Will trigger VideoRecordEvent.Finalize callback
            it.stop()
            currentRecording = null
        }
    }

    private fun stopLoop() {
        // Stop current, don’t chain next
        val wasActive = currentRecording != null
        finalizeCurrentSegment()
        if (wasActive) showToast("Recording saved")
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

}
