package uk.veritaslabs.vesta.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.veritaslabs.kits.spaces.LeaveResult
import uk.veritaslabs.kits.spaces.SpaceRepository

/**
 * Vesta's Homes: a Home is a Space of the kits whose kind is the Home's shape (`Home.shape`, a [HomeShape] storage value).
 * This adapts [SpaceRepository], which speaks strings, to the Home types the screens and view models use.
 */
class HomeRepository(private val spaces: SpaceRepository) {

    /** Every Home, re-emitted whenever the Home table changes. */
    val homes: Flow<List<HomeListItem>> = spaces.spaces.map { list -> list.map { HomeListItem(id = it.id, name = it.name) } }

    /** The Home with the given [id], or null if there is none. */
    suspend fun get(id: String): HomeDetails? = spaces.get(id)?.let { HomeDetails(id = it.id, name = it.name, shape = it.kind) }

    /**
     * Creates a Home named [name] with the given [shape], returning its new id. The creator, the current Profile, is its
     * first member and its admin (docs/adr/ADR-014-home-membership.md).
     */
    suspend fun create(name: String, shape: String): String = spaces.create(name, shape)

    /** Renames the Home with the given [id] and changes its [shape]. */
    suspend fun update(id: String, name: String, shape: String) = spaces.update(id, name, shape)

    /** The current user leaves the Home with the given [id] (see [SpaceRepository.leave]). */
    suspend fun leave(id: String): LeaveResult = spaces.leave(id)

    /** Deletes the Home with the given [id]; its members are removed with it. */
    suspend fun delete(id: String) = spaces.delete(id)
}
