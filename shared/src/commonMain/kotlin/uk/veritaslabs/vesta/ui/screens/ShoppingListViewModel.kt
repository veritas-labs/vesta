package uk.veritaslabs.vesta.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val tags: List<String> = emptyList(),
    /** How many of [quantity] have been picked up so far. */
    val got: Int = 0,
    /** The shop had none of it. */
    val outOfStock: Boolean = false,
    /** Keys of the merchants it is bought from, highest priority first, taken from its Pantry entry (see [withMerchantKeys]). */
    val merchantKeys: List<String> = emptyList(),
) {
    /** Everything wanted has been picked up. */
    val isCrossedOff: Boolean get() = !outOfStock && got >= quantity

    /** Some, but not all, of what was wanted has been picked up. */
    val isPartlyGot: Boolean get() = !outOfStock && got in 1 until quantity

    /** Checked off in any way: crossed off, partly got or out of stock. */
    val isCheckedOff: Boolean get() = outOfStock || got > 0

    /** Something has been picked up (so it can go to the Pantry): crossed off or partly got, but not out of stock. */
    val isBought: Boolean get() = !outOfStock && got > 0
}

/** How many of [items] are still to be dealt with: not crossed off, partly got or out of stock. */
fun shoppingRemaining(items: List<ShoppingListItem>): Int = items.count { !it.isCheckedOff }

/** The editable fields of a shopping-list item; [key] is null while adding one. */
data class ShoppingItemDetails(
    val key: String? = null,
    val primaryCategory: String = "",
    val secondaryCategory: String? = null,
    val title: String = "",
    val quantity: Int = 1,
    val packageSize: String = "",
    val barcode: String? = null,
    val tags: List<String> = emptyList(),
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

    /** Records that [got] of the item at [key] are picked up, or that the shop is [outOfStock] of it. */
    suspend fun setProgress(key: String, got: Int, outOfStock: Boolean)

    /**
     * Puts what has been picked up into the Pantry: each [ShoppingListItem.isBought] item adds its [ShoppingListItem.got]
     * (at most its quantity) to its Pantry stock count, and leaves the list if that was all of it, or stays, wanting
     * the rest and unchecked, if it was only part. Returns how many items were added; 0 by default, for a source with no Pantry.
     */
    suspend fun addCheckedToPantry(): Int = 0
}

/**
 * [ShoppingListScreen]'s state; [isLoading] is true until the source has emitted for the first time. [selectedTags]
 * are the lower-cased tags and [selectedMerchants] the merchant keys the list is filtered to (none: everything shows);
 * [hideChecked] hides the items that are checked off. See [applyShoppingFilters].
 */
data class ShoppingListUiState(
    val items: List<ShoppingListItem> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val selectedMerchants: Set<String> = emptySet(),
    val hideChecked: Boolean = false,
    val isLoading: Boolean = true,
)

/** The distinct tags used by [items] (ignoring case, first spelling wins), sorted for display. */
fun shoppingTags(items: List<ShoppingListItem>): List<String> =
    items.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

/**
 * The [items] carrying any of the [selected] tags (lower-cased). Selected tags no item has any more are ignored, and
 * with none left every item shows.
 */
fun filterByTags(items: List<ShoppingListItem>, selected: Set<String>): List<ShoppingListItem> {
    val active = selected intersect shoppingTags(items).map { it.lowercase() }.toSet()
    return if (active.isEmpty()) items else items.filter { item -> item.tags.any { it.lowercase() in active } }
}

/**
 * [items] with [ShoppingListItem.merchantKeys] filled in from the Pantry entry each one is stocked as: the entry at
 * the item's barcode or lower-cased title (as the Pantry addresses them — ADR-008), else the first entry with the same
 * title ignoring case.
 */
fun withMerchantKeys(items: List<ShoppingListItem>, pantryItems: List<PantryListItem>): List<ShoppingListItem> {
    val byKey = pantryItems.associate { it.key to it.merchantKeys }
    val byTitle = pantryItems.groupBy { it.title.trim().lowercase() }.mapValues { (_, entries) -> entries.first().merchantKeys }
    return items.map { item ->
        val title = item.title.trim().lowercase()
        val keys = item.barcode?.takeIf { it.isNotBlank() }?.let { byKey[it] } ?: byKey[title] ?: byTitle[title] ?: return@map item
        item.copy(merchantKeys = keys)
    }
}

/** The merchants, sorted by name, that at least one of [items] is bought from. */
fun shoppingMerchants(items: List<ShoppingListItem>, merchants: List<Merchant>): List<Merchant> {
    val used = items.flatMapTo(mutableSetOf()) { it.merchantKeys }
    return merchants.filter { it.key in used }.sortedBy { it.name.lowercase() }
}

/**
 * The [items] bought from any of the [selected] merchant keys. Selected merchants no item is bought from are
 * ignored, and with none left every item shows.
 */
fun filterByMerchants(items: List<ShoppingListItem>, selected: Set<String>): List<ShoppingListItem> {
    val active = selected intersect items.flatMapTo(mutableSetOf()) { it.merchantKeys }
    return if (active.isEmpty()) items else items.filter { item -> item.merchantKeys.any { it in active } }
}

/** [items] narrowed by the tags, the merchants and the hide-checked switch together (an item must pass all three). */
fun applyShoppingFilters(
    items: List<ShoppingListItem>,
    selectedTags: Set<String>,
    selectedMerchants: Set<String>,
    hideChecked: Boolean,
): List<ShoppingListItem> =
    filterByMerchants(filterByTags(items, selectedTags), selectedMerchants).filter { !hideChecked || !it.isCheckedOff }

/** Backs [ShoppingListScreen]: exposes the list from [source] as [uiState], with the filters the user has chosen. */
class ShoppingListViewModel(private val source: ShoppingListSource) : ViewModel() {

    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())

    private val selectedMerchants = MutableStateFlow<Set<String>>(emptySet())
    private val hideChecked = MutableStateFlow(false)

    val uiState: StateFlow<ShoppingListUiState> = combine(
        source.items, selectedTags, selectedMerchants, hideChecked,
    ) { items, tags, merchants, hide ->
        ShoppingListUiState(items = items, selectedTags = tags, selectedMerchants = merchants, hideChecked = hide, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ShoppingListUiState())

    /** Adds [tag] to the filter, or takes it out if it is there. */
    fun toggleTag(tag: String) {
        val key = tag.lowercase()
        selectedTags.update { if (key in it) it - key else it + key }
    }

    /** Adds the merchant at [key] to the filter, or takes it out if it is there. */
    fun toggleMerchant(key: String) {
        selectedMerchants.update { if (key in it) it - key else it + key }
    }

    /** Hides the items that are checked off, or shows them again. */
    fun setHideChecked(hide: Boolean) {
        hideChecked.value = hide
    }

    fun save(item: ShoppingItemDetails) {
        viewModelScope.launch { source.save(item) }
    }

    /** Crosses [item] off, or puts it back to normal if it is crossed off or out of stock (a partly got item is crossed off). */
    fun toggleCrossedOff(item: ShoppingListItem) {
        val normal = item.isCrossedOff || item.outOfStock
        setProgress(item.key, got = if (normal) 0 else item.quantity, outOfStock = false)
    }

    /** Records that [got] of the item at [key] are picked up (or that it is [outOfStock]). */
    fun setProgress(key: String, got: Int, outOfStock: Boolean) {
        viewModelScope.launch { source.setProgress(key, got, outOfStock) }
    }

    /** Resets the list: every item that is crossed off, partly got or out of stock becomes unchecked again. */
    fun uncheckAll() {
        viewModelScope.launch {
            uiState.value.items.filter { it.isCheckedOff }.forEach { source.setProgress(it.key, 0, false) }
        }
    }

    /** Adds what has been picked up to the Pantry (see [ShoppingListSource.addCheckedToPantry]), then calls [onDone] with how many items that was. */
    fun addCheckedToPantry(onDone: (addedCount: Int) -> Unit) {
        viewModelScope.launch { onDone(source.addCheckedToPantry()) }
    }

    /** Removes every item from the list. */
    fun removeAll() {
        viewModelScope.launch { uiState.value.items.forEach { source.remove(it.key) } }
    }

    fun remove(key: String) {
        viewModelScope.launch { source.remove(key) }
    }
}
