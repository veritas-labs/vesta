package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.Composable
import uk.veritaslabs.kits.blobs.PhotoSource

// Photos need Automerge-backed blobs, which iOS doesn't have yet (see LocalBlobs), so there is nothing to pick for.
@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): PhotoPicker = PhotoPicker { }
