package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val StopTimeoutMillis = 5_000L

/** [PantryCategoriesScreen]'s state; [isLoading] is true until [PantryCategoriesSource] has emitted. */
data class PantryCategoriesUiState(
    val categories: List<PantryCategoryGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs [PantryCategoriesScreen]: exposes [source]'s categories as [uiState] and forwards edits to it. */
class PantryCategoriesViewModel(private val source: PantryCategoriesSource) : ViewModel() {

    val uiState: StateFlow<PantryCategoriesUiState> = source.categories
        .map { PantryCategoriesUiState(categories = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), PantryCategoriesUiState())

    /** Registers [name] as a primary category; blank names are ignored. */
    fun addPrimary(name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { viewModelScope.launch { source.addPrimary(it) } }
    }

    /** Registers [name] under [primary]; blank names are ignored. */
    fun addSecondary(primary: String, name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { viewModelScope.launch { source.addSecondary(primary, it) } }
    }

    fun deletePrimary(name: String) {
        viewModelScope.launch { source.deletePrimary(name) }
    }

    fun deleteSecondary(primary: String, name: String) {
        viewModelScope.launch { source.deleteSecondary(primary, name) }
    }
}
