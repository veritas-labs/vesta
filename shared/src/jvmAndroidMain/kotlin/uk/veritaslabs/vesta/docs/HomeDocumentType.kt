package uk.veritaslabs.vesta.docs

import kotlinx.coroutines.flow.Flow

/** Who a [HomeDocumentType] is shared with. */
internal enum class Scope {
    /** Synced with the user's paired devices and the Home's members and hubs, carried by the mailbox, acknowledged. */
    Shared,

    /** Synced with the user's own paired devices only: no mailbox, no other members. */
    Private,
}

/**
 * One kind of per-Home document (Pantry, Tasks, ...), described once so the engine never has to name it.
 *
 * Its document id is `"$homeId:$feature"` (ADR-006). The feature strings are on the wire and in stored rows, so they
 * must never change for an existing type.
 *
 * @property feature the id suffix, for example `pantry`; must not contain `:` (the registry parses on the last one).
 * @property module the Sync activity module name events for this type are filed under.
 * @property scope see [Scope].
 * @property open the document for a Home, or null when there is no such Home or this device does not have it yet.
 * @property localChanges the Home id each time this type's document is edited on this device.
 * @property afterReceive runs after a sync message for this type has been applied (the roster projects and publishes).
 */
internal class HomeDocumentType(
    val feature: String,
    val module: String,
    val scope: Scope,
    val open: suspend (homeId: String) -> HomeDocument?,
    val localChanges: Flow<String>,
    val afterReceive: suspend (homeId: String) -> Unit = {},
)
