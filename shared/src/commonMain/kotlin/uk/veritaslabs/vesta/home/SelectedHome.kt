package uk.veritaslabs.vesta.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which Home the user is currently working in, by Home id (null = none selected, e.g. on the
 * Home picker). Screens and view models that are scoped to a Home (Pantry, ...) observe [homeId]
 * rather than being handed an id, so switching Homes re-scopes them. In-memory only for now.
 */
class SelectedHome {
    private val _homeId = MutableStateFlow<String?>(null)

    val homeId: StateFlow<String?> = _homeId.asStateFlow()

    fun select(homeId: String) {
        _homeId.value = homeId
    }

    fun clear() {
        _homeId.value = null
    }

    /** Clears the selection if [homeId] is the selected Home, e.g. after it is deleted or left. */
    fun clearIf(homeId: String) {
        _homeId.compareAndSet(homeId, null)
    }
}
