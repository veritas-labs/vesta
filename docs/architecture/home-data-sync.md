# Home Data Sync: cr-sqlite + x0x

> Technical architecture, not visual/interaction design (see `docs/design/` for that layer). Answers the question
> CORE-EXPERIENCE.md §5 explicitly defers: "How identity propagates across devices is an architecture concern, not a
> product spec concern." Builds on the working spike described below — read that section first if you're new to
> either cr-sqlite or x0x in this codebase.

## 1. Status

**Proposed plan, not yet implemented.** §2 documents a working proof of concept (committed, spike-quality) that lives
on the `poc/cr-sqlite-sync-over-x0x` branch, not `main`. §3 onward is the design for turning it into the real thing:
per-Home group broadcast with DM-based catch-up. Nothing past §2 has been built or tested yet — treat every code
sketch below as illustrative, not final.

## 2. What the spike proved (2026-09-06)

A throwaway screen (`CrdtSyncDemoScreen`, currently what `App()` renders — see its kdoc) proved the core mechanics
end to end between two independent app installs:

- `Profile` upgraded to a cr-sqlite CRDT table (`crsql_as_crr('Profile')`, idempotent, run on every launch — see
  `uk.veritaslabs.vesta.db.createVestaDatabase`).
- Local writes are readable as `crsql_changes` rows (`uk.veritaslabs.vesta.db.crsqlChangesSince`) and replayable
  losslessly and idempotently on another device (`applyCrsqlChange`) — this is cr-sqlite's own CRDT merge, not
  anything Vesta implements.
- `uk.veritaslabs.vesta.sync.ProfileCrdtSync` wraps that in a wire format (`CrsqlChange` → base64 JSON → one x0x
  direct message → back to `CrsqlChange` → merge) and pushes it over x0x's `/agents/connect` + `/direct/send` +
  `/direct/events`.
- Verified for real: two desktop app instances (isolated x0x identities, isolated SQLite files via the new
  `VESTA_DATA_DIR`/`X0X_DATA_DIR` env overrides), pasted agent ids, added profiles on both sides, tapped **Sync now**
  on each — both converged to the same 4-profile set.

What the spike deliberately does **not** do, and what this document is about: broadcast to more than one peer, use
x0x's group/encryption features at all, trigger sync automatically, or persist a sync watermark durably. It's a
manual, single-peer, DM-only proof that the cr-sqlite↔x0x wire format works.

## 3. Target shape: one x0x group per Home

Map each Home (`docs/design/create-home.md`, `docs/design/join-home.md`) to one x0x **named group**
(`POST /groups`, preset `private_secure` — MLS-encrypted, the default preset). Home membership *is* group
membership: creating a Home creates the group; the invite-code flow in `join-home.md` becomes, underneath, an x0x
group invite (`POST /groups/<gid>/invite` → `POST /groups/join`); removing someone from a Home removes them from the
group.

This needs a new local table (illustrative shape, not final):

```sql
CREATE TABLE HomeSync (
    homeId TEXT NOT NULL PRIMARY KEY,
    x0xGroupId TEXT NOT NULL,
    x0xTopic TEXT NOT NULL,      -- see §4 — the gossip topic this Home's changes broadcast on
    lastAppliedDbVersion INTEGER NOT NULL DEFAULT 0
);
```

`Profile.x0xInstanceName` (already in the schema from the spike) hints this multi-Home structure was anticipated —
worth revisiting once real Home↔group wiring exists, since "instance name" and "which Home" may end up the same
concept.

## 4. Two delivery paths, not one

x0x's own docs (`x0x` CLI's bundled skill reference, §"Beyond Messaging"/"How It Works") draw a sharp line between
two delivery modes, and this design deliberately uses both — this is the user's explicit call, not a fallback:

| Mode | Endpoint | Delivery | Confidentiality |
|---|---|---|---|
| **Gossip pub/sub** | `POST /publish` on a **topic** | eventually consistent, epidemic — every relaying peer forwards it | sender-signed but **plaintext to every relay** unless the payload itself is pre-encrypted |
| **Direct messaging** | `POST /direct/send` to an **agent id** | immediate, reliable, ordered, durable-ACK (ADR-0030) | end-to-end between the two agents |

Important nuance found while researching this: `POST /groups/<gid>/secure/encrypt` (the private-group MLS primitive)
only **encrypts a payload** — it does not deliver it anywhere. There is no "broadcast to this group" endpoint for
`private_secure` groups. The actual broadcast transport is always the plain `publish`/`subscribe` topic bus; MLS
groups add membership + encryption, which the app must combine itself: **encrypt via the group, then publish the
ciphertext on a topic**. `x0xTopic` in §3 is that topic name (derived from `x0xGroupId`, e.g. the group id itself —
exact derivation TBD).

### 4.1 Broadcast path (online members)

On every local Profile write:

1. `crsqlChangesSince(homeSync.lastBroadcastDbVersion)` — same call the spike already uses.
2. Serialize to the spike's existing `WireChange` JSON shape (no change needed there).
3. `POST /groups/<gid>/secure/encrypt` the JSON payload.
4. `POST /publish` the ciphertext + the MLS **epoch** it was encrypted under (the epoch must travel alongside the
   ciphertext in our own envelope — `groups decrypt` requires it explicitly) to `x0xTopic`.
5. Every Home member currently `subscribe`d to `x0xTopic` receives it, `groups decrypt`s with the given epoch, parses
   `WireChange`s, and calls `applyCrsqlChange` — the spike's merge path, unchanged.

### 4.2 Catch-up path (returning members)

This is **not** a broadcast-replay problem, and DMs aren't just a stylistic choice here — MLS is forward-secret:
group membership changes (anyone joining or leaving) rotate the epoch, and old epoch keys are not necessarily kept
around. A member who was offline across an epoch rotation may not be able to decrypt gossip published under a prior
epoch at all. Broadcast is therefore only reliable for members who were online continuously; catching up after any
gap needs a channel with fresh, current keys — which is exactly what a DM is (`/direct/events` supports
`?backfill=N` per ADR-0023 §7, but more importantly every new DM is encrypted under the current session, not a
possibly-rotated group epoch).

Proposed flow, reusing the spike's `ProfileCrdtSync` almost unchanged:

1. Watch `/presence/events` (SSE) for a Home member transitioning offline → online.
2. On that transition, the peer who noticed it (or every peer, idempotently — cr-sqlite merges don't care about
   duplicates) sends the returning member a DM containing `crsqlChangesSince(<per-peer watermark for that member>)`.
3. The returning member applies it via `applyCrsqlChange`, exactly as the spike does today.

Per-peer watermarks (`HomeSync.lastAppliedDbVersion` in §3 was Home-scoped for simplicity above; catch-up actually
needs it tracked per-*peer*, since different Home members may have missed different windows — this needs a real
schema pass, not the one-liner in §3) replace the spike's in-memory `lastPublishedVersion`, which reset on every
restart. That was fine for a manual demo; it would mean full-history resends on every launch in the real app.

### 4.3 A brand-new member joining a Home

Distinct from catch-up (§4.2, which assumes prior membership): a first-time joiner has no local Profile data at
all. This is closer to x0x's own TaskList CRDT bootstrap pattern (its skill docs describe this as "cold-start
bootstrap, then deltas") than to steady-state sync, and probably warrants its own full-state transfer rather than
replaying the group's entire change history from `db_version` 0 — e.g. one member DMs (or file-transfers, via x0x's
`send-file`) a full snapshot on join, and the joiner starts its own `lastAppliedDbVersion` watermark from there. Not
designed in detail here — flagged as its own follow-up.

## 5. Phased build order

1. **Durable watermarks.** Replace `ProfileCrdtSync`'s in-memory `lastPublishedVersion` with the `HomeSync` table
   from §3 (per-peer, per §4.2). No behavior change yet — just makes the spike's own mechanism restart-safe.
2. **Multi-peer fan-out over DM.** Before touching groups/gossip at all: generalize `publishChanges` to loop over
   every Home member's agent id (from `/groups/<gid>/members`) and DM each one, instead of the spike's single
   hardcoded peer. This alone gets multi-device sync working correctly (if not efficiently) and is a small diff from
   what's already proven.
3. **Group + gossip broadcast (§4.1).** Swap the fan-out DM loop for real publish/subscribe broadcast for online
   members, keeping DM fan-out (§4.2) for anyone who was offline. This is the step that actually needs the MLS
   encrypt/epoch handling worked out in practice, not just on paper.
4. **New-Home-member bootstrap (§4.3).**
5. **Beyond Profile.** Generalize past the single hardcoded table — `WireChange.table` already carries this, so
   `crsqlAsCrr` just needs calling on more tables as Vesta grows past Profile.

## 6. Open questions

- **Topic derivation and confidentiality.** §4 assumes `x0xTopic` is derived from `x0xGroupId`; gossip topics are
  described in x0x's own docs as "sender-signed but readable by every relaying peer" — confirm the encrypt-then-
  publish pattern is in fact how other x0x-based apps (e.g. Symphony's TaskList CRDTs, which combine "MLS group
  encryption" with the same pub/sub layer per its docs) actually do this, rather than inferring it from the CLI
  surface alone as done here.
- **Epoch bookkeeping.** Every broadcast payload needs to carry the epoch it was encrypted under; decrypt failures
  (stale epoch) should fail soft — drop the message and rely on DM catch-up — rather than error the app.
- **Payload size / chunking.** Untested for both `publish` and `direct/send` — large change batches (e.g. an initial
  bootstrap dump, §4.3) may need chunking or should bypass gossip/DM sizing entirely via `send-file`.
- **Conflict semantics.** cr-sqlite's default merge is last-writer-wins per column (by `col_version`/`site_id`) —
  confirm this matches product expectations for concurrent edits (e.g. two members renaming the same Profile at
  once) before this ships; no UI currently surfaces a conflict happened at all.
- **Presence-detection reliability.** §4.2's offline→online trigger depends on `/presence/events`; needs a fallback
  (e.g. also re-sync on the receiving member's own app foreground) in case a presence transition is missed.

## 7. Known environment gotchas carried over from the spike

Still true, not re-litigated here — see the spike's own docs for detail:

- Only **one** cr-sqlite-loaded SQLite connection may exist per JVM process on this dev machine (segfaults otherwise)
  — affects `:shared:jvmTest` design, not the real app (`shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/db/TestDatabase.kt`
  kdoc).
- Android's cr-sqlite binary is `arm64-v8a` only, and **the Android emulator cannot run ARM64 images on an x86_64
  host at all** (hard block, not just slow — confirmed directly on this machine). Real device or physical ARM64
  hardware needed for any Android-side testing of this feature until/unless an x86_64 binary is built (README's
  "cr-sqlite binaries" section).
