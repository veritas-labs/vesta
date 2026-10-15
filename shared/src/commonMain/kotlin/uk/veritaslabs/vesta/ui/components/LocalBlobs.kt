package uk.veritaslabs.vesta.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import uk.veritaslabs.vesta.blobs.Blobs

/** The [Blobs] of the running app; null where there is none (previews, iOS), in which case photos can't be added and show their placeholder. */
val LocalBlobs = staticCompositionLocalOf<Blobs?> { null }
