package uk.veritaslabs.vesta.ui.flows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import uk.veritaslabs.vesta.ui.screens.MerchantDetailScreen
import uk.veritaslabs.vesta.ui.screens.MerchantEditScreen
import uk.veritaslabs.vesta.ui.screens.MerchantsScreen
import uk.veritaslabs.vesta.ui.screens.MerchantsViewModel

private enum class MerchantsStep { List, Detail, Edit }

/**
 * The merchants' three screens (ADR-026) and the moves between them: the list, one merchant, and the form that adds or
 * edits one. [onBack] leaves the merchants from the list. A merchant that disappears while it is open (deleted here or
 * on another device) returns to the list.
 */
@Composable
fun MerchantsFlow(viewModel: MerchantsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Not lifecycle-aware: the list screen collects the same state with the lifecycle, and two observers added at once race.
    val state by viewModel.uiState.collectAsState()
    var step by remember { mutableStateOf(MerchantsStep.List) }
    // The merchant open on the detail screen, and the one being edited (null while adding); both by key.
    var viewingKey by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    val viewing = state.merchants.firstOrNull { it.key == viewingKey }
    when (step) {
        MerchantsStep.List -> MerchantsScreen(
            viewModel,
            modifier,
            onMerchantSelected = {
                viewingKey = it.key
                step = MerchantsStep.Detail
            },
            onBack = onBack,
            onAddMerchant = {
                editingKey = null
                step = MerchantsStep.Edit
            },
        )
        MerchantsStep.Detail -> viewing?.let { merchant ->
            MerchantDetailScreen(
                merchant,
                modifier,
                onBack = { step = MerchantsStep.List },
                onEdit = {
                    editingKey = merchant.key
                    step = MerchantsStep.Edit
                },
                onDelete = {
                    viewModel.remove(merchant.key)
                    step = MerchantsStep.List
                },
            )
        } ?: LaunchedEffect(Unit) { step = MerchantsStep.List }
        MerchantsStep.Edit -> {
            val editing = state.merchants.firstOrNull { it.key == editingKey }
            val backTo = if (editing != null) MerchantsStep.Detail else MerchantsStep.List
            MerchantEditScreen(
                merchant = editing,
                modifier = modifier,
                onSave = {
                    viewModel.save(it)
                    step = backTo
                },
                onCancel = { step = backTo },
            )
        }
    }
}
