# ADR-013: Syncing a user's data between their paired devices

**Status:** Accepted (2026-10-01). Builds on [[ADR-012]] (who the devices are), [[ADR-005]]/[[ADR-006]] (Automerge over
x0x direct messages). Does not cover sync between the members of a Home, which comes later.

## Context

[[ADR-012]] gives each user a roster of their own devices (the `DeviceSync` x0x group) but replicates nothing. Two things
need to reach every paired device: the list of Homes (a plain `Home` SQLite table, not a document) and each Home's
Pantry (already an Automerge document, one per Home).

## Decision

**Transport.** Automerge sync messages in x0x direct messages, wrapped in the [[ADR-006]] envelope
(`[1 byte id length][document id][sync message]`) by `SyncEnvelope`. The peers are the devices in
`SyncedDeviceRepository`. A message is applied only if it is `verified` and its sender is in that roster, so membership
of the DeviceSync group is the access control (the sync-time ACL [[ADR-004]]/[[ADR-005]] left open, for this scope).
Every device syncs with every other, so nobody relays. No `MultiDocumentSyncHub` class: `DeviceDataSyncService` is the
hub, with the per-`(document, peer)` sync states kept in memory.

**Home list.** A per-user Automerge document, id `user:homes`, stored in the new `UserDocument` table. Its root map has
one entry per Home id (name, shape, createdAt, `deleted`). There is deliberately no wrapping `homes` map: two devices
creating it concurrently would conflict and one would lose its Homes. A deletion is a tombstone so it propagates.
`HomeRegistry` mirrors it into the `Home` table both ways: `HomeRepository` reports each local change through
`HomeChangeListener` (commonMain), and changes arriving from a peer are projected into the table. Homes that existed
before pairing are imported into the registry at startup.

**Pantries.** Each Home's existing Pantry document, id `$homeId:pantry`, synced as is.
`PantryDocumentSource` gained `generateSync`/`receiveSync` and an `onLocalChange` callback; `PantrySources` exposes
`localChanges`. The UI and sync must share one `PantrySources` per database (Android now does too, via
`AgentCommandService`). `newPantryDocument()` creates the `items` and `categories` maps, so **only the device that
created the Home seeds the document**: a Home that arrives from a peer gets an empty stored document
(`storeEmptyPantry`) and fills from the creator's. Otherwise every device would have its own competing root maps and a
merge would keep only one. The creator seeds before the registry entry is written.

**Ordering.** The registry and a Pantry sync side by side, so a Pantry message can arrive before its Home. Such messages
are held (up to 64 per Home) and replayed once the registry has created the Home.

**Offline devices.** A direct message to an offline device can vanish without an error, and Automerge's sync state assumes
what it sent arrived. Every 30 s, and when the roster changes, all sync states are restarted: a small handshake per
document and peer, and no changes are re-sent for a device that is already up to date.

## Consequences

- Homes and Pantries (items, stock, categories) stay in step across a user's devices, concurrently and offline.
- Sync states aren't persisted ([[ADR-005]] suggested it). Restarting them every round makes that moot until the
  handshake cost matters.
- Concurrent edits of a field are last-writer-wins (Home name/shape). Pantry stock correction keeps the caveat in
  [[ADR-008]].
- A Pantry edited on a device *before* its first sync, for a Home that device didn't create, would seed its own root maps
  and conflict. It can only happen if a Home arrives and is edited before the first Pantry message; not guarded.
- Removing a device stops future sync only ([[ADR-012]]); nothing is wiped from it. A removed device's Homes stay on it.
- Everything is decrypted on the device and sent over end-to-end encrypted direct messages. Messages from a device that
  is still paired but compromised are trusted.
- JVM and Android only (Automerge); iOS has no sync yet. Agent profile, settings and other data aren't synced.

## References

- `DeviceDataSyncService`, `HomeRegistry`, `SyncEnvelope`, `HomeRegistryDocument`; `DeviceDataSyncServiceTest`
  (two devices over the in-memory mesh).
