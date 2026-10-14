package uk.veritaslabs.vesta.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_cancel

object ConfirmDeleteTestTags {
    const val Dialog = "confirm_delete_dialog"
    const val Confirm = "confirm_delete_confirm"
    const val Cancel = "confirm_delete_cancel"
}

/**
 * The "are you sure?" dialog every destructive delete asks before going ahead: [title] says what is about to go, [body] what that
 * means, and the [confirmLabel] button (in the error colour) calls [onConfirm]. Cancel or tapping outside calls [onDismiss].
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(ConfirmDeleteTestTags.Confirm)) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ConfirmDeleteTestTags.Cancel)) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
        modifier = modifier.testTag(ConfirmDeleteTestTags.Dialog),
    )
}
