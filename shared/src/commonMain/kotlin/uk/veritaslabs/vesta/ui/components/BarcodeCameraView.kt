package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Whether this platform can scan barcodes with a camera; callers hide their scan entry point when it can't. */
expect val isBarcodeScanningSupported: Boolean

/**
 * A live camera preview that watches for barcodes and calls [onBarcode] with each decoded value (it may repeat
 * the same value while the code stays in view, so callers should stop showing the view after the first one).
 * Handles its own camera permission and no-camera states. Only call where [isBarcodeScanningSupported] is true.
 */
@Composable
expect fun BarcodeCameraView(onBarcode: (String) -> Unit, modifier: Modifier = Modifier)
