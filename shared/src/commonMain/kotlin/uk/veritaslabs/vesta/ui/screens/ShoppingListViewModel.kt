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

/** One line of the Home's shopping list, as [ShoppingListScreen] shows it (the UI mirror of `ShoppingItem`, ADR-008). */
data class ShoppingListItem(
    val key: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val title: String,
    val quantity: Int,
    val packageSize: String,
    val barcode: String? = null,
)

/** The editable fields of a shopping-list item; [key] is null while adding one. */
data class ShoppingItemDetails(
    val key: String? = null,
    val primaryCategory: String = "",
    val secondaryCategory: String? = null,
    val title: String = "",
    val quantity: Int = 1,
    val packageSize: String = "",
    val barcode: String? = null,
)

/**
 * Where [ShoppingListViewModel] gets a Home's shopping list from. Implemented over the shopping-list Automerge
 * document (ADR-008), which lives in jvm/android-only code. Saving an item also adds it to the Pantry if it is new.
 */
interface ShoppingListSource {
    val items: Flow<List<ShoppingListItem>>

    /** Adds [item] (null [ShoppingItemDetails.key]; an item already listed gets the quantities added) or edits one. */
    suspend fun save(item: ShoppingItemDetails)

    suspend fun remove(key: String)
}

/** [ShoppingListScreen]'s state; [isLoading] is true until the source has emitted for the first time. */
data class ShoppingListUiState(val items: List<ShoppingListItem> = emptyList(), val isLoading: Boolean = true)

/** Backs [ShoppingListScreen]: exposes the list from [source] as [uiState]. */
class ShoppingListViewModel(private val source: ShoppingListSource) : ViewModel() {

    val uiState: StateFlow<ShoppingListUiState> = source.items
        .map { ShoppingListUiState(items = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ShoppingListUiState())

    fun save(item: ShoppingItemDetails) {
        viewModelScope.launch { source.save(item) }
    }

    fun remove(key: String) {
        viewModelScope.launch { source.remove(key) }
    }
}
