package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object UndoTestTags {
    const val Snackbar = "undo_snackbar"
    const val Action = "undo_action"
}

/**
 * Tells the user something was deleted and lets them take it back: [offer] shows [message] with an Undo button on [host], and calls
 * `onUndo` if it is pressed before the snackbar goes. A newer offer replaces an older one, so only the latest delete can be undone.
 */
class UndoController(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope,
    private val undoLabel: String,
) {
    fun offer(message: String, onUndo: () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

/** The app's [UndoController], or null where there is no snackbar host (previews and most screen tests), where deleting simply deletes. */
val LocalUndo = staticCompositionLocalOf<UndoController?> { null }
