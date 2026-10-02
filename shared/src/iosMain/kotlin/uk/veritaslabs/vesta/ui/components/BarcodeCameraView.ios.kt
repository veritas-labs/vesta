package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isBarcodeScanningSupported: Boolean = false

@Composable
actual fun BarcodeCameraView(onBarcode: (String) -> Unit, modifier: Modifier, kind: BarcodeKind) {
    // Not implemented on iOS yet (needs an AVFoundation capture session); the Scan button is hidden there.
}
