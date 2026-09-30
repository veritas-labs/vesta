# ADR-009: Agent Commands over x0x — Schema.org + ActivityStreams Messages that Change the Pantry

## Status

Accepted — 2026-09-30; implemented (see "Implementation notes"). Independent of [[ADR-005]]/[[ADR-006]]'s document sync (it uses the same x0x direct-message
channel but a different message family, see "Routing"). Uses [[ADR-008]]'s Pantry document model as its first
target.

Implemented on `main` for desktop, 2026-09-30, with unit tests, and verified end to end the same day against real
x0x daemons (see "Implementation notes"). The Pantry screen updating live was not observed in that run.

## Context

The first thing to prove about Vesta's x0x integration is that an AI agent running on the same machine as the app can
send it a message and have the app act on it — concretely, add an item to a Home's Pantry. Today the app's only live
x0x integration is a `GET /health` poll for the connectivity indicator; no code listens for x0x events.

Decisions made by the user (2026-09-30) before this ADR:

- **Payload vocabulary:** use public schemas — Schema.org for objects, ActivityStreams 2.0 for actions — rather than a
  bespoke application schema. (The same convention the `colo-hygiene-inspection` skill already uses: Schema.org
  `Review` inside an Activity Streams `Announce`.)
- **Target Home:** the `homeId` travels in the message.
- **Trust:** the user runs `x0x trust` by hand for now.
- **"Add" semantics:** adding bumps the item's stock by the quantity; an explicit "set quantity" comes later.
- **Acknowledgement:** the app replies to every accepted command.

### x0x facts this design rests on (verified against `x0xd 0.45.0`, 2026-09-30)

- x0x has an owner/sub-agent model (its ADR-0039): an AI harness is either **ACP-attached** (runs its own x0x
  instance with its own key) or a **rider** (a scoped API token on the owner's daemon).
- A rider token can only reach `POST /groups/:id/send`, `.../secure/encrypt` and `GET /history` for explicitly
  granted groups; it cannot send direct messages. A rider cannot join an MLS-encrypted group (no key package), so a
  rider channel means a plaintext `signed_public` group, and the app would have to poll group messages (the client has
  no stream for them). The rider's identity appears only in a `rider_provenance` field — `author_agent_id` is the
  owner's own agent. Minting a rider needs harness-side Rust (`x0x::groups::sign_rider_delegation`); the CLI has no
  command for it. The user rejected this route after a spike.
- An ACP-mode agent is a second `x0xd` (`x0x start --name <instance>`). With `x0x trust set <id> trusted` on both
  daemons, `POST /agents/connect` then `POST /direct/send` delivers to the app daemon's `GET /direct/events` as flat
  JSON `{sender, machine_id, payload (base64), received_at, verified, trust_decision}`, and the reverse direction works
  for replies. Direct messages are end-to-end encrypted. The first send after starting a new instance can fail with
  `recipient_ack_semantics_unavailable` for up to about a minute, until the peers have exchanged capability adverts;
  it succeeds on retry.
- Trust between two agents of the same owner is not automatic.

## Decision

### Transport: direct messages to and from the app's daemon

- The AI agent runs as an **ACP-attached, separately-named x0x instance** and sends each command as a direct message
  (`POST /direct/send`) to the app daemon's agent id. Owner certification of that instance
  (`x0x owner agents issue --mode acp`) is optional and not required for this feature.
- The app receives commands on `GET /direct/events` and sends replies with `POST /direct/send` to the command's
  `sender`.
- The agent is responsible for retrying a send that fails with `recipient_ack_semantics_unavailable` (back off; give
  up after about two minutes).

### Message format: JSON-LD in compact form, Schema.org objects, ActivityStreams actions

The direct-message payload is a UTF-8 JSON document. Vesta does **not** run a JSON-LD processor (there is no good
Kotlin one, and expansion isn't needed): it requires the exact context below, then decodes the compact form as plain
JSON. Senders must therefore use the `schema:` prefix and the AS2 term names exactly as written.

```json
{
  "@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
  "type": "Add",
  "id": "urn:uuid:0b7c5a2e-3f0e-4d3a-9d54-5d6c1a3f9e10",
  "object": {
    "type": "schema:TypeAndQuantityNode",
    "schema:amountOfThisGood": 2,
    "schema:typeOfGood": {
      "type": "schema:Product",
      "schema:name": "Heinz Ketchup",
      "schema:category": ["Condiments", "Ketchup"],
      "schema:gtin": "5000157024671",
      "schema:size": "1 bottle"
    }
  },
  "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry"}
}
```

Field mapping to [[ADR-008]]'s Pantry item:

| Message field | Pantry field | Rule |
| --- | --- | --- |
| `id` | — | Required, unique per command; the idempotency key. |
| `target.id` | Home + document | Required; `urn:vesta:document:<homeId>:pantry`, matching [[ADR-006]]'s `"$homeId:pantry"` document id. |
| `schema:typeOfGood.schema:name` | `title` | Required, non-blank. |
| `schema:typeOfGood.schema:category` | `primaryCategory`, `secondaryCategory` | Ordered list: `[primary, secondary]`; secondary optional; a bare string means primary only. |
| `schema:typeOfGood.schema:gtin` | `barcode` | Optional. |
| `schema:typeOfGood.schema:size` | `packageSize` | Optional. |
| `schema:amountOfThisGood` | stock delta | Optional integer, default 1; must be between 1 and 9999. |

`schema:TypeAndQuantityNode` is Schema.org's closest fit for "N of this product" (it is designed for bundle contents);
Schema.org has no dedicated inventory-count property.

### Replies: AS2 `Accept` / `Reject`, sent back to the sender

```json
{
  "@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
  "type": "Accept",
  "id": "urn:uuid:<new>",
  "inReplyTo": "urn:uuid:0b7c5a2e-3f0e-4d3a-9d54-5d6c1a3f9e10",
  "object": "urn:uuid:0b7c5a2e-3f0e-4d3a-9d54-5d6c1a3f9e10",
  "result": {"type": "schema:Product", "id": "<pantry key>", "schema:name": "Heinz Ketchup",
             "schema:amountOfThisGood": 3}
}
```

`result.schema:amountOfThisGood` is the stock count after the command. A `Reject` has the same shape without `result`
and with a human-readable `summary` (e.g. "unknown home", "quantity out of range", "title is required").

### Behaviour

1. **Authorisation.** A command is acted on only if the daemon reported `verified == true` and `sender` is in Vesta's
   agent **allowlist** (a local list of x0x agent ids; empty by default, so nothing is accepted until configured).
   `trust_decision` must also be `accept`. A message failing either check is dropped **silently** — no reply — so the
   app can't be probed for existence, and the drop is logged. The payload's own `actor` field, if any, is never used
   for authorisation.
2. **Validation.** Payload size limit 16 KiB. The `@context` must match exactly; `type` must be `Add` and the object
   must be a `schema:TypeAndQuantityNode` wrapping a `schema:Product`. Anything else from an authorised sender gets a
   `Reject` with a `summary`.
3. **Home resolution.** `homeId` must be an existing Home on this device with an openable Pantry document; otherwise
   `Reject` ("unknown home").
4. **Effect.** Upsert the Pantry entry (dedup by barcode, else normalised title, per [[ADR-008]]) and adjust stock by
   `+quantity`. Existing fields on a matching entry must not be blanked by omitted optional fields. Verified: `upsertPantryItem`
   *does* overwrite/blank them, so the handler fills omitted fields from the existing entry first. A new entry with no
   `schema:category` gets primary category `Uncategorized` and no `schema:size` gives an empty package size (the
   Pantry UI requires both, the command doesn't). Then republish/persist through `PantryDocumentSource.mutate`, exactly as
   an edit made in the UI would, so the Pantry screen updates and the change syncs like any other.
5. **Idempotency.** The `id` of every processed command is recorded, keyed by `(sender, id)`, in a **local, unsynced**
   table (`AgentCommandLog`) — not per Home, because a command rejected for an unknown Home has no Home row to
   reference. The table stores the exact reply JSON. A repeat
   `id` is not applied again; the original `Accept` is re-sent (or a `Reject` if it was rejected). This survives app
   restarts.
6. **Reply.** Sent with `sendDirect` to `sender` after the effect is committed. A failure to deliver the reply is
   logged and does not undo the effect; a re-sent command gets the reply via rule 5.

### Routing and components

Sync messages ([[ADR-005]]/[[ADR-006]]) and commands will both arrive on `GET /direct/events`, so one component owns
that stream and routes by message kind rather than each feature opening its own subscription.

- **`X0xEventHub`** (`commonMain`): wraps `X0xClient.directEvents(callback)` as a `Flow` (via `callbackFlow`), with
  reconnect and backoff, in app scope — not tied to a composable. The connectivity indicator and status card move to
  a shared client instance rather than each calling `createX0xClient()`.
- **`DirectMessageRouter`**: classifies each message. A payload that parses as JSON with a JSON-LD `@context` is a
  command and goes to the command handler; anything else is left for the sync layer once [[ADR-006]] exists.
- **`PantryCommandHandler`** (`jvmAndroidMain`, because [[ADR-008]]'s Pantry document code and `PantryDocumentSource`
  live there): parses, authorises, validates, applies, replies. It takes a resolver `(homeId) -> PantryDocumentSource?`
  and the allowlist, so it is testable with fakes and no daemon.
- **Wiring** happens where `pantrySource` is created today (`App.kt` / the platform entry points).

### Scope

In scope: the desktop (JVM) app first; `Add` to the Pantry; the `Accept`/`Reject` reply. Out of scope: Android/iOS
wiring (the code lives in `jvmAndroidMain` and the hub in `commonMain`, but only desktop is exercised), an
allowlist/trust management UI, set/remove/update commands, shopping-list commands, and any change to document sync.

## Implementation notes

- `X0xEventHub` (`commonMain`) reconnects with 1 s → 30 s backoff; that needed an `onClose` signal on
  `openSseStream`/`directEvents`/`gossipEvents`. The iOS actual of that change is unbuilt/unverified.
- There is no separate `DirectMessageRouter` yet: with only one message family, `PantryCommandHandler` itself ignores
  anything that isn't one of its commands (`ParseResult.NotACommand`). Add the router when ADR-006 sync shares the
  stream.
- `AgentCommandService` (`jvmAndroidMain`) owns the shared `PantrySources` — one `PantryDocumentSource` per Home, used
  by both the UI and the handler, because a source must be the only reader/writer of its document — and the loop
  feeding the hub to the handler. Desktop wiring: `VestaApp(backgroundServices = …)`. Android is not wired (TODO in
  `MainActivity`).
- The allowlist is the `VESTA_AGENT_ALLOWLIST` environment variable (comma-separated agent ids), read at startup.
  No database table or UI.
- Handling is serialised, so a command and its duplicate can't race past the idempotency check. There is a small
  crash window between applying an effect and recording it; a command re-sent after such a crash would apply twice.
- Migration `2.sqm` creates `AgentCommandLog` for existing databases; a test verifies the driver applies it.
- The connectivity indicator and status card still create their own x0x clients.
- **End-to-end run (2026-09-30, x0xd 0.45.0):** the desktop app (fresh database, `VESTA_AGENT_ALLOWLIST` set) and a
  separate `vesta-agent` instance with mutual trust. A new command added the item with stock 2 and got an `Accept`; a
  second command id raised stock to 4; re-sending the same id re-sent the identical `Accept` (same reply id) and left
  stock at 4; an unknown Home got `Reject` "unknown home"; with an empty allowlist a new command had no effect, no
  reply and a logged drop reason. Replies arrived on the agent's `/direct/events` as `verified=true`, `trust=accept`.
  The first send succeeded without `recipient_ack_semantics_unavailable`, so the warm-up delay didn't show up here.

## Consequences

- The agent needs its own x0x instance and a mutual `x0x trust` step; documenting the two-command setup and the
  warm-up retry is part of the feature.
- Using public vocabularies means a third-party agent can be told to "send an ActivityStreams `Add` of a
  `schema:Product`" without a Vesta-specific spec, and the message stays meaningful outside Vesta. The cost is that
  the schema is looser than a bespoke one (quantity rides a bundle-oriented type, categories are an ordered list), and
  Vesta validates the shape itself instead of relying on a schema tool.
- Not running a JSON-LD processor means semantically equivalent JSON-LD that uses a different context or prefix is
  rejected. That is deliberate: it keeps parsing trivial and makes the accepted surface one documented shape.
- Silent drops for unauthorised senders make misconfiguration (an agent not on the allowlist) hard to diagnose from
  the agent's side; it shows up only in Vesta's log. Acceptable for the first version.
- The idempotency table is new local persistence. Migrations now exist (`1.sqm`, `2.sqm`) and the driver applies
  them, so existing databases pick the table up on open.

## Open Questions

1. **Allowlist storage and management.** v1 reads an environment variable. Still open: how it is eventually stored
   and edited (config file, database table, UI, `x0x` contacts integration) and how it is set on Android.
2. **Machine-readable reject reasons.** `Reject.summary` is human text only. If agents need to branch on the reason,
   a stable code is needed; Schema.org/AS2 have no standard slot for one.
3. **Quantity type.** `TypeAndQuantityNode` is an approximation. If it proves awkward, a small `vesta:` extension
   property is the fallback.
4. **Owner certification.** Whether to require the agent's instance to be owner-certified (verifiable "this is my
   agent") instead of, or in addition to, the allowlist. Untested.
5. **Reply reliability.** Replies are fire-and-forget over one direct message. Whether the agent should be able to
   re-request a reply (it can, by re-sending the command, per the idempotency rule) or Vesta should retry delivery.

## References

- [[ADR-005]] — one Automerge document per Home, group is discovery-only, sync is direct-message fan-out.
- [[ADR-006]] — document-id envelope for multiplexing sync messages on the same direct-message stream.
- [[ADR-008]] — Pantry document model this ADR writes to.
- x0x ADR-0039 (agent harness boundary: ACP-attached vs rider) — `saorsa-labs/x0x`, `docs/adr/0039-agent-harness-boundary.md`.
- x0x API reference — `GET /direct/events`, `POST /direct/send`, `POST /agents/connect`, `POST /owner/agents/issue`.
- W3C Activity Streams 2.0 (`Add`, `Accept`, `Reject`, `inReplyTo`) and Schema.org (`Product`,
  `TypeAndQuantityNode`, `gtin`, `category`, `size`).
