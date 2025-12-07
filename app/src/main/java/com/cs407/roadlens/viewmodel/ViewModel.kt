package com.cs407.roadlens.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.roadlens.camera.DashCamActions
import com.cs407.roadlens.camera.DashCamService
import com.cs407.roadlens.data.local.entities.EmergencyContact
import com.cs407.roadlens.data.repository.EmergencyContactRepository
import com.cs407.roadlens.message.EmergencyMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

// Global App Settings
data class AppSettings(
    val cameraGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val saveGps: Boolean = false,
    val loopDuration: String = "30 Seconds",
    val crashSensitivity: Float = 0.25f
)

// UI state for recording
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val targetDurationSeconds: Int = 60,
    val autoStopped: Boolean = false,
    val lastSavedClipKind: ClipKind? = null
)

enum class ClipKind { LOOP, MANUAL, ACCIDENT }
enum class RecordingStopReason { MANUAL, MANUAL_CLIP, AUTO, ACCIDENT }

// Always use 30-second segments for dashcam loops
private fun parseLoopDuration(option: String): Int = when (option) {
    "30 Seconds" -> 30
    "1 Minute" -> 60
    "3 Minutes" -> 180
    "5 Minutes" -> 300
    else -> 30
}

// Holds settings and Recording UI state
private fun dashcamPrepare(context: Context) {
    startForegroundService(
        context,
        Intent(context, DashCamService::class.java).setAction(DashCamActions.ACTION_PREPARE)
    )
}

private fun dashcamStart(context: Context, segmentDurationMs: Long, withAudio: Boolean, crashSensitivity: Float) {
    dashcamPrepare(context)
    startForegroundService(
        context,
        Intent(context, DashCamService::class.java)
            .setAction(DashCamActions.ACTION_START)
            .putExtra(DashCamActions.EXTRA_SEG_DURATION_MS, segmentDurationMs)
            .putExtra(DashCamActions.EXTRA_AUDIO, withAudio)
            .putExtra(DashCamActions.EXTRA_SENSITIVITY, crashSensitivity)
    )
}

private fun dashcamStop(context: Context) {
    context.startService(
        Intent(context, DashCamService::class.java).setAction(DashCamActions.ACTION_STOP)
    )
}

//Holds Settings state; Exposes Recording UI state + list of local clips
class ViewModel : ViewModel() {

    var settings by mutableStateOf(AppSettings())
        private set

    private val _recordingState = MutableStateFlow(
        RecordingUiState(targetDurationSeconds = parseLoopDuration(settings.loopDuration))
    )
    val recordingState: StateFlow<RecordingUiState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null
    private var currentStartTimestamp: Long? = null
    private var recordingContext: Context? = null
    private var continueLoop = false

    // ---- Settings setters ----
    fun setCameraGranted(v: Boolean) { settings = settings.copy(cameraGranted = v) }
    fun setLocationGranted(v: Boolean) { settings = settings.copy(locationGranted = v) }
    fun setSaveGps(v: Boolean) { settings = settings.copy(saveGps = v) }

    fun setLoopDuration(v: String) {
        settings = settings.copy(loopDuration = v)
        val seconds = parseLoopDuration(v)
        _recordingState.update { state ->
            state.copy(targetDurationSeconds = seconds)
        }
    }

    fun setCrashSensitivity(v: Float) { settings = settings.copy(crashSensitivity = v) }

    // ---- Recording control (looped segmentation) ----
    fun startRecording(context: Context) {
        if (_recordingState.value.isRecording) return
        if (!settings.cameraGranted) {
            Toast.makeText(
                context,
                "Camera permission is required to start recording.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context)
        ) {
            Toast.makeText(
                context,
                "Please enable notifications to start the dashcam.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        continueLoop = true
        beginRecordingSegment(context.applicationContext)
    }

    private fun beginRecordingSegment(context: Context) {
        recordingJob?.cancel()
        recordingJob = null

        val targetSeconds = parseLoopDuration(settings.loopDuration)
        val segmentDurationMs = targetSeconds.coerceAtLeast(1) * 1000L
        val audioOk = hasRecordAudioPermission(context)
        val useAudio = settings.cameraGranted && audioOk
        if (settings.cameraGranted && !audioOk) {
            Toast.makeText(
                context,
                "Microphone permission not granted; recording without audio.",
                Toast.LENGTH_LONG
            ).show()
        }
        val startTimestamp = System.currentTimeMillis()

        currentStartTimestamp = startTimestamp
        recordingContext = context.applicationContext

        dashcamStart(
            context.applicationContext,
            segmentDurationMs = segmentDurationMs,
            withAudio = useAudio,
            crashSensitivity = settings.crashSensitivity
        )

        _recordingState.value = RecordingUiState(
            isRecording = true,
            elapsedSeconds = 0,
            targetDurationSeconds = targetSeconds,
            autoStopped = false,
            lastSavedClipKind = null
        )

        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                val current = _recordingState.value
                if (!current.isRecording) break

                val updatedElapsed = current.elapsedSeconds + 1
                val target = current.targetDurationSeconds
                _recordingState.value = current.copy(elapsedSeconds = updatedElapsed)

                if (updatedElapsed >= target) {
                    completeSegment(context, RecordingStopReason.AUTO, restart = true)
                    break
                }
            }
        }
    }

    private fun completeSegment(
        context: Context,
        reason: RecordingStopReason,
        restart: Boolean
    ) {
        dashcamStop(context.applicationContext)

        recordingJob?.cancel()
        recordingJob = null

        val current = _recordingState.value
        val elapsed = max(current.elapsedSeconds, 1)
        val startedAt = currentStartTimestamp ?: System.currentTimeMillis()

        val clipKind = when (reason) {
            RecordingStopReason.MANUAL_CLIP -> ClipKind.MANUAL
            RecordingStopReason.ACCIDENT -> ClipKind.ACCIDENT
            else -> ClipKind.LOOP
        }

        val showAutoStopped = reason == RecordingStopReason.AUTO && !restart
        val lastKind = if (restart) null else clipKind

        _recordingState.value = current.copy(
            isRecording = false,
            elapsedSeconds = 0,
            autoStopped = showAutoStopped,
            lastSavedClipKind = lastKind
        )

        currentStartTimestamp = null
        recordingContext = null

        if (restart && continueLoop) {
            viewModelScope.launch {
                // Small pause to let the service finalize before restarting.
                delay(250)
                if (continueLoop) {
                    beginRecordingSegment(context)
                }
            }
        }
    }


    fun stopRecording(context: Context, reason: RecordingStopReason) {
        if (!_recordingState.value.isRecording) return
        continueLoop = false
        completeSegment(context, reason, restart = false)
    }

    fun manualClip(context: Context) {
        stopRecording(context, RecordingStopReason.MANUAL_CLIP)
    }

    fun acknowledgeAutoStop() {
        val current = _recordingState.value
        if (current.autoStopped || current.lastSavedClipKind != null) {
            _recordingState.value = current.copy(
                autoStopped = false,
                lastSavedClipKind = null
            )
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED


}

class EmergencyContactViewModel(val repo: EmergencyContactRepository) : ViewModel() {

    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var isLoaded by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val contact = repo.getContact()
            if (contact != null) {
                name = contact.name
                phone = contact.phone
            }
            isLoaded = true
        }
    }

    fun saveContact() {
        viewModelScope.launch {
            repo.saveContact(
                EmergencyContact(
                    id = 1,
                    name = name,
                    phone = phone
                )
            )
        }
    }

    fun clearChanges(original: EmergencyContact?) {
        name = original?.name ?: ""
        phone = original?.phone ?: ""
    }

    suspend fun reload() {
        val contact = repo.getContact()
        clearChanges(contact)
    }

}

class EmergencyMessageViewModel() : ViewModel() {

    fun sendCrashDetectedSms(
        context: Context,
        phone: String,
        name: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        Log.e("MESSAGE_TO",phone)
        val locationText = if (latitude != null && longitude != null) {
            " at Lat: ${"%.5f".format(latitude)}, Lon: ${"%.5f".format(longitude)}"
        } else {
            " at Lat: , Lon: "
        }
        EmergencyMessage.sendSms(
            context = context,
            phone = phone,
            message = "$name was in a crash$locationText!"
        )
    }

}

