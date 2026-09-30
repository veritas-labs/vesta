# Implementation plan: agent "add to Pantry" command over x0x (ADR-009)

Written 2026-09-30 so a fresh session can implement [ADR-009](../adr/ADR-009-agent-commands-over-x0x.md) without
re-deriving context. Read the ADR first (message format, reply format, behaviour rules 1–6); this plan says **how to
build it in this codebase**, in what order, and how to verify it. Desktop (JVM) is the target; Android/iOS must still
compile where they can.

## Goal

An AI agent on this machine, running as its own x0x instance, sends an ActivityStreams `Add` of a `schema:Product` as
a direct message. The running Vesta desktop app adds it to the named Home's Pantry (stock += quantity), the Pantry
screen updates live, and Vesta replies `Accept`/`Reject`.

## State of the repo (verified 2026-09-30)

- `main` has the full x0x client (cherry-picked `b9439d9`): `X0xClient.directEvents(onMessage)` returning an
  `X0xEventStream`, `sendDirect(agentId, bytes)`, `X0xDirectMessage {sender, machineId, payload(b64), receivedAt,
  verified, trustDecision}`. Transport primitives: `httpRequest`, `openSseStream` (`expect`, in
  `shared/src/commonMain/.../x0x/X0xClient.kt`; JVM actual in `jvmMain/.../StandaloneX0xClient.jvm.kt`, Android/iOS
  actuals in `EmbeddedX0xClient.{android,ios}.kt`).
- **`openSseStream` has no "closed / dropped" signal.** The JVM reader thread just ends when `readLine()` returns
  null. Reconnect logic needs one (WP1).
- Pantry code: `shared/src/jvmAndroidMain/.../automerge/PantryDocument.kt` (`upsertPantryItem`, `adjustStock`,
  `readPantryItem`, `pantryDedupKey`) and `PantryDocumentSource.kt` (`mutate`, `openPantryDocumentSource`).
  `PantryDocumentSource` **must be the only reader/writer of its `Document`** (its kdoc) — see WP3, this drives the
  design.
- `VestaApp` (`shared/src/commonMain/.../App.kt`) creates the `VestaDatabase` inside the composable and opens a
  Pantry source per selected Home via `produceState` + the `openPantry` lambda; desktop wiring is
  `desktopApp/src/main/kotlin/uk/veritaslabs/vesta/main.kt`.
- SQLDelight: schema in `shared/src/commonMain/sqldelight/uk/veritaslabs/vesta/db/*.sq`; migrations in
  `.../sqldelight/migrations/` (currently only `1.sqm`, "1 → 2", which added `AutomergeDocument`; so the next
  migration is `2.sqm`, "2 → 3"). Every `.sq` table must be mirrored by a migration. The database is async
  (`awaitAsOneOrNull` etc.).
- `PantryItemEditScreen` requires non-blank title, primary category and package size, but the Pantry document layer
  itself accepts empty strings.
- Test conventions (project memory, must follow): every test method has `// Arrange`, `// Act`, `// Assert` comments;
  `kotlin.test` + `runBlocking`; in-memory DB via `openInMemoryDatabase()`; jvm tests in `shared/src/jvmTest`.
- The ADR and this plan are untracked files; the cherry-pick of the client is already a commit on `main`.

## Decisions taken in this plan (deviations/clarifications to ADR-009 — fold back into the ADR when done)

1. **Missing category/size.** The Pantry UI forces both; the command's are optional. If `schema:category` is absent →
   primary category `"Uncategorized"`; if `schema:size` absent → `""` for a new entry. For an **existing** entry,
   omitted optional fields keep their current values (see WP3, `upsertPantryItem` blanks omitted
   `secondaryCategory`/`barcode` and overwrites the rest).
2. **Allowlist v1** = an `AgentAllowlist` interface with a simple implementation reading the env var
   `VESTA_AGENT_ALLOWLIST` (comma-separated x0x agent ids; empty/unset ⇒ accept nothing). No DB table, no UI yet.
   (ADR open question 1.)
3. **Idempotency table** is keyed `(senderId, commandId)` — *not* by Home — because rejected commands (e.g. unknown
   Home) have no valid Home row for a foreign key. It stores the exact reply JSON so a duplicate command gets the same
   reply re-sent. There is a small crash window between applying the effect and recording it; accepted for v1.
4. **One `PantryDocumentSource` per Home, shared** between the UI and the command handler via a registry (WP3);
   otherwise the two would hold divergent in-memory copies of the same Automerge document.

## Work packages (do in this order; each ends green with its tests)

### WP1 — SSE close signal + resilient event hub  (`commonMain`, + platform actuals)

- Extend `openSseStream(url, bearerToken, onEvent, onClose: (Throwable?) -> Unit = {})` and
  `X0xClient.directEvents(onMessage, onClose = {})` (and `gossipEvents` for symmetry; keep the parameter defaulted so
  existing callers compile). `onClose` fires exactly once when the stream ends by server close, IO error, or explicit
  `close()` (pass the error or null). Update the JVM actual (reader thread's `finally`); Android/iOS actuals: make the
  equivalent change — **the iOS actual cannot be compiled on this Linux machine, so change it minimally and mirror the
  JVM logic; say so in the commit message**.
- New `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/x0x/X0xEventHub.kt`:
  `class X0xEventHub(private val client: X0xClient, backoff: Backoff = ...)` exposing
  `val directMessages: Flow<X0xDirectMessage>` (a `callbackFlow` over `directEvents`, reconnecting with capped
  exponential backoff, e.g. 1 s → 30 s, resetting after a message or a stable connection; cancellation closes the
  stream). No CLI/daemon dependency: tests use a fake `X0xClient`.
- Tests (`commonTest` or `jvmTest`, fake client): emits messages; reconnects after `onClose`; backs off between
  failed opens (inject a controllable delay/clock via `kotlinx-coroutines-test`); stops on cancellation and closes the
  underlying stream.

### WP2 — Command model, parser, reply builder  (`commonMain`, pure Kotlin)

New package `uk.veritaslabs.vesta.agent` under `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/agent/`:

- `AgentCommand.kt`: `data class AddPantryItem(val commandId: String, val homeId: String, val title: String,
  val primaryCategory: String?, val secondaryCategory: String?, val packageSize: String?, val barcode: String?,
  val quantity: Int)`; `sealed interface ParseResult { Command(AddPantryItem); Invalid(commandId: String?, reason:
  String); NotACommand }`.
- `AgentCommandParser.kt`: `parse(payload: ByteArray): ParseResult` using `kotlinx.serialization.json` (already a
  dependency) — **no JSON-LD library**. Rules exactly as ADR-009: ≤ 16 KiB; valid JSON object; `@context` must equal
  `["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]` (else `NotACommand`, so
  future non-command traffic isn't rejected); `type == "Add"`; `id` non-blank; `target.id` matches
  `urn:vesta:document:<homeId>:pantry` (homeId non-blank); `object.type == "schema:TypeAndQuantityNode"` and
  `schema:typeOfGood.type == "schema:Product"`; `schema:name` non-blank; `schema:category` string **or** array of
  1–2 strings; `schema:amountOfThisGood` int 1..9999 default 1. Anything wrong ⇒ `Invalid` with a short human
  `reason` (becomes `Reject.summary`); keep `commandId` when it could be read so a reply is possible.
- `AgentReplies.kt`: `accept(replyId, commandId, key, title, stockAfter): String` and `reject(replyId, commandId,
  summary): String`, producing the JSON-LD from ADR-009 exactly (`inReplyTo`, `object`, `result`).
- Tests (`commonTest`): valid full command; minimal command (name only ⇒ quantity 1, category null); category as bare
  string; every invalid case above; non-JSON and wrong-context payloads ⇒ `NotACommand`; oversize payload; reply JSON
  round-trips and contains `inReplyTo`.

### WP3 — Handler, allowlist, idempotency, shared Pantry sources  (`jvmAndroidMain` + `commonMain` SQL)

- **Migration + table.** Add `shared/src/commonMain/sqldelight/uk/veritaslabs/vesta/db/AgentCommandLog.sq`
  (`senderId TEXT NOT NULL, commandId TEXT NOT NULL, replyJson TEXT NOT NULL, processedAt INTEGER NOT NULL, PRIMARY
  KEY (senderId, commandId)`; queries `selectReply`, `insert`) **and** `migrations/2.sqm` ("Version 2 -> 3") creating
  the same table — keep the two in sync (comment in each, as `AutomergeDocument.sq` does). Add a test that opens a
  database at version 2 and migrates, if the existing test setup supports it; otherwise just confirm
  `openInMemoryDatabase()` still works. (Reminder from project memory: `Schema.version`/`migrate()` behaviour was a
  known gap — check `DatabaseDriverFactory`/`AndroidxSqliteDriver` actually applies `2.sqm` for an existing local
  DB; if it doesn't, note it as a known limitation in the plan's follow-ups instead of fixing it here.)
- **`PantrySources`** (`jvmAndroidMain/.../automerge/PantrySources.kt`): `class PantrySources(database)` with
  `suspend fun forHome(homeId: String): PantryDocumentSource?` — a `Mutex`-guarded `MutableMap` cache that calls
  `openPantryDocumentSource`, and returns `null` if the `Home` row doesn't exist (query `Home` by id; look at
  `Home.sq`/`HomeRepository.get`). Change desktop/Android `openPantry` to `sources.forHome(homeId)` so the UI and the
  handler share one source per Home. (Sources are never evicted in v1; a deleted Home's entry is harmless.)
- **`AgentAllowlist`** (`commonMain/.../agent/`): `fun interface AgentAllowlist { fun allows(agentId: String): Boolean
  }`; `EnvAgentAllowlist` reading `VESTA_AGENT_ALLOWLIST` (put the env read behind an `expect`/parameter so it's
  testable; JVM/Android use `System.getenv`; iOS returns an empty list).
- **`PantryCommandHandler`** (`jvmAndroidMain/.../agent/`): constructor `(sources: PantrySources or a
  (String) -> PantryDocumentSource? lambda, allowlist, log: CommandLog, client: X0xClient, newId: () -> String, now:
  () -> Long)`. Method `suspend fun handle(message: X0xDirectMessage)` implementing ADR-009 behaviour 1–6 in order:
  1. drop silently (log) unless `verified == true`, `trustDecision == "accept"`, `sender` non-null and
     `allowlist.allows(sender)`;
  2. base64-decode payload, `AgentCommandParser.parse`; `NotACommand` ⇒ ignore; `Invalid` with a `commandId` ⇒
     record + `sendDirect` a `Reject`;
  3. duplicate `(sender, commandId)` in the log ⇒ re-send the stored reply, do nothing else;
  4. `sources.forHome(homeId)` null ⇒ `Reject("unknown home")`;
  5. apply via `source.mutate { doc -> ... }`: `key = pantryDedupKey(barcode, title)`;
     `existing = doc.readPantryItem(key)`; merge omitted optional fields from `existing` (decision 1); call
     `upsertPantryItem(...)`; `adjustStock(doc, key, +quantity)`; read back `stockCount`;
  6. build `Accept`, write it to the log, `sendDirect` to `sender` (catch/log delivery failure; never undo the effect).
- Tests (`jvmTest`, real `PantryDocumentSource(Document.load(newPantryDocument()))` + `openInMemoryDatabase()`, fake
  `X0xClient` recording `sendDirect` calls): adds a new item with stock = quantity and replies `Accept`; second add of
  same title increases stock and keeps existing category/size/barcode when omitted; barcode vs title dedup; duplicate
  `commandId` applies once and re-sends the identical reply; unverified / untrusted / non-allowlisted sender ⇒ no
  effect and **no reply**; unknown home ⇒ `Reject`; invalid payload ⇒ `Reject`; non-command JSON ⇒ ignored; reply
  delivery failure keeps the effect; the Pantry `contents` flow reflects the add (proves UI sharing).

### WP4 — Wiring  (`commonMain` App + desktop/Android entry points)

- `VestaApp` gets an optional `backgroundServices: (suspend CoroutineScope.(VestaDatabase) -> Unit)? = null` launched
  in a `LaunchedEffect(database)`, because the database is created inside the composable. Keep existing behaviour when
  null (previews).
- Desktop `main.kt` builds one `PantrySources` from the database it's handed (create it lazily in `openPantry` and in
  `backgroundServices` — both receive the same `VestaDatabase`, so hold the `PantrySources` in a `remember`/holder
  keyed by database, or restructure so a single lambda creates it once). Then: `val client = createX0xClient()`;
  `X0xEventHub(client).directMessages.collect { handler.handle(it) }` inside `backgroundServices`, with each message
  handled in its own `launch` so a slow reply doesn't block the stream.
- Android `MainActivity` gets the same wiring if it compiles cleanly; if the embedded daemon lifecycle makes that
  non-trivial, leave a clear `TODO` and mention it in the final summary (desktop is the target).
- Optionally move `X0xConnectivityIndicator`/`X0xStatusCard` off their private `createX0xClient()` to a shared client —
  **not required**; skip unless trivial.

### WP5 — Agent-side helper + docs

- `skills/vesta/scripts/agent-pantry-add.sh` (bash; note `GID` is special in zsh, use bash): args `--agent-instance NAME --to
  <vesta-agent-id> --home <homeId> --title ... [--category A --category B] [--size ..] [--gtin ..] [--qty N]`.
  Builds the JSON-LD from ADR-009, base64-encodes it, resolves the instance's API port/token from
  `~/.local/share/x0x-NAME/{api.port,api-token}`, calls `POST /agents/connect` then `POST /direct/send`, and retries
  with backoff for ~2 minutes on `recipient_ack_semantics_unavailable`. Prints the `request_id`.
- `docs/troubleshooting/agent-commands.md` (short): one-time setup (`x0x start --name vesta-agent`; `x0x trust set
  <vesta-agent-id> trusted --name vesta-agent`; `x0x trust set <agent-id> trusted`; export `VESTA_AGENT_ALLOWLIST`),
  the warm-up delay, how to see Vesta's agent id (`x0x agent`), and "silent drop" debugging (check Vesta's log,
  allowlist, trust on both daemons).
- Update ADR-009: status → Accepted/Implemented as appropriate, fold in the decisions above, and update
  `docs/adr` cross-references if needed. Save a project memory note when done (project convention).

## Verification

1. `./gradlew :shared:jvmTest` (all new tests plus existing Pantry tests) and `./gradlew :shared:compileKotlinJvm`.
   Also try `./gradlew :shared:compileDebugKotlinAndroid` (or the repo's Android compile task); iOS cannot be built on
   this Linux host — say so rather than claiming it works.
2. **Manual end-to-end** (the acceptance test), with the desktop app running (`./gradlew :desktopApp:run` — check the
   README for the exact task) and a Home with a Pantry open:
   - `export VESTA_AGENT_ALLOWLIST=<agent instance id>` before launching the app;
   - `x0x start --name vesta-agent`; mutual `x0x trust set ... trusted`; wait ~60 s;
   - `skills/vesta/scripts/agent-pantry-add.sh --agent-instance vesta-agent --to $(x0x agent | ...) --home <homeId> --title
     "Heinz Ketchup" --category Condiments --category Ketchup --qty 2`;
   - expect: item appears in the Pantry screen with stock 2; re-running increases it to 4 only for a *new* command id;
     re-sending the *same* id leaves it unchanged; the agent instance's `GET /direct/events` shows the `Accept`.
   - Negative: run with an empty allowlist ⇒ no effect, no reply.
3. **Clean up after manual testing:** `x0x stop --name vesta-agent`, delete `~/.x0x-vesta-agent` and
   `~/.local/share/x0x-vesta-agent`, remove the test agent's contact from the default daemon
   (`x0x contacts remove <id>`), and remove test items from the Pantry.

## Known risks / notes

- **x0x facts** (verified in a 2026-09-30 spike, all on `x0xd 0.45.0`): direct messages between two local daemons need
  mutual trust; the first send after starting a new instance may fail with `recipient_ack_semantics_unavailable`
  for up to ~1 min; `/direct/events` payloads are base64 in a flat JSON object. Riders were evaluated and rejected
  (group-only, plaintext, no DMs) — do not revisit unless the user asks.
- The unauthenticated-sender silent drop makes misconfiguration invisible to the agent; make sure the handler logs
  the reason at a visible level.
- `PantryDocumentSource.mutate` republishes the Pantry to the UI and persists to SQLite, but **nothing syncs to other
  devices yet** (ADR-005/006 are unbuilt) — out of scope.
- If the async SQLDelight driver or `Schema.version` handling prevents `2.sqm` from being applied to an existing dev
  database, developers must delete their local DB; note it, don't build migration infrastructure in this change.
- Commit in small steps per work package; commit messages end with the standard Co-Authored-By line. Do not push.
