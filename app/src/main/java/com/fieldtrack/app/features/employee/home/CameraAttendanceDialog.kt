package com.fieldtrack.app.features.employee.home

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Size
import java.io.File
import java.time.Instant

/** Front-facing CameraX preview. No gallery picker or external camera intent. */
@Composable
fun CameraAttendanceDialog(busy: Boolean, onDismiss: () -> Unit, onPhoto: (File, String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val capture = remember {
        ImageCapture.Builder()
            .setJpegQuality(85)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
    }
    var ready by remember { mutableStateOf(false) }
    var takingPhoto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) try {
                provider = future.get()
                val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                preview = cameraPreview
                provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, capture)
                ready = true
            } catch (_: Exception) { error = "Front camera unavailable. Check camera permissions." }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            preview?.let { provider?.unbind(it, capture) }
        }
    }
    Dialog(onDismissRequest = { if (!busy && !takingPhoto) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Live selfie attendance", style = MaterialTheme.typography.titleLarge)
                Text("Take a fresh selfie. GPS coordinates, accuracy and capture time in IST (UTC+05:30) will be embedded in the saved photo before work tracking begins.")
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(320.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = ready && !busy && !takingPhoto, onClick = {
                    takingPhoto = true; error = null
                    val photo = try {
                        File.createTempFile("attendance_", ".jpg", context.cacheDir)
                    } catch (_: java.io.IOException) {
                        takingPhoto = false
                        error = "Could not save the selfie. Check available storage."
                        return@Button
                    }
                    val capturedAt = Instant.now().toString()
                    previewView.display?.let { capture.targetRotation = it.rotation }
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(photo).build(), ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                takingPhoto = false; onPhoto(photo, capturedAt)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                photo.delete(); takingPhoto = false; error = "Photo capture failed. Please retry."
                            }
                        })
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy || takingPhoto) "Saving selfie & GPS…" else "Capture selfie & start work")
                }
                TextButton(enabled = !busy && !takingPhoto, onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
