package uk.veritaslabs.vesta.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request, from outside the UI, to open a Home's Tasks: the morning digest notification's tap (ADR-040). The platform entry point
 * calls [request]; the app opens that Home on Tasks, if the user is still in it, and calls [consume] so it happens once.
 */
object TasksDeepLink {
    private val _homeId = MutableStateFlow<String?>(null)

    /** The Home whose Tasks were asked for and not yet opened. */
    val homeId: StateFlow<String?> = _homeId.asStateFlow()

    fun request(homeId: String) {
        _homeId.value = homeId
    }

    /** Marks the request for [homeId] as dealt with; a newer request for another Home is kept. */
    fun consume(homeId: String) {
        _homeId.compareAndSet(homeId, null)
    }
}
