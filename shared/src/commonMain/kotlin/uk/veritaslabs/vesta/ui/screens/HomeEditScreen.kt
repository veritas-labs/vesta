package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.HomeDetails
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import uk.veritaslabs.vesta.ui.components.NarrowContentMaxWidth
import uk.veritaslabs.vesta.ui.components.ConfirmDeleteDialog
import vesta.shared.generated.resources.home_delete_confirm_title
import vesta.shared.generated.resources.home_delete_confirm_body
import vesta.shared.generated.resources.home_delete_confirm
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.home_edit_create_title
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_trash
import vesta.shared.generated.resources.home_edit_delete
import vesta.shared.generated.resources.home_edit_edit_title
import vesta.shared.generated.resources.home_edit_name_error
import vesta.shared.generated.resources.home_edit_name_helper
import vesta.shared.generated.resources.home_edit_name_label
import vesta.shared.generated.resources.home_edit_save
import vesta.shared.generated.resources.home_edit_save_create
import vesta.shared.generated.resources.home_edit_shape_family
import vesta.shared.generated.resources.home_edit_shape_family_description
import vesta.shared.generated.resources.home_edit_shape_flatshare
import vesta.shared.generated.resources.home_edit_shape_flatshare_description
import vesta.shared.generated.resources.home_edit_shape_label
import vesta.shared.generated.resources.home_edit_shape_other
import vesta.shared.generated.resources.home_edit_shape_other_description
import vesta.shared.generated.resources.home_edit_shape_single_support
import vesta.shared.generated.resources.home_edit_shape_single_support_description
import vesta.shared.generated.resources.ic_dots_vertical

private val HomeEditTabletBreakpoint = 600.dp
private const val HomeNameMinLength = 2
private const val HomeNameMaxLength = 50

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HomeEditTestTags {
    const val NameField = "home_edit_name_field"
    const val SaveButton = "home_edit_save_button"
    const val DeleteButton = "home_edit_delete_button"
    const val BackButton = "home_edit_back_button"

    /** Tag for the option of the given [shape], independent of its displayed label. */
    fun shapeOption(shape: HomeShape) = "home_edit_shape_${shape.storageValue}"
}

/** The fixed Home Shapes (FR-1); [storageValue] is what the `Home.shape` column holds. */
enum class HomeShape(
    val storageValue: String,
    internal val label: StringResource,
    internal val description: StringResource,
) {
    Family("family", Res.string.home_edit_shape_family, Res.string.home_edit_shape_family_description),
    Flatshare("flatshare", Res.string.home_edit_shape_flatshare, Res.string.home_edit_shape_flatshare_description),
    SingleSupport(
        "single_support",
        Res.string.home_edit_shape_single_support,
        Res.string.home_edit_shape_single_support_description,
    ),
    Other("other", Res.string.home_edit_shape_other, Res.string.home_edit_shape_other_description);

    companion object {
        fun fromStorageValue(value: String): HomeShape? = entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * Home edit form backed by [viewModel]: saving or deleting persists via the view model, then calls
 * [onFinished]. Blank while an existing Home is still loading, so the form doesn't flash empty.
 */
@Composable
fun HomeEditScreen(
    viewModel: HomeEditViewModel,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    onCancel: () -> Unit = onFinished,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onFinished()
    }
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        HomeEditScreen(
            modifier = modifier,
            home = state.home,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onCancel = onCancel,
        )
    }
}

/**
 * Create/update/delete form for a single Home. Pass [home] to edit an existing
 * one, or leave it null to create a new one — the Delete action only appears
 * once [home] (and its [HomeDetails.id]) is present.
 *
 * UI only — the caller owns persistence: [onSave] receives the edited
 * [HomeDetails] with the name trimmed and a shape always set (currently [HomeShape.Other] unless editing); [onDelete]
 * receives the id of the Home to remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeEditScreen(
    modifier: Modifier = Modifier,
    home: HomeDetails? = null,
    onSave: (HomeDetails) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    var name by remember(home) { mutableStateOf(home?.name.orEmpty()) }
    // Shape selection is parked for now (see the commented-out block below): new Homes are "Other",
    // and an existing Home keeps whatever shape it has.
    val shape = home?.shape ?: HomeShape.Other.storageValue
    var nameTouched by remember(home) { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val isNameValid = name.trim().length in HomeNameMinLength..HomeNameMaxLength
    val isValid = isNameValid

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= HomeEditTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (home == null) Res.string.home_edit_create_title else Res.string.home_edit_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(HomeEditTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.common_cancel),
                        )
                    }
                },
                actions = {
                    home?.id?.let { id ->
                        IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag(HomeEditTestTags.DeleteButton)) {
                            Icon(painterResource(Res.drawable.ic_trash), contentDescription = stringResource(Res.string.home_edit_delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )

            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.widthIn(max = NarrowContentMaxWidth).fillMaxWidth().padding(contentPadding)) {
                    val showNameError = nameTouched && !isNameValid
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(HomeNameMaxLength)
                            nameTouched = true
                        },
                        label = { Text(stringResource(Res.string.home_edit_name_label)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (showNameError) Res.string.home_edit_name_error else Res.string.home_edit_name_helper,
                                ),
                            )
                        },
                        isError = showNameError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag(HomeEditTestTags.NameField),
                    )

                    // TODO: Home shape picker — parked as a future feature; every Home is "Other" until then.
                    // Text(
                    //     text = stringResource(Res.string.home_edit_shape_label),
                    //     style = MaterialTheme.typography.titleMedium,
                    //     color = MaterialTheme.colorScheme.onBackground,
                    //     modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    // )
                    // Column(modifier = Modifier.selectableGroup()) {
                    //     HomeShape.entries.forEach { option ->
                    //         HomeShapeOption(
                    //             shape = option,
                    //             selected = shape == option,
                    //             onSelect = { shape = option },
                    //         )
                    //     }
                    // }

                    PrimaryButton(
                        label = stringResource(
                            if (home == null) Res.string.home_edit_save_create else Res.string.home_edit_save,
                        ),
                        enabled = isValid,
                        onClick = {
                            onSave(HomeDetails(id = home?.id, name = name.trim(), shape = shape))
                        },
                        modifier = Modifier.padding(top = 24.dp).testTag(HomeEditTestTags.SaveButton),
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.home_delete_confirm_title),
            body = stringResource(Res.string.home_delete_confirm_body),
            confirmLabel = stringResource(Res.string.home_delete_confirm),
            onConfirm = {
                confirmingDelete = false
                home?.id?.let(onDelete)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

// Parked with the shape picker above.
// @Composable
// private fun HomeShapeOption(shape: HomeShape, selected: Boolean, onSelect: () -> Unit) {
//     Row(
//         modifier = Modifier
//             .fillMaxWidth()
//             .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
//             .padding(vertical = 8.dp)
//             .testTag(HomeEditTestTags.shapeOption(shape)),
//         verticalAlignment = Alignment.CenterVertically,
//     ) {
//         RadioButton(selected = selected, onClick = null)
//         Column(modifier = Modifier.padding(start = 16.dp)) {
//             Text(
//                 text = stringResource(shape.label),
//                 style = MaterialTheme.typography.bodyLarge,
//                 color = MaterialTheme.colorScheme.onBackground,
//             )
//             Text(
//                 text = stringResource(shape.description),
//                 style = MaterialTheme.typography.bodyMedium,
//                 color = MaterialTheme.colorScheme.onSurfaceVariant,
//             )
//         }
//     }
// }

private val HomeEditPreviewHome = HomeDetails(id = "flat-4b", name = "Flat 4B", shape = HomeShape.Flatshare.storageValue)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeEditScreenCreatePhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeEditScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeEditScreenCreatePhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeEditScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeEditScreenCreateTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeEditScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeEditScreenCreateTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeEditScreen() }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeEditScreenEditPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomeEditScreen(home = HomeEditPreviewHome) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomeEditScreenEditPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomeEditScreen(home = HomeEditPreviewHome) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeEditScreenEditTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomeEditScreen(home = HomeEditPreviewHome) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomeEditScreenEditTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomeEditScreen(home = HomeEditPreviewHome) }
}
