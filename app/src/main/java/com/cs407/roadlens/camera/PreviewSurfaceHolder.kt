package com.cs407.roadlens.camera

import androidx.camera.core.Preview

/**
 * Shares a Preview.SurfaceProvider between the UI preview and the dashcam service
 * so the live view can stay active while recording.
 */
object PreviewSurfaceHolder {
    @Volatile
    var surfaceProvider: Preview.SurfaceProvider? = null
}
