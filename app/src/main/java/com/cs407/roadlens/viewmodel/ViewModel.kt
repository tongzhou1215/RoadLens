package com.cs407.roadlens.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

data class RecordingClip(
    val id: Long,
    val fileName: String,
    val durationSeconds: Int,
    val recordedAt: Long,
    val kind: ClipKind,
    val uploadStatus: ClipUploadStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mediaUri: String? = null
)

enum class ClipKind { LOOP, MANUAL, ACCIDENT }
enum class ClipUploadStatus { LOCAL_ONLY, SYNCED }
enum class RecordingStopReason { MANUAL, MANUAL_CLIP, AUTO, ACCIDENT }

// Always use 30-second segments for dashcam loops
private fun parseLoopDuration(option: String): Int = when (option) {
    "30 Seconds" -> 30
    "1 Minute" -> 60
    "3 Minutes" -> 180
    "5 Minutes" -> 300
    else -> 30
}

private const val PREFS_NAME = "recording_clips"
private const val KEY_CLIPS = "clips"
private const val RECORDINGS_DIR = "recordings"
private const val MAX_CLIPS = 5

// Holds settings, exposes Recording UI state + list of local clips
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

    private val _clips = MutableStateFlow<List<RecordingClip>>(emptyList())
    val clips: StateFlow<List<RecordingClip>> = _clips.asStateFlow()

    private var recordingJob: Job? = null
    private var currentStartTimestamp: Long? = null
    private var recordingContext: Context? = null
    private var initialized = false
    private var continueLoop = false
    private var appContext: Context? = null
    private var segmentReceiverRegistered = false

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

    // ---- Initialization for clips ----
    fun ensureInitialized(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        this.appContext = appContext
        registerSegmentReceiver(appContext)
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { loadStoredClips(appContext) }
            val trimmed = stored.take(MAX_CLIPS)
            _clips.value = trimmed

            // Remove anything older than our retained window to keep storage bounded.
            val toDelete = stored.drop(MAX_CLIPS).map { it.id }.toSet()
            if (toDelete.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    deleteClipFiles(appContext, toDelete)
                    persistClipList(appContext, trimmed)
                }
            }
        }
    }

    private fun registerSegmentReceiver(context: Context) {
        if (segmentReceiverRegistered) return
        val filter = IntentFilter(DashCamActions.ACTION_SEGMENT_SAVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                segmentReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(segmentReceiver, filter)
        }
        segmentReceiverRegistered = true
    }

    private val segmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action != DashCamActions.ACTION_SEGMENT_SAVED) return
            val uri = intent.getStringExtra(DashCamActions.EXTRA_SEG_URI) ?: return
            val name = intent.getStringExtra(DashCamActions.EXTRA_SEG_NAME) ?: return
            val startedAt = intent.getLongExtra(DashCamActions.EXTRA_SEG_STARTED_AT, 0L)
            if (startedAt == 0L) return
            val durationMs = intent.getLongExtra(DashCamActions.EXTRA_SEG_DURATION_MS, 0L)
            val isAccident = intent.getBooleanExtra(DashCamActions.EXTRA_SEG_IS_ACCIDENT, false)
            val durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
            val ctx = context ?: appContext
            val coords = ctx?.let {
                if (settings.saveGps && settings.locationGranted) getLastKnownLocation(it) else null
            }
            val clip = RecordingClip(
                id = startedAt,
                fileName = name,
                durationSeconds = durationSeconds,
                recordedAt = startedAt,
                kind = if (isAccident) ClipKind.ACCIDENT else ClipKind.LOOP,
                uploadStatus = ClipUploadStatus.LOCAL_ONLY,
                latitude = coords?.first,
                longitude = coords?.second,
                mediaUri = uri
            )
            viewModelScope.launch { addClipInternal(ctx ?: return@launch, clip) }
        }
    }

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

    fun onAccidentDetected(context: Context) {
        if (!_recordingState.value.isRecording) return

        context.startService(
            Intent(context, DashCamService::class.java)
                .setAction(DashCamActions.ACTION_CRASH_DETECTED)
        )

        stopRecording(context, RecordingStopReason.ACCIDENT)
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

    // ---- Clips management ----
    fun deleteClips(context: Context, clipIds: Set<Long>) {
        if (clipIds.isEmpty()) return
        val appContext = context.applicationContext
        val toDelete = _clips.value.filter { it.id in clipIds }
        viewModelScope.launch {
            val remaining = _clips.value.filterNot { it.id in clipIds }
            _clips.value = remaining
            withContext(Dispatchers.IO) {
                persistClipList(appContext, remaining)
                deleteClipFiles(appContext, clipIds)
                deleteMediaStoreEntries(appContext, toDelete)
            }
        }
    }

    private suspend fun addClipInternal(context: Context, clip: RecordingClip) {
        var evicted: List<RecordingClip> = emptyList()
        _clips.update { current ->
            val updated = listOf(clip) + current.filterNot { it.id == clip.id }
            if (updated.size > MAX_CLIPS) {
                evicted = updated.drop(MAX_CLIPS)
                updated.take(MAX_CLIPS)
            } else {
                updated
            }
        }
        withContext(Dispatchers.IO) {
            ensureClipFileExists(context, clip)
            persistClipList(context, _clips.value)
            if (evicted.isNotEmpty()) {
                deleteClipFiles(context, evicted.map { it.id }.toSet())
            }
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

    private fun deleteMediaStoreEntries(context: Context, clips: List<RecordingClip>) {
        clips.forEach { clip ->
            val uri = clip.mediaUri?.let { Uri.parse(it) }
            if (uri != null) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: SecurityException) {
                    // On Android 11+ user may need to confirm delete; ignore failures here.
                }
            } else {
                // Best-effort fallback: delete by display name in Movies/DashCam
                try {
                    val where = "${MediaStore.Video.Media.DISPLAY_NAME}=?"
                    val args = arrayOf(clip.fileName)
                    context.contentResolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, where, args)
                } catch (_: SecurityException) {
                    // Ignore failure
                }
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
                    uploadStatus = ClipUploadStatus.valueOf(obj.getString("status")),
                    latitude = if (obj.has("lat")) obj.getDouble("lat") else null,
                    longitude = if (obj.has("lon")) obj.getDouble("lon") else null,
                    mediaUri = obj.optString("uri").takeIf { it.isNotBlank() }
                )
            )
        }
        return clips.take(MAX_CLIPS)
    }

    private fun persistClipList(context: Context, clips: List<RecordingClip>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        clips.take(MAX_CLIPS).forEach { clip ->
            val obj = JSONObject().apply {
                put("id", clip.id)
                put("fileName", clip.fileName)
                put("duration", clip.durationSeconds)
                put("recordedAt", clip.recordedAt)
                put("kind", clip.kind.name)
                put("status", clip.uploadStatus.name)
                clip.latitude?.let { put("lat", it) }
                clip.longitude?.let { put("lon", it) }
                clip.mediaUri?.let { put("uri", it) }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
    }

    private fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        val mgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.forEach { provider ->
            val loc = try { mgr.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            if (loc != null) return loc.latitude to loc.longitude
        }
        return null
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
}
