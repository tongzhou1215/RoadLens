package com.cs407.roadlens.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Global App Settings
data class AppSettings(
    val cameraGranted: Boolean = false, //camera permission
    val locationGranted: Boolean = false, //
    val saveGps: Boolean = false,
    val loopDuration: String = "1 Minute",
    val crashSensitivity: Float = 0.25f,
    val emergencyContact: String = ""
)

class ViewModel : androidx.lifecycle.ViewModel() {
    var settings by mutableStateOf(AppSettings())
        private set

    private val _recordingState = MutableStateFlow(
        RecordingUiState(targetDurationSeconds = parseLoopDuration(settings.loopDuration))
    )
    val recordingState: StateFlow<RecordingUiState> = _recordingState.asStateFlow()

    private val _clips = MutableStateFlow<List<RecordingClip>>(emptyList())
    val clips: StateFlow<List<RecordingClip>> = _clips.asStateFlow()

    private var recordingJob: Job? = null
    private var currentStartTimestamp: Long? = null
    private var recordingContext: Context? = null
    private var initialized = false

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
    fun setEmergencyContact(v: String) { settings = settings.copy(emergencyContact = v) }

    fun ensureInitialized(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { loadStoredClips(appContext) }
            _clips.value = stored
        }
    }

    fun startRecording(context: Context) {
        if (_recordingState.value.isRecording) return
        val targetSeconds = parseLoopDuration(settings.loopDuration)
        val startTimestamp = System.currentTimeMillis()
        currentStartTimestamp = startTimestamp
        recordingContext = context.applicationContext
        _recordingState.value = RecordingUiState(
            isRecording = true,
            elapsedSeconds = 0,
            targetDurationSeconds = targetSeconds,
            autoStopped = false
        )

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                val current = _recordingState.value
                if (!current.isRecording) break
                val updatedElapsed = current.elapsedSeconds + 1
                val updatedState = current.copy(elapsedSeconds = updatedElapsed)
                _recordingState.value = updatedState

                if (updatedElapsed >= updatedState.targetDurationSeconds) {
                    recordingContext?.let { ctx ->
                        stopRecording(ctx, RecordingStopReason.AUTO)
                    } ?: run {
                        _recordingState.value = updatedState.copy(
                            isRecording = false,
                            autoStopped = true
                        )
                    }
                    break
                }
            }
        }
    }

    fun stopRecording(context: Context, reason: RecordingStopReason) {
        val current = _recordingState.value
        if (!current.isRecording) return

        recordingJob?.cancel()
        recordingJob = null

        val elapsed = max(current.elapsedSeconds, 1)
        val startedAt = currentStartTimestamp ?: System.currentTimeMillis()
        val clipKind = if (reason == RecordingStopReason.MANUAL_CLIP) ClipKind.MANUAL else ClipKind.LOOP

        _recordingState.value = current.copy(
            isRecording = false,
            elapsedSeconds = 0,
            autoStopped = reason == RecordingStopReason.AUTO,
            lastSavedClipKind = clipKind
        )

        val clip = RecordingClip(
            id = startedAt,
            fileName = "clip-$startedAt.mp4",
            durationSeconds = elapsed,
            recordedAt = startedAt,
            kind = clipKind,
            uploadStatus = ClipUploadStatus.SYNCED
        )

        currentStartTimestamp = null
        recordingContext = null

        viewModelScope.launch {
            addClipInternal(context.applicationContext, clip)
        }
    }

    fun manualClip(context: Context) {
        stopRecording(context, RecordingStopReason.MANUAL_CLIP)
    }

    fun acknowledgeAutoStop() {
        val current = _recordingState.value
        if (current.autoStopped || current.lastSavedClipKind != null) {
            _recordingState.value = current.copy(autoStopped = false, lastSavedClipKind = null)
        }
    }

    fun deleteClips(context: Context, clipIds: Set<Long>) {
        if (clipIds.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val remaining = _clips.value.filterNot { it.id in clipIds }
            _clips.value = remaining
            withContext(Dispatchers.IO) {
                persistClipList(appContext, remaining)
                deleteClipFiles(appContext, clipIds)
            }
        }
    }

    private suspend fun addClipInternal(context: Context, clip: RecordingClip) {
        _clips.update { clips -> listOf(clip) + clips }
        withContext(Dispatchers.IO) {
            ensureClipFileExists(context, clip)
            persistClipList(context, _clips.value)
        }
    }

    private fun ensureClipFileExists(context: Context, clip: RecordingClip) {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, clip.fileName)
        if (!file.exists()) {
            file.writeBytes(byteArrayOf())
        }
    }

    private fun deleteClipFiles(context: Context, clipIds: Set<Long>) {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            val id = file.name.substringBefore('.').substringAfter("clip-").toLongOrNull()
            if (id != null && id in clipIds) {
                file.delete()
            }
        }
    }

    private fun loadStoredClips(context: Context): List<RecordingClip> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CLIPS, null) ?: return emptyList()
        val array = JSONArray(json)
        val clips = mutableListOf<RecordingClip>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            clips.add(
                RecordingClip(
                    id = obj.getLong("id"),
                    fileName = obj.getString("fileName"),
                    durationSeconds = obj.getInt("duration"),
                    recordedAt = obj.getLong("recordedAt"),
                    kind = ClipKind.valueOf(obj.getString("kind")),
                    uploadStatus = ClipUploadStatus.valueOf(obj.getString("status"))
                )
            )
        }
        return clips
    }

    private fun persistClipList(context: Context, clips: List<RecordingClip>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        clips.forEach { clip ->
            val obj = JSONObject().apply {
                put("id", clip.id)
                put("fileName", clip.fileName)
                put("duration", clip.durationSeconds)
                put("recordedAt", clip.recordedAt)
                put("kind", clip.kind.name)
                put("status", clip.uploadStatus.name)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
    }
}

data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val targetDurationSeconds: Int = 60,
    val autoStopped: Boolean = false,
    val lastSavedClipKind: ClipKind? = null
)

data class RecordingClip(
    val id: Long,
    val fileName: String,
    val durationSeconds: Int,
    val recordedAt: Long,
    val kind: ClipKind,
    val uploadStatus: ClipUploadStatus
)

enum class ClipKind { LOOP, MANUAL }

enum class ClipUploadStatus { LOCAL_ONLY, SYNCED }

enum class RecordingStopReason { MANUAL, MANUAL_CLIP, AUTO }

private fun parseLoopDuration(option: String): Int = when (option) {
    "30 Seconds" -> 30
    "1 Minute" -> 60
    "3 Minutes" -> 3 * 60
    "5 Minutes" -> 5 * 60
    else -> option.toIntOrNull() ?: 60
}

private const val PREFS_NAME = "recording_clips"
private const val KEY_CLIPS = "clips"
private const val RECORDINGS_DIR = "recordings"