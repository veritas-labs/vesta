package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import uk.veritaslabs.kits.blobs.PhotoSource

/** Opens the platform's way of choosing a photo; [launch] returns at once and the choice arrives through the callback given to [rememberPhotoPicker]. */
fun interface PhotoPicker {
    fun launch()
}

/**
 * A [PhotoPicker]: the system photo picker on Android (which needs no permission), a file dialog on desktop, and one that does nothing on iOS.
 * [onPicked] is called with the chosen photo, never when the person cancels.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): PhotoPicker

/** Replaces the platform picker where set, so a test can "choose" a photo without a system dialog. */
val LocalPhotoPickerFactory = staticCompositionLocalOf<((onPicked: (PhotoSource) -> Unit) -> PhotoPicker)?> { null }

/** The picker screens use: [LocalPhotoPickerFactory]'s if there is one, otherwise the platform's [rememberPhotoPicker]. */
@Composable
fun rememberPhotoPickerForScreen(onPicked: (PhotoSource) -> Unit): PhotoPicker {
    val factory = LocalPhotoPickerFactory.current
    return if (factory != null) factory(onPicked) else rememberPhotoPicker(onPicked)
}
