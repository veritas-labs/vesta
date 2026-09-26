# ADR-006: Multi-Document Sync — an Envelope for Demultiplexing Automerge Sync Messages

## Status

Proposed — 2026-09-26. Extends [[ADR-005]] — doesn't reopen the one-`Document`-per-Home decision, resolves the
"multi-document routing" gap ADR-005 left as Open Question 2. Builds directly on [[ADR-004]]'s `Document`/
`SyncState`-over-x0x mechanism, which stands unchanged underneath this.

Not yet implemented — no code exists for this yet, on `poc/automerge-over-x0x` or elsewhere. This ADR records the
design; implementation is follow-up work.

## Context

ADR-005 scoped Vesta's sync unit to one Automerge `Document` per Home, but explicitly declined to design how a
device handles more than one `Document` at once, framed around a device belonging to more than one Home:

> A device in >1 Home now normally needs >1 `Document` over one `X0xClient`; no envelope/document-id scheme
> exists yet to demux `directEvents` to the right `DocumentSyncSession`.

Discussing what a Home's data model actually looks like surfaced that this isn't only a multi-Home problem: a
single Home is very likely to want more than one document too — e.g. a `tasks` document, a separate `chores`
document, a `lists` document — rather than one document holding every feature's data as nested state. Splitting
by feature gives smaller sync payloads, lets one feature's document grow (or misbehave) independently of
another's, and echoes the per-table sync-boundary thinking [[ADR-001]] used for cr-sqlite before it was retired
— though ADR-001's `private`/`shared` classification doesn't carry over as written, and this ADR takes no
position on where a given feature's data should live; that's a data-modeling call for whoever builds each
feature, not this ADR's job.

The upshot: "one Home, several documents" makes the routing gap ADR-005 flagged as a future multi-Home edge case
into the normal case for a single Home, immediately. Explicit user instruction (2026-09-26): design this now
rather than deferring it, since deferring it only means paying the same cost later with more call sites already
built against the single-document assumption.

### Why nothing below already solves this

- **`X0xClient.directEvents` delivers every inbound direct message, from any sender, to every subscriber.**
  There's no per-document (or per-anything) filtering at the x0x API level for direct messages.
- **`X0xDirectMessage.payload` is opaque bytes with no channel/topic field.** Contrast `X0xGossipMessage`, which
  does carry a `topic` — gossip pub/sub has a channel concept baked in; direct messaging doesn't.
- **An Automerge sync message (`Document.generateSyncMessage`'s output) is also just opaque bytes.** It doesn't
  reserve any space for a document identifier — two different `Document`s' sync messages are indistinguishable
  from their bytes alone.

So nothing at the x0x layer or the Automerge layer already tags a message with which document it's for; that has
to be added above both.

## Decision

### Document identity: a deterministic composite key, no registry

`documentId = "$homeId:$feature"` — e.g. `"3fae1c2e-…:tasks"`, `"3fae1c2e-…:chores"`. `homeId` is already known
to every member of a Home; `feature` is a small, fixed vocabulary defined in code (`"tasks"`, `"chores"`,
`"lists"`, …), not something that needs runtime discovery or a shared allocator — every client that ships a
given feature agrees on its name at compile time.

This deliberately doesn't support dynamically created documents (e.g. a user-defined custom list, if that's ever
wanted) — that would need an actual id-allocation-and-discovery mechanism, out of scope here (see Open
Questions). For the fixed, code-defined feature set this ADR is written against, a deterministic key is simpler
and needs no new distribution mechanism.

### Wire envelope: a length-prefixed document id in front of the untouched sync message

```
[1 byte: docId length N] [N bytes: docId, UTF-8] [remaining bytes: Automerge sync message, unmodified]
```

A one-byte length prefix caps `docId` at 255 bytes — comfortably enough for `<uuid>:<feature>` (~45 bytes) with
room to spare. A plain length-prefixed string rather than a registered integer code: traffic at this layer
already goes through HTTP + base64 + JSON at the x0x transport (see [[ADR-004]]), so a few extra bytes per sync
message is noise, and a string avoids maintaining a separate id↔code table that would itself need distributing
and versioning.

### Routing: one per-device hub replaces one `DocumentSyncSession` per document

[[ADR-004]]'s `DocumentSyncSession` (`shared/src/jvmMain/.../automerge/DocumentSyncSession.kt` on
`poc/automerge-over-x0x`) is one `Document` plus one `SyncState`-per-peer, over its own `directEvents`
subscription. Since `directEvents` only ever has one active subscription's worth of delivery semantics to work
with (every subscriber sees everything), running several `DocumentSyncSession`s side by side doesn't compose —
each would see, and need to filter, every other document's traffic too. This ADR replaces it with a single
`MultiDocumentSyncHub` per device, owning the one `directEvents` subscription directly:

```kotlin
class MultiDocumentSyncHub(
    private val client: X0xClient,
    private val scope: CoroutineScope,
) {
    private val documents = ConcurrentHashMap<String, Document>()                 // docId -> Document
    private val syncStates = ConcurrentHashMap<Pair<String, String>, SyncState>() // (docId, peer) -> SyncState
    private val onChanged = ConcurrentHashMap<String, () -> Unit>()
    private val knownSenders = ConcurrentHashMap.newKeySet<String>()

    suspend fun start() { client.directEvents(::onDirectMessage) }

    fun register(docId: String, document: Document, onDocumentChanged: () -> Unit = {}) {
        documents[docId] = document
        onChanged[docId] = onDocumentChanged
    }

    suspend fun connectTo(peerAgentId: String) {
        client.connectAgent(peerAgentId)
        documents.keys.forEach { pump(it, peerAgentId) }
    }

    /** Call after a local edit to the document identified by [docId] — not the other registered documents. */
    suspend fun pumpAll(docId: String) =
        syncStates.keys.filter { it.first == docId }.forEach { (_, peer) -> pump(docId, peer) }

    private suspend fun pump(docId: String, peer: String) {
        val document = documents[docId] ?: return
        val syncState = syncStates.computeIfAbsent(docId to peer) { SyncState() }
        while (true) {
            val msg = document.generateSyncMessage(syncState).getOrNull() ?: break
            client.sendDirect(peer, envelope(docId, msg))
        }
    }

    private fun onDirectMessage(message: X0xDirectMessage) {
        val sender = message.sender ?: return
        val raw = message.payload?.let { Base64.Default.decode(it) } ?: return
        val (docId, syncBytes) = parseEnvelope(raw) ?: return   // malformed envelope -> drop
        val document = documents[docId] ?: return               // not a document we host -> drop
        runBlocking(scope.coroutineContext) {
            if (knownSenders.add(sender)) client.connectAgent(sender)   // dial-back once per sender, not per doc
            val syncState = syncStates.computeIfAbsent(docId to sender) { SyncState() }
            document.receiveSyncMessage(syncState, syncBytes)
            onChanged[docId]?.invoke()
            pump(docId, sender)
        }
    }
}
```

Two refinements over a naive per-document port of `DocumentSyncSession`:

- **The accept-side dial-back (ADR-004's one surviving gotcha from the deleted `Repo` design) is tracked once
  per sender, not once per `(docId, sender)`.** `syncStates` is now keyed by `(docId, peer)`, so the old
  `syncStates.containsKey(sender)` check would call `connectAgent` redundantly on the first message for every
  document the same peer happens to sync. `knownSenders` decouples "have we dialed this peer back" from "which
  documents are we syncing with them."
- **An unrecognized `docId` is silently dropped, not an error.** A peer ahead on app version (a document for a
  feature this build doesn't know about) or a peer not participating in a given feature's document shouldn't
  crash or log noisily — the hub just ignores that message. Good enough for now; revisit alongside the ACL gap
  below if it needs to be tightened (e.g. to distinguish "unknown feature" from "known feature, not entitled").

### Local edits pump only their own document

`pumpAll(docId)` is scoped to the document that actually changed, rather than a single global `pumpAll()` that
re-checks every registered document's `SyncState` on every local edit. This is one of the actual payoffs of
splitting a Home's data into several documents in the first place — editing `tasks` doesn't generate any traffic
for `chores`' sync states.

## Consequences

- **Resolves ADR-005's Open Question 2 in full, not just the multi-document-per-Home half.** A device in more
  than one Home is the same mechanism as a Home with more than one document — both are just multiple entries in
  `MultiDocumentSyncHub.documents`, demuxed by the same envelope. No separate design needed for the multi-Home
  case.
- **`DocumentSyncSession` (ADR-004) is retired in favor of `MultiDocumentSyncHub`, not merely renamed.** The
  public shape changes: `register(docId, document, onChanged)` replaces the constructor taking a single
  `Document`, and `pumpAll()` becomes `pumpAll(docId)`. Anything built against the single-document API (the
  `poc/automerge-over-x0x` demo) needs updating, not just relinking.
- **Does not add a sync-time ACL.** A well-formed envelope for a `docId` the hub recognizes is accepted from any
  sender that can reach this device over x0x, regardless of Home/group membership — ADR-004/ADR-005's "no ACL on
  who may sync" gap is unchanged, and arguably sharper now: one peer connection can legitimately carry traffic
  for documents belonging to entirely different Homes, so there's more for an unauthorized sync to reach into if
  this isn't addressed later.
- **Introduces a small versioning requirement.** Every client needs to agree on `feature` name strings across
  app versions for `documentId` construction to line up; a mismatch doesn't corrupt anything (unknown `docId` →
  dropped, per Decision) but does mean two out-of-sync clients silently fail to sync a given feature rather than
  erroring loudly. Acceptable for now; worth a health-check/diagnostic later if version skew in the field becomes
  real.
- **Slightly more per-peer state.** `SyncState` is now keyed per `(docId, peer)` rather than per `peer` — a
  device syncing 3 documents with the same peer holds 3 `SyncState`s instead of 1. Genuinely more memory, but
  each `SyncState` is small and this is the direct cost of the payload-scoping the split documents were for.

## Open Questions

1. **Dynamic/user-created documents.** If a feature ever needs documents that aren't a fixed compile-time set
   (e.g. a user-created custom list per Home), the deterministic `homeId:feature` key doesn't work — that needs
   real id allocation and a way for peers to discover a new document's id, not designed here.
2. **Sync-time ACL.** Tying `onDirectMessage`'s acceptance of a `(docId, sender)` pair to actual group
   membership (only accept sync for a Home's documents from that Home's current roster) — carried over from
   ADR-004/ADR-005, unresolved, now touching more surface area per the Consequences above.
3. **Envelope versioning.** The 1-byte length prefix has no version byte or reserved flags of its own — fine for
   the scheme as designed here, but if the envelope format itself needs to change later (e.g. a varint length, or
   per-message flags), there's currently nothing to detect which format a given message is using. Not a problem
   today; worth a version byte if this needs to change post-deployment.
4. **Where documents get registered relative to Home membership and persistence.** This ADR designs the
   wire/routing layer only — not when/how a `Document` is created, loaded from ADR-005's SQLite BLOB, and
   `register`ed with the hub as a device joins a Home or a new feature ships, nor what happens to a document's
   entry in the hub when a device leaves a Home.

## References

- [[ADR-004]] — the `Document`/`SyncState`-direct-over-x0x mechanism and `DocumentSyncSession` this ADR replaces
  with `MultiDocumentSyncHub`; the accept-side dial-back gotcha this ADR's `knownSenders` set preserves.
- [[ADR-005]] — the one-Document-per-Home scoping this ADR doesn't reopen, and the source of Open Question 2 this
  ADR resolves.
- [[ADR-001]] — the retired cr-sqlite table-boundary thinking that motivated splitting a Home's data by feature
  in the first place, though its `private`/`shared` classification doesn't carry over as written.
- `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/x0x/X0xModels.kt` — `X0xDirectMessage` (no channel/topic
  field) vs. `X0xGossipMessage` (has `topic`) — the asymmetry that motivates the envelope.
- `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/x0x/AbstractX0xClient.kt` — `sendDirect`/`directEvents`
  implementation this ADR's envelope sits on top of, unmodified.
