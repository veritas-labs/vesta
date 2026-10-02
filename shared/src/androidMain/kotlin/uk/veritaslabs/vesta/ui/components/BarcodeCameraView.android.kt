package uk.veritaslabs.vesta.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.barcode_scanner_no_camera
import vesta.shared.generated.resources.barcode_scanner_permission_grant
import vesta.shared.generated.resources.barcode_scanner_permission_message

actual val isBarcodeScanningSupported: Boolean = true

/** Retail product codes only: they carry a check digit, so stripes elsewhere in the shot rarely read as one. */
private val ProductBarcodeFormats = intArrayOf(
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_UPC_A,
    Barcode.FORMAT_UPC_E,
)

private fun BarcodeKind.formats(): IntArray = when (this) {
    BarcodeKind.Product -> ProductBarcodeFormats
    BarcodeKind.QrCode -> intArrayOf(Barcode.FORMAT_QR_CODE)
}

@Composable
actual fun BarcodeCameraView(onBarcode: (String) -> Unit, modifier: Modifier, kind: BarcodeKind) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) {
        if (!granted) requestPermission.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraPreview(onBarcode, kind, modifier)
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(Res.string.barcode_scanner_permission_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(Res.string.barcode_scanner_permission_grant))
            }
        }
    }
}

@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, kind: BarcodeKind, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    var noCamera by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner, kind) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val formats = kind.formats()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray()).build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameraProvider = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                } else {
                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstNotNullOfOrNull { it.rawValue }?.let { currentOnBarcode(it) }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: IllegalArgumentException) {
                // No back camera on this device.
                noCamera = true
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    if (noCamera) {
        Text(
            text = stringResource(Res.string.barcode_scanner_no_camera),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(24.dp),
        )
    } else {
        AndroidView(factory = { previewView }, modifier = modifier)
    }
}
