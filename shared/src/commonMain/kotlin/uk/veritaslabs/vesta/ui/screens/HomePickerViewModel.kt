package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.HomeListItem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.veritaslabs.vesta.home.SelectedHome

private const val StopTimeoutMillis = 5_000L

/** [HomePickerScreen]'s state; [isLoading] is true until the homes source has emitted for the first time. */
data class HomePickerUiState(
    val homes: List<HomeListItem> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backs [HomePickerScreen]: exposes [homes] as [uiState] and records the user's choice in
 * [selectedHome], which the rest of the app observes.
 */
class HomePickerViewModel(
    homes: Flow<List<HomeListItem>>,
    private val selectedHome: SelectedHome,
) : ViewModel() {

    val uiState: StateFlow<HomePickerUiState> = homes
        .map { HomePickerUiState(homes = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), HomePickerUiState())

    fun selectHome(home: HomeListItem) {
        selectedHome.select(home.id)
    }
}
