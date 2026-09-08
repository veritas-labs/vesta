# ADR-003: Full Device Sync — cr-sqlite CRDT Tables over a Per-User x0x Group

## Status

Proposed. Nothing in this ADR is implemented — unlike [[ADR-002]], there is no spike to build on. This is the
`private` half of [[ADR-001]]'s two sync forms; the spike behind ADR-002 only ever exercised a `shared`-shaped
table, so this ADR has no working example to generalize from and every code sketch below is illustrative, not
final.

Builds on [[ADR-001]] (table-level sync boundary, UUID primary keys) and reuses the transport pattern
[[ADR-002]] establishes (MLS group + gossip for online peers, DM for catch-up) — this ADR does not
re-litigate why that split exists, only how it applies to a different membership: a single person's own
devices, not a Home's members.

## Context

[[ADR-001]] names two sync forms:

1. **Full device sync** — a single user's own data (e.g. their Profile) replicated across every device they
   own.
2. **Partial (Home) sync** — data replicated to Home members, via the per-Home x0x group [[ADR-002]] designs.

ADR-002 answers (2). This ADR answers (1) — the `private`-classified half of ADR-001's table boundary, which
ADR-001's own open questions flag as unbuilt: *"Whether any `private`-classified table is even implemented
yet — the spike ... only exercises a `shared`-shaped table."*

### Why this isn't just "ADR-002 with a different roster"

The mechanism is deliberately the same shape: an MLS group for broadcast, DMs for catch-up, per-ADR-002's
reasoning about forward secrecy and epoch rotation, which applies identically here — a device that was
offline across a membership change can't decrypt gossip published under a rotated epoch, full stop, regardless
of whether the group represents a Home or a person's own device fleet. Reusing one mechanism for both is the
point: sync-transport code dispatches on ADR-001's `private`/`shared` classification, and each maps to exactly
one group scope (per-user vs. per-Home) using the same broadcast/DM machinery underneath.

What's genuinely different, and is what this ADR has to design that ADR-002 didn't:

- **Home membership already has a bootstrap: the invite code.** [`docs/design/join-home.md`](../design/join-home.md)
  hands a Home's group-join to an existing product flow. A user's own device fleet has no equivalent — nothing
  in the product today establishes "this phone and that tablet belong to the same person."
- **Vesta has no account system.** `docs/design/onboarding.md` S1's copy is explicit: *"Your household, your
  rules. No account needed."* `docs/product/CORE-EXPERIENCE.md` §8 lists "Multi-device sync of Profiles" as a
  non-goal, deferred as *"an architecture concern, not a product spec concern."* This ADR is that deferred
  concern — but it means there is no login, no server-side identity, nothing to hang device membership off of
  except what x0x itself provides.
- **The blast radius of getting this wrong is bigger.** A Home group leaks Home data to a Home member — bad,
  but bounded by whatever that member could already see once admitted. A device-sync group leaks a user's
  *entire private data set* to a device that shouldn't have it, permanently (see Consequences).

## Decision

### x0x's own Human identity layer is the trust anchor, not a Vesta-invented concept

x0x already models exactly the relationship this ADR needs. From the x0x skill's Identity model:

> **Machine** (automatic) — hardware-pinned. **Agent** (portable) — can move between machines. **Human**
> (opt-in) — issues an `AgentCertificate` binding agent to human.

This is the answer to "how does a new device prove it's the same person": it doesn't prove it to *Vesta* at
all. Vesta doesn't invent a user-linking concept — it rides x0x's existing one, and confirmed against x0x's
own certificate-issuance mechanism (ADR-0039 / x0x's `SKILL.md`), issuance is **asymmetric by design**:

1. The new device generates its own local ML-DSA-65 agent keypair, as any x0x agent does — its private key
   never leaves that device, full stop.
2. The new device's **public** key only is handed to the existing ("owner") device —
   `x0x owner agents issue <PUBLIC_KEY_HEX> --mode rider --label "<device label>"` / `POST
   /owner/agents/issue { agent_public_key, mode: "rider", label }`.
3. The owner device signs the resulting `AgentCertificate` with its own `~/.x0x/user.key` — binding the new
   device's `agent_id` (derived from its public key) to the shared `user_id` — and the certificate blob is
   returned to the new device (or distributed over the mesh; it's self-verifying, so it doesn't need a
   confidential channel to deliver, only an authentic one).

Per x0x's own docs: *"the harness generates the keypair, passes only the public key... the daemon never sees
the secret."* The human private key (`~/.x0x/user.key`) is therefore **strictly single-device** — it stays on
whichever device first ran `x0x user-id create`, and every other device is a `rider` under it, never a second
holder of the same key. `Profile.x0xInstanceName` (existing schema, from the ADR-002 spike) is the per-device
x0x identity each `rider` certificate attaches to.

**Do not manually copy `~/.x0x/user.key` between devices as an alternative to this flow.** x0x's own tracker
(issue #449) documents that two daemons independently holding the same raw `user.key` currently provision two
distinct x0x "Homes" — an x0x-internal identity-scoping concept, unrelated to and not to be confused with a
*Vesta* Home ([[ADR-002]]) — silently splitting what was meant to be one identity. This forecloses Open
Question (b) from an earlier draft of this ADR (a "shared-key" model): it isn't just less safe, it's actively
broken today.

### One private x0x group per user, membership driven by the certified device set

Mirroring ADR-002's per-Home group exactly: create one x0x named group (`POST /groups`, `private_secure`
preset) per user identity, containing the agent of every device holding a valid `AgentCertificate` for that
`user_id`. This needs its own local table, parallel to ADR-002's `HomeSync`:

```sql
CREATE TABLE DeviceSync (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,          -- the x0x user_id this device's Profile is certified under
    x0xGroupId TEXT NOT NULL,
    x0xTopic TEXT NOT NULL
);

CREATE TABLE DeviceSyncPeer (
    deviceSyncId TEXT NOT NULL REFERENCES DeviceSync(id),
    peerAgentId TEXT NOT NULL,               -- another of this user's devices
    lastAppliedDbVersion INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (deviceSyncId, peerAgentId)
);
```

`DeviceSyncPeer` exists for the same reason ADR-002 flags per-peer watermarks as needed, not the Home-scoped
`lastAppliedDbVersion` its own table sketch starts from: different devices of the same user can miss different
windows independently (a laptop closed for a week has a different gap than a phone that was merely offline
overnight).

### Device enrollment ("link a new device")

Concrete flow, following the asymmetric issuance mechanism above — the new device always initiates, the
existing ("owner") device always signs:

1. **New device starts its daemon and generates its own agent keypair** (ordinary x0x behavior, nothing
   Vesta-specific) and renders its public key as a **QR code**.
2. **The existing device scans it** and calls `owner agents issue --mode rider` with that public key,
   producing a signed `AgentCertificate`.
3. **The existing device creates a `DeviceSync` group invite** (`POST /groups/<gid>/invite`) — same call
   ADR-002's Home invite uses — and DMs both the certificate and the invite link to the new device's now-known
   `agent_id`. This doesn't require a pre-established trust relationship: the new device is reachable on the
   x0x network as soon as its daemon starts (ordinary bootstrap discovery), and the certificate is
   self-verifying, so an unauthenticated DM is an acceptable delivery channel for it.
4. **The new device redeems the invite** (`POST /groups/join`), then polls `GET /groups/<gid>/members` until
   its own agent shows `state: "active"`, per the same pattern ADR-002 inherits from x0x's documented join
   flow.

This is proximity-based, device-to-device pairing — the same shape as WhatsApp/Signal "link a device," not an
emailed link. It needs a new onboarding entry point that doesn't exist today: `docs/design/onboarding.md`'s S1
only ever starts a *brand new* Profile (`Get Started` → `S2` Profile Setup). Linking an existing identity to a
new device needs its own path (illustratively, an `S1c`: "This is a new device — I already have a Profile
elsewhere" → show the new device's own QR for an existing device to scan) that this ADR flags but does not
design; it's a product/design decision belonging with the rest of `docs/design/onboarding.md`, not this
document.

### Sync mechanics: unchanged from ADR-002

Once the group exists and has ≥2 members, everything downstream is exactly ADR-002's design, substituting
"this user's other devices" for "this Home's other members":

- **Broadcast (online devices):** `crsqlChangesSince` → `WireChange` JSON → `POST
  /groups/<gid>/secure/encrypt` → `POST /publish` on `x0xTopic` with the MLS epoch attached → every online
  peer device decrypts and `applyCrsqlChange`s. Same encrypt-then-publish combination ADR-002 establishes,
  same reason (`/groups/.../secure/encrypt` only encrypts; publish/subscribe is the only broadcast transport).
- **Catch-up (a device that was offline):** watch `/presence/events` for that device's agent transitioning
  online, DM it `crsqlChangesSince(<DeviceSyncPeer.lastAppliedDbVersion for that peer>)`, apply on receipt.
- **New-device bootstrap:** a freshly linked device has zero local data for any `private` table. Same
  cold-start gap ADR-002 flags for a brand-new Home joiner and leaves undesigned — worth resolving **once**,
  generically, since both ADRs now need it rather than each inventing its own.

## Consequences

### Phased build order

Same shape as ADR-002's, adjusted for having no spike to start from:

1. **Prove the mechanism on one table.** Pick whichever table is the first genuinely `private`-classified
   one (ADR-001 flags none exist yet — likely a split-out "my own full Profile fidelity" table, see Open
   Questions) and get two of one person's own devices syncing it via manual DM, no group yet — the ADR-002
   spike's own starting point, repeated here.
2. **Device enrollment flow.** The QR-based certify + invite flow above — this is net-new UI and net-new x0x
   usage (`user-id`, `AgentCertificate`), not a reuse of anything already built.
3. **Group + gossip broadcast**, **watermark durability**, generalizing past one table — same steps as
   ADR-002 §Consequences, applied to the `DeviceSync` group instead of `HomeSync`.
4. **Revocation.** Losing a device (lost/stolen phone) needs a "remove this device" action: `x0x identity
   revoke` for its agent id, plus removing it from the `DeviceSync` group (which rotates the MLS epoch, so a
   revoked device can no longer decrypt future broadcasts). This has no ADR-002 equivalent to reuse wholesale
   — removing a Home member is a lower-stakes, more common action than deauthorizing one of your own devices,
   and probably wants its own confirmation UX. **Local-first limitation, not a bug to fix:** revocation stops
   *future* sync to that device; it does nothing to data already replicated onto it before revocation. No
   mechanism in this design (or in cr-sqlite/x0x generally) remotely wipes a device. Flag this to product —
   the "lost phone" story needs to be "your data is safe going forward," not "your data is deleted from the
   phone you lost."

### Known constraints carried over from ADR-002 / its spike

Unchanged, apply identically here — see ADR-002 §"Known constraints" for detail: single cr-sqlite connection
per JVM process (jvmTest impact only), Android emulator cannot run the ARM64 cr-sqlite binary at all.

## Open Questions

- **Single point of failure: the owner device.** Confirmed above — `~/.x0x/user.key` lives on exactly one
  device by design, and every other device is a `rider` under it, never a second holder. That's the *correct*
  security property (smaller blast radius per compromised device), but it means losing the owner device
  *before any other device was ever linked* leaves no way to certify a replacement — there is no second device
  that can call `owner agents issue`. Needs a documented recovery story (e.g., a recovery phrase for
  `user.key`, or an explicit "promote a rider to owner" operation if x0x supports one) before this ships, not
  after. This is now the single biggest unresolved risk in this design, not a hypothetical.
- **Cold-start bootstrap mechanism** (full snapshot transfer for a first-time joiner/linked device) — same gap
  ADR-002 leaves open, now shared by two ADRs; whoever builds phase 1 of either should resolve it once.
- **Does `Profile` become the first `private` table, or does it need the private/shared split ADR-001 flags as
  an open question?** The ADR-002 spike already uses `Profile` for Home-scoped sync (visible to Home members:
  display name, avatar). If a user's *own* full-fidelity Profile data (e.g. anything not meant for Home
  members to see) also needs full-device sync, that's ADR-001's "some private, some shared" case, which its
  own decision says gets split into two tables, not one with a visibility flag. Not resolved here — first
  concrete `private` table needed decides it.
- **Conflict semantics, payload chunking, presence-detection fallback** — identical open questions to ADR-002,
  not re-litigated here since the underlying mechanism is shared.

## References

- [[ADR-001]] — table-level sync boundary (`private`/`shared`) and UUID primary keys; this ADR builds the
  `private` half.
- [[ADR-002]] — the Home-scoped `shared` half; this ADR's broadcast/DM mechanism is the same design applied to
  a different group membership.
- `docs/product/CORE-EXPERIENCE.md` §8 — explicitly defers "Multi-device sync of Profiles" as an architecture
  concern; this ADR is that deferral being answered.
- `docs/design/onboarding.md` — S1's "no account needed" framing this ADR has to design around; needs a new
  device-linking entry point this ADR flags but doesn't design.
- x0x ADR-0039 / `SKILL.md` (external, x0x's own repo) — the asymmetric `AgentCertificate` issuance mechanism
  (`owner agents issue --mode rider`, `POST /owner/agents/issue`) this ADR's enrollment flow is built on; x0x
  issue #449 — the documented failure mode of copying `~/.x0x/user.key` between devices instead.
