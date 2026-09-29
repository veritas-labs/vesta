package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val StopTimeoutMillis = 5_000L

/** What [PantryScreen] needs to render: the Pantry's entries plus the primary categories that currently have none. */
data class PantryContents(
    val items: List<PantryListItem> = emptyList(),
    val emptyCategories: List<String> = emptyList(),
)

/**
 * Where [PantryViewModel] gets a Home's Pantry from. Implemented over the Pantry Automerge document
 * (ADR-008), which lives in jvm/android-only code — this seam keeps the view model in commonMain,
 * next to the screen it backs. Emits whenever the Pantry changes (locally or via sync).
 */
interface PantrySource {
    val contents: Flow<PantryContents>

    /** Creates [item] (null [PantryItemDetails.key]) or updates the entry at its key; [contents] re-emits. */
    suspend fun save(item: PantryItemDetails)

    /** Removes the entry at [key]; [contents] re-emits. */
    suspend fun delete(key: String)
}

/** [PantryScreen]'s state; [isLoading] is true until [PantrySource] has emitted for the first time. */
data class PantryUiState(
    val items: List<PantryListItem> = emptyList(),
    val emptyCategories: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs [PantryScreen]: exposes the Pantry from [source] as [uiState], updating as the Pantry changes. */
class PantryViewModel(private val source: PantrySource) : ViewModel() {

    val uiState: StateFlow<PantryUiState> = source.contents
        .map { PantryUiState(items = it.items, emptyCategories = it.emptyCategories, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), PantryUiState())

    /** Removes the entry at [key] from the Pantry. */
    fun delete(key: String) {
        viewModelScope.launch { source.delete(key) }
    }

    /** Persists [item] to the Pantry; the new/updated entry then arrives through [uiState]. */
    fun save(item: PantryItemDetails) {
        viewModelScope.launch { source.save(item) }
    }
}
