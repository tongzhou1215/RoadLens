package com.cs407.roadlens.ui.screen

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.roadlens.viewmodel.ClipKind
import com.cs407.roadlens.viewmodel.RecordingStopReason
import com.cs407.roadlens.viewmodel.RecordingUiState
import com.cs407.roadlens.viewmodel.ViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    uiState: RecordingUiState,
    cameraAllowed: Boolean = true,
    accelActive: Boolean = true,
    onToggleRecording: () -> Unit,
    onManualSave: () -> Unit,
    onAutoStopAcknowledged: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    // NEW: callback so the route can get the VideoCapture instance
    onBindVideoCapture: (VideoCapture<Recorder>) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // ----- TOAST EVERY TIME A CLIP IS SAVED (MANUAL OR LOOP) -----
    LaunchedEffect(uiState.autoStopped, uiState.lastSavedClipKind) {
        val message = when (uiState.lastSavedClipKind) {
            ClipKind.MANUAL ->
                "Manual clip saved"
            ClipKind.LOOP ->
                "${formatDurationLabel(uiState.targetDurationSeconds)} clip saved"
            else -> null
        }

        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        if (uiState.autoStopped) {
            onAutoStopAcknowledged()
        }
    }

    // If permission revoked while recording, stop
    LaunchedEffect(cameraAllowed, uiState.isRecording) {
        if (!cameraAllowed && uiState.isRecording) onToggleRecording()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // ===== Camera preview + overlays =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (cameraAllowed) {
                    CameraPreviewBox(
                        modifier = Modifier.matchParentSize(),
                        onVideoCaptureCreated = onBindVideoCapture
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("0 MPH", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "GPS: Active",
                            color = Color(0xFF52D273),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (uiState.isRecording) Color(0xFFED4245) else Color(0xFF64748B))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isRecording) "LIVE REC" else "PREVIEW",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                onBack?.let { back ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 6.dp, start = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x66121315))
                            .clickable { back() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("← Back", color = Color.White, fontSize = 12.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC111315))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatHms(uiState.elapsedSeconds),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (uiState.isRecording) {
                            Text(
                                text = "Saving ${formatDurationLabel(uiState.targetDurationSeconds)} clips",
                                color = Color(0xFFCBD5F5),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // ===== Controls panel =====
            Surface(
                color = Color(0xFFF7F7FA),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val recordEnabled = cameraAllowed
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    if (recordEnabled) {
                                        if (uiState.isRecording) Color(0xFFEF4444) else Color(0xFFFF7A00)
                                    } else {
                                        Color(0xFFFF7A00).copy(alpha = 0.35f)
                                    }
                                )
                                .border(2.dp, Color(0xFFFFA552), CircleShape)
                                .let { base ->
                                    if (recordEnabled) base.clickable { onToggleRecording() } else base
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isRecording) "■" else "⏺",
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        }
                    }

                    if (!cameraAllowed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Camera permission required. Go to Settings.",
                            color = Color(0xFFEA4335),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Route used by your NavGraph */
@Composable
fun RecordingRoute(
    vm: ViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val ui by vm.recordingState.collectAsState()

    // CameraX video pieces
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    fun startVideoRecording() {
        val vc = videoCapture ?: return

        val name = "RoadLens_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RoadLens")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            ctx.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val recorder = vc.output
            .prepareRecording(ctx, outputOptions)
            .apply {
                val audioGranted =
                    PermissionChecker.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
                if (audioGranted) {
                    withAudioEnabled()
                }
            }

        activeRecording = recorder.start(ContextCompat.getMainExecutor(ctx)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        Toast.makeText(
                            ctx,
                            "Saved to gallery: ${event.outputResults.outputUri}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            ctx,
                            "Recording error: ${event.error}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    activeRecording = null
                }

                else -> Unit
            }
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    RecordingScreen(
        uiState = ui,
        cameraAllowed = vm.settings.cameraGranted,
        onToggleRecording = {
            if (ui.isRecording) {
                // stop video + inform VM
                stopVideoRecording()
                vm.stopRecording(ctx, RecordingStopReason.MANUAL)
            } else {
                // start video + inform VM
                startVideoRecording()
                vm.startRecording(ctx)
            }
        },
        onManualSave = { vm.manualClip(ctx) },
        onAutoStopAcknowledged = { vm.acknowledgeAutoStop() },
        onBack = onBack,
        onBindVideoCapture = { vc -> videoCapture = vc }
    )
}

/** CameraX preview + wiring up VideoCapture */
@Composable
private fun CameraPreviewBox(
    modifier: Modifier = Modifier,
    onVideoCaptureCreated: (VideoCapture<Recorder>) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx) }

    // Ask CAMERA + AUDIO permission if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!cameraGranted || !audioGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    // Bind Preview + VideoCapture
    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val listener = Runnable {
            try {
                val provider = future.get()

                val preview = Preview.Builder().build().also { p ->
                    p.setSurfaceProvider(previewView.surfaceProvider)
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                val videoCapture = VideoCapture.withOutput(recorder)

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )

                onVideoCaptureCreated(videoCapture)
            } catch (t: Throwable) {
                Toast.makeText(ctx, "Camera bind failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(ctx))
        onDispose {
            // Leave bound while visible; actual lifecycle handles cleanup
        }
    }
}

/* ---------- small helpers ---------- */

private fun formatHms(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatDurationLabel(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        if (seconds == 0) String.format("%d min", minutes) else String.format("%d:%02d min", minutes, seconds)
    } else {
        String.format("%d sec", seconds)
    }
}
