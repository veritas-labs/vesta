package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.home.HomeRepository
import uk.veritaslabs.vesta.home.SelectedHome

/**
 * [HomeEditScreen]'s state. [isLoading] is true while an existing Home is being read; [home] is that
 * Home (null when creating, or when [homeId] no longer exists). [isFinished] flips to true once a save
 * or delete has completed, telling the caller to leave the screen.
 */
data class HomeEditUiState(
    val home: HomeDetails? = null,
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
)

/**
 * Backs [HomeEditScreen]: loads the Home with [homeId] for editing (or starts blank when [homeId] is
 * null), and persists saves and deletes through [repository]. Deleting the currently selected Home
 * also clears it from [selectedHome].
 */
class HomeEditViewModel(
    private val repository: HomeRepository,
    private val selectedHome: SelectedHome,
    private val homeId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeEditUiState(isLoading = homeId != null))
    val uiState: StateFlow<HomeEditUiState> = _uiState.asStateFlow()

    init {
        homeId?.let { id ->
            viewModelScope.launch {
                _uiState.value = HomeEditUiState(home = repository.get(id), isLoading = false)
            }
        }
    }

    /** Creates a new Home, or updates the one being edited, then marks the screen finished. */
    fun save(details: HomeDetails) {
        val shape = details.shape ?: return
        viewModelScope.launch {
            details.id?.let { repository.update(it, details.name, shape) } ?: repository.create(details.name, shape)
            _uiState.value = _uiState.value.copy(isFinished = true)
        }
    }

    /** Deletes the Home with the given [id], then marks the screen finished. */
    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            selectedHome.clearIf(id)
            _uiState.value = _uiState.value.copy(isFinished = true)
        }
    }
}
