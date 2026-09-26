package com.reconsiliation.caisse.ui.scanner

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import android.os.Vibrator
import android.content.Context
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import com.reconsiliation.caisse.R
import com.reconsiliation.caisse.permissions.PermissionDecision
import com.reconsiliation.caisse.permissions.PermissionPolicy
import com.reconsiliation.caisse.ui.permissions.RefreshOnResume
import com.reconsiliation.caisse.ui.permissions.findActivity
import com.reconsiliation.caisse.ui.permissions.hasPermission
import com.reconsiliation.caisse.ui.permissions.openApplicationSettings

@Composable
fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    continuous: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (continuous) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_finish)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (continuous) stringResource(R.string.action_cancel) else stringResource(R.string.action_close)) } },
        title = { Text(if (continuous) stringResource(R.string.title_continuous_scan) else stringResource(R.string.title_scan_barcode)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                BarcodeScannerView(onBarcodeScanned, continuous, onDismiss)
            }
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeScannerView(onBarcodeScanned: (String) -> Unit, continuous: Boolean = false, onDismiss: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var requestAttempted by remember { mutableStateOf(false) }

    fun refreshCameraPermission() {
        hasCameraPermission = context.hasPermission(Manifest.permission.CAMERA)
    }

    RefreshOnResume { refreshCameraPermission() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            requestAttempted = true
            hasCameraPermission = granted
        }
    )

    val rationaleVisible = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    val decision = PermissionPolicy.decide(hasCameraPermission, rationaleVisible, requestAttempted)

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.permission_camera_rationale))
                if (decision == PermissionDecision.OpenSettings) {
                    Text(stringResource(R.string.permission_denied_permanently))
                    Button(onClick = { context.openApplicationSettings() }) {
                        Text(stringResource(R.string.action_open_app_settings))
                    }
                } else {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.action_authorize))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_enter_barcode_manually))
                }
            }
        }
        return
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasScanned by remember { mutableStateOf(false) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    var lastScannedCode by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableLongStateOf(0L) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && (continuous || !hasScanned)) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    val code = barcodes[0].rawValue ?: ""
                                    val currentTime = System.currentTimeMillis()
                                    if (code.isNotEmpty() && (code != lastScannedCode || currentTime - lastScanTime > 2000)) {
                                        if (!continuous) hasScanned = true
                                        lastScannedCode = code
                                        lastScanTime = currentTime
                                        vibrator.vibrate(100)
                                        onBarcodeScanned(code)
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("Scanner", "Camera binding failed", e)
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
