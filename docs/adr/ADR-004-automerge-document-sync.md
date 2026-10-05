# ADR-004: Automerge Document Sync — `Document`/`SyncState` directly over x0x

## Status

**Extended by [[ADR-005]]** (2026-09-26) — not superseded: the `Document`/`SyncState`-direct-over-x0x decision
below stands unchanged. ADR-005 scopes it to one `Document` per Home, decides its persistence (Open Question 1,
a SQLite BLOB), generalizes its sync to fan out over a Home's x0x group roster (Open Question 3), and resolves
Open Question 8 in Automerge's favor (cr-sqlite retired).

Proposed. Everything below "What's built" is implemented and tested (spike-quality, on the `poc/automerge-over-x0x`
branch, not `main`). This ADR does **not** settle Automerge vs. cr-sqlite (see Context) — it records the integration
decisions made *within* the Automerge spike itself. This is the third revision of that decision: §"Designs tried"
below documents two earlier working implementations of *this same current design* — an `org.automerge.repo.Repo` +
custom `Storage`/`Dialer`/`Transport` adapter pair, and before that a hand-rolled chunk-log store — both built,
tested, and deliberately thrown away.

## Context

[[ADR-001]] commits Vesta's device/Home sync to cr-sqlite CRDT tables, and [[ADR-002]]/[[ADR-003]] design the x0x
transport around that choice. Separately, on the `poc/automerge-over-x0x` branch, Automerge — a different CRDT
technology, operating on whole documents (nested JSON-like structures) rather than SQL table rows — was added as a
second dependency and integrated against x0x. This ADR does not have a recorded product rationale for evaluating
Automerge *instead of or alongside* cr-sqlite; it only documents what was built once that evaluation started, and
is explicit that the two approaches have not been compared or chosen between (see Open Questions).

### Designs tried, in order

1. **A hand-rolled chunk-log `Storage`** (`AutomergeSqliteStorage` + an `AutomergeChunk` table: `seq`,
   `documentId`, `bytes`, `createdAt`) built against `org.automerge` 0.0.7, which at the time had no
   `Storage`/`Dialer`/`Transport` contracts at all — those are a JS `automerge-repo` concept the Java bindings
   hadn't yet exposed. Worked (tested against a real `org.automerge.Document`), but was pure guesswork about what
   shape upstream might eventually want. Deleted.

2. **A hand-rolled `SyncState`-based peer sync** (`AutomergeSyncPeer`, `AutomergeX0xSync`, `X0xDirectChannel`),
   built once 0.0.9 was pinned, using `Document`'s own `generateSyncMessage`/`receiveSyncMessage` primitives
   directly — essentially the same shape as §"Decision" below, arrived at independently and *rejected* at the
   time. Presented as a recommendation (`Repo`/`Storage`/`Dialer`/`Transport` had just landed the same day as the
   0.0.9 version bump, far less battle-tested than `Document`/`SyncState`, present since 0.0.1), the user's
   explicit instruction reversed it: *"I would rather adhere to the automerge Repo interface — even if it means
   adding bridge code."* Deleted in favour of design 3.

3. **`org.automerge.repo.Repo` + `SqliteRepoStorage` (a `Storage` implementation) + `X0xRepoNetworkAdapter` (a
   `Dialer`/`Transport`/`AcceptorHandle` implementation).** Built, tested (including two real `x0xd` daemons, not
   just fakes), and for a while treated as this ADR's decision. Real, working code — not a false start like design
   1 — but ultimately rejected once actually driven from a live two-window demo; see §"Why `Repo` was rejected"
   below. Deleted 2026-09-24.

4. **Current: `Document`/`SyncState` directly, via a single small `DocumentSyncSession` class — no
   `org.automerge.repo` at all.** See §"Decision".

The path from 3 back to (a refined version of) 2 was an explicit user call, not a technical dead end discovered by
elimination: *"it was my mistake to try and enforce the 'storage adapter' and 'network adapter' concepts on you.
These concepts belong to the automerge-repo project and are designed for the web ecosystem. As we're using x0x for
the transport we do not need this."* The reasoning holds up: `automerge-repo`'s `Dialer`/`Transport`/`Storage`
abstraction exists so a browser-shaped app can plug in arbitrary transports (WebSocket, BroadcastChannel, IndexedDB
storage, …) it doesn't otherwise control. x0x is not an arbitrary transport slotted in from outside — it's *this
app's own* peer-to-peer network, already reliable, ordered, and durable-ACKed end to end (see [[ADR-002]]). Wrapping
it in an abstraction designed for pluggable, uncontrolled transports added a translation layer with its own failure
modes (see below) for no corresponding benefit.

### Why `Repo` was rejected

Three real, reproducible bugs were found running design 3 against two actual `x0xd` daemons (not just the
in-process fakes both `X0xRepoNetworkAdapterTest` and `TaskListDemoViewModelTest` used, which never exercised real
network timing or `x0xd`'s actual routing behavior):

- **The accepting side of a connection must also call `connectAgent` toward the dialer.** `X0xClient.sendDirect`
  from a side that never itself called `connectAgent` toward its peer returns `200 OK` from its *own* daemon (the
  send is accepted locally) but the message never reaches that peer's `/direct/events` at all — silently dropped,
  not queued or retried. This is an x0x-level fact, not specific to `Repo` — it still applies to design 4 and is
  handled there too (see §"Decision").
- **`Repo`'s own automatic dial retry silently orphaned in-flight handshakes.** `DialerConfig.defaults()` retries
  `Dialer.connect()` on a backoff, sometimes several times before (or even after) a handshake actually succeeds.
  The adapter's `Dialer.connect()` created a *new* `Transport` object on every call; each retry's new `Transport`
  overwrote the previous one in the adapter's per-peer map, so a reply addressed to a connection `Repo` was still
  mid-handshake with could land on a newer, unrelated `Transport` instead — hanging `DialerHandle.onEstablished()`
  intermittently, with no error on either side. Fixed (make the map `computeIfAbsent`-idempotent per peer) before
  design 3 was abandoned, but is a good example of the class of bug this abstraction invited: `Repo`'s retry
  semantics and the adapter's object lifecycle had to independently agree on something neither side stated
  explicitly.
- **Two peers dialing each other simultaneously broke one direction.** If both sides call `Repo.dial()` toward each
  other (rather than one side dialing and the other only accepting), the accepting adapter's inbound-message
  handler found it already had a `Transport` for that peer (from its *own* outbound dial) and fed the peer's
  separate, independent handshake attempt into that already-established `Transport` — which `Repo`'s protocol state
  machine wasn't expecting, hanging that direction. Confirmed with a fast in-process fake network (not just real-
  network flakiness) — a genuine logic bug, not timing. Never fixed; worked around by dialing from only one side,
  which is exactly the kind of caller-side discipline a good abstraction shouldn't require.

None of these are criticisms of `org.automerge.repo` on its own terms — they're evidence that its network model
(automatic reconnection, retry backoff, a `Dialer`/`Transport` pair per logical connection attempt) is designed
around assumptions x0x's own connection model doesn't share, and reconciling the two added real, debugged-the-hard-
way complexity with no payoff once x0x was already doing reliable delivery itself.

## Decision

### Build directly on `org.automerge.Document`/`SyncState` — no `org.automerge.repo`

`Document.generateSyncMessage(SyncState)` / `Document.receiveSyncMessage(SyncState, bytes)`, one `SyncState` per
peer, driven by plain x0x direct messaging (`X0xClient.connectAgent`/`sendDirect`/`directEvents`). No `Repo`, no
`RepoConfig`, no `Storage`, no `Dialer`/`Transport`/`AcceptorHandle`. This is core `org.automerge` API, present
since 0.0.1 — not the newer `org.automerge.repo` package design 3 used.

### `DocumentSyncSession`: the entire "network integration" is one ~90-line class

`shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/DocumentSyncSession.kt`. Constructor takes the `Document`
to keep synced, an `X0xClient`, a `CoroutineScope`, and an optional `onDocumentChanged` callback (fired after an
incoming sync message applies — not for local edits, which the caller already knows about).

- **`start()`** — opens the one shared `/direct/events` subscription. Call once, before `connectTo`.
- **`connectTo(peerAgentId)`** — `connectAgent`, then sends this side's opening sync message. Safe to call more
  than once (idempotent — see below).
- **`pumpAll()`** — call after *any* local change to the document, to propagate it to every peer already syncing.
  Nothing pushes local changes automatically; the caller must call this.
- **`onDirectMessage`** (private, the `directEvents` callback) — the first time it sees a message from an unknown
  sender, calls `connectAgent` back toward that sender *before* processing, establishing this side's own reply
  route (see §"Why `Repo` was rejected" — this is the one gotcha that survived the rewrite, now handled in one
  place rather than relied on at every call site). Then `receiveSyncMessage` + re-pump toward that sender.

Each peer's `SyncState` lives in a `ConcurrentHashMap<agentId, SyncState>`, created via `computeIfAbsent` — the
lesson from design 3's retry-clobbering bug, kept even though `Repo`'s own retry machinery (the thing that made the
bug possible) is gone: idempotent-per-peer state is just more obviously correct, cheap to keep.

`X0xClient.directEvents`'s callback is a plain (non-suspend) function, so `onDirectMessage` bridges into suspend
x0x/Automerge calls via `runBlocking` on the caller-supplied `CoroutineScope`'s context — same bridge design 3
needed, minus everything else it did. This also happens to keep messages from the same peer processed one at a
time, in order, on the SSE reader's own thread — required, because `SyncState`'s own kdoc says the sync protocol
"is designed to run over a reliable in order stream."

### Confirmed x0x-level facts (apply regardless of what's built on top)

- **The accepting side must dial back.** See above — handled inside `DocumentSyncSession`, not left to callers.
- **Real message latency is highly variable.** Measured directly (both via this app's HTTP client and via the raw
  `x0x` CLI, against two real `x0xd` daemons, same machine): anywhere from under a second to 20+ seconds for a
  single `sendDirect` round trip, occasionally an outright timeout. `/agents/connect` reporting `outcome: Direct`
  does not mean subsequent `sendDirect` calls take the fast path — `path: gossip_inbox` was observed even for
  "Direct" connections. Confirmed as a genuine environment/network characteristic (not a client bug) by reproducing
  it with the plain `x0x` CLI directly, independent of this app's Kotlin HTTP client. **Accepted as known** for
  this dev environment rather than worked around in application code; see Open Questions for what that implies for
  a production timeout/retry policy.

## What's built (2026-09-24, `poc/automerge-over-x0x` branch)

- `shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/DocumentSyncSession.kt` — see §"Decision".
- `shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/TaskListDocument.kt` — plain `Document`/`Transaction`
  helpers for this demo's shape (`title: String`, `tasks: List<String>`): `readTaskList()`, `setTitle()`,
  `addTask()`. No dependency on `DocumentSyncSession` or `org.automerge.repo`.
- `shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/TaskListDemoViewModel.kt` — one `Document` (starts
  empty, in-memory only — see Consequences), one `DocumentSyncSession`, exposes a flat `TaskListDemoUiState`.
- `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/ui/screens/TaskListDemoScreen.kt` — the whole demo is one
  screen, unlike design 3's two-screen create/join flow: every device already has a document (starts empty), and
  connecting to a peer's agent id merges the two via the sync protocol, so there's no separate document identifier
  to create, share, or look up. Editable title field + "Set" button, add-task field, task list, and a "Sync"
  section (this device's agent id, a peer-agent-id field, "Connect", and a live connection-status line).
- Tests: `DocumentSyncSessionTest.kt` (fast, deterministic, in-process fake x0x network — including a regression
  test for the accept-side-must-dial-back gotcha) and `AutomergeDirectSyncTest.kt` (same proof, two real `x0xd`
  daemons — skips rather than fails when they're not running). `uk.veritaslabs.vesta.x0x.X0xDirectConnectionTest` +
  `RealX0xDaemons.kt`/`FakeX0xNetwork.kt` prove/support plain x0x connectivity independent of Automerge entirely.
- **Manually verified working end to end**: two desktop app windows, two real `x0xd` identities, title and tasks
  set on either side after connecting sync to both, live, repeatedly.

## Consequences

- **No persistence.** The `Document` is in-memory only; every process restart starts from empty. `Document.save()`/
  `Document.load()` and `SyncState.encode()`/`decode()` exist and are the obvious next step (see Open Questions) —
  deliberately not attempted in this pass, to keep the sync-mechanism rewrite and the persistence question separate.
- **No answer for more than one document.** This PoC hardcodes exactly one `Document` per process. `X0xClient
  .directEvents` delivers every inbound message to every subscriber; nothing in an Automerge sync message
  self-identifies which document it belongs to. Running two `DocumentSyncSession`s off one `X0xClient` today would
  have both see every message and neither know which are actually theirs. Not solved here — see Open Questions.
- **No multi-peer (>2) fan-out tested.** `DocumentSyncSession`'s per-peer `SyncState` map is structurally ready for
  N peers, but every test and the manual demo only ever used exactly two.
- **No automatic reconnection.** `Repo`'s `DialerConfig` retry/backoff is gone along with `Repo` itself — a
  deliberate trade (see §"Why `Repo` was rejected"), but the result is that `connectTo` is one-shot: nothing
  detects a dropped peer or retries. If resilience is wanted, it has to be built deliberately now, informed by the
  bugs design 3 hit trying to get it for free.
- **No security/authorization model.** Any agent id that successfully calls `connectAgent` and sends well-formed
  sync messages can merge arbitrary content into the document. No different from design 3's exposure, but worth
  restating now that this is the design going forward.
- **iOS still unsupported; Android still untested.** `org.automerge`'s JNI bindings cover JVM and Android only, no
  iOS. `DocumentSyncSession` is simpler than design 3's adapter and *might* port to Android more easily, but this
  has not been tried — same caveat design 3 carried.
- **The standalone x0x client's HTTP request timeout (10s, hardcoded) is a real rough edge**, exposed by the
  latency variance above — occasionally trips even for a request that would have succeeded given more time. Not
  changed in this pass; flagged for whoever tackles Open Question 7.

## Open Questions — a checklist for implementing this properly

1. **Persistence.** Wire `Document.save()`/`load()` to SQLite — a single BLOB column is enough (no need for design
   3's generic key/value `Storage` contract; that shape existed to satisfy `Repo`'s `Storage` interface, which no
   longer exists here). `SyncState.encode()`/`decode()` lets a resumed connection skip re-deriving shared history
   from scratch — worth persisting per known peer alongside the document.
2. **Multi-document routing.** The real design gap. Needs some envelope around each sync message (e.g. a document
   id prefix) so one `directEvents` subscription can demux inbound bytes to the right `DocumentSyncSession`/
   `SyncState` — not attempted here, and not obviously simple: whatever scheme is chosen has to stay compatible
   with `Document`'s sync-message format, which doesn't reserve space for this today.
3. **Multi-peer group sync.** For a document meant to be shared by more than two devices (e.g. a Home's full
   member list) — no fan-out/broadcast equivalent to [[ADR-002]]/[[ADR-003]]'s MLS group exists here. Every
   relationship is an explicit, separate `connectTo()`. Needs its own design if pursued.
4. **Real device/peer discovery.** [[ADR-003]]'s pairing flow was never built (its `DevicePairing.kt` stubs were deleted; see [[ADR-012]]). This
   PoC's "paste an agent id" is a stand-in, not a production mechanism.
5. **Reconnection and liveness.** Nothing here detects a peer going offline or retries a dropped connection. Decide
   whether/how to rebuild this deliberately — informed by, not repeating, design 3's retry-object-lifecycle bug.
6. **Security/authorization.** No ACL on who may sync a given document. Needs a real trust model — tying into
   [[ADR-002]]/[[ADR-003]]'s existing group/contact trust machinery is the obvious starting point — before this is
   anything but a toy.
7. **Production HTTP client tuning.** Reassess `StandaloneX0xClient.jvm.kt`'s fixed 10-second request timeout (and
   whether/how to retry) against real (non-sandboxed-dev) network conditions.
8. **Automerge vs. cr-sqlite is still not decided.** Unchanged from the previous revision of this ADR: whether
   Vesta ships with one, the other, or both for different data shapes is unresolved and not this ADR's concern to
   settle.
9. **Multiplatform reach.** Android: untested, though now simpler code than design 3's adapter. iOS: blocked
   entirely on upstream Automerge having no iOS bindings; unchanged from every previous revision of this ADR.

## References

- [[ADR-001]] — the table-level sync boundary and UUID-primary-key decisions made for the cr-sqlite direction;
  this ADR's document-CRDT approach doesn't use either concept and hasn't been reconciled with them.
- [[ADR-002]] — the Home-scoped x0x group + gossip/DM design; this ADR's direct-messaging-only sync doesn't reuse
  its group/broadcast layer (see Open Question 3).
- [[ADR-003]] — the per-user device-sync counterpart to ADR-002, and the still-unbuilt pairing flow this ADR's
  manual agent-id paste stands in for (see Open Question 4).
- `shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/DocumentSyncSession.kt`,
  `TaskListDocument.kt`, `TaskListDemoViewModel.kt` — the current design's production code.
- `shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/automerge/DocumentSyncSessionTest.kt`,
  `AutomergeDirectSyncTest.kt`, and `shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/x0x/X0xDirectConnectionTest.kt`
  — tests proving the current design, fast (fake network) and real (two live `x0xd` daemons).
- `org.automerge:automerge` / `org.automerge:androidnative`, version 0.0.9 (Maven Central) — specifically
  `Document`/`SyncState`/`Transaction` (core, present since 0.0.1), *not* the `org.automerge.repo` package this
  ADR now deliberately avoids.
