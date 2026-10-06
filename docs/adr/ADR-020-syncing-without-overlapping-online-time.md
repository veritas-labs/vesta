# ADR-020: Syncing and joining without overlapping online time

**Status:** Proposed (2026-10-03). Decision step 1 (option 1) implemented 2026-10-05; the mailbox design in step 2 was spiked
2026-10-05/06 and is not built. Addresses the limit named in [[ADR-018]] (open point: "whether x0x
stores could serve as a mailbox") and the wording fixed in [[ADR-019]]. Builds on [[ADR-005]], [[ADR-013]], [[ADR-014]] and
[[ADR-017]].

## Context

Everything between devices is an end-to-end encrypted x0x **direct message**, retried by the sender every sync round
([[ADR-013]], [[ADR-017]]). There is no server and no mailbox: a message to an offline device can vanish, so the sender
resends until the other device happens to be online. Consequences:

- **Joining** ([[ADR-018]], [[ADR-019]]) needs the joiner and an admin device online together at least once after the link
  is redeemed and approved, within the link's 7 days and the 14-day retry window.
- **Data sync** between members needs two devices online together for one round (30 s). Each device keeps its whole
  Automerge document, so nothing is lost while apart, and Automerge syncs the full document, so a third device can carry
  changes between two that never meet. But a Home of two people with one device each has no such carrier.
- **Mobile.** A suspended phone does not retry. Background behaviour is undesigned ([[ADR-019]] open point), so overlaps
  may be rarer than "both online" suggests.

For a household this will usually work: people are home and online most evenings. It fails for the cases that matter
most to trust: a joiner who opens the link on a weekend and an admin who is away, or two members whose devices rarely
run at once.

x0x's bootstrap nodes are for discovery and NAT traversal and never hold application data. x0x **stores** are replicated
key–value CRDTs, but a replica lives on the agents that joined it: a store keeps data available only while at least one
replica is online. So a store is **not by itself a mailbox**. It only helps if some always-on agent replicates it.

## Options

1. **Make the overlap likelier (no new infrastructure).** Sync when a peer comes online instead of waiting for the next
   30 s round: x0x's `/presence/events` SSE reports online and offline transitions ([[ADR-002]] already planned this).
   Also run a round when the app returns to the foreground. This shortens the overlap needed from a full round to the
   moment two devices are both up. It does not remove the need for the overlap.

2. **An always-on Home hub running Vesta.** *Rejected 2026-10-06: the target households have phones only and do not want
   the desktop app. The hub is instead an x0x daemon holding a store (see Decision).* A device that is nearly always online (a desktop left running, a Raspberry Pi, a small VPS)
   runs Vesta headless and is enrolled as an ordinary device of a member, so it is in the roster and syncs the Home like any
   other replica. Members then sync with the hub whenever each is online, and changes flow A → hub → B without A and B
   meeting. For joining, the invite link can name the hub's agent as one of the "admin agents" it already lists
   ([[ADR-018]] step 1), so the joiner's request is held somewhere online. The hub holds a full decrypted replica, so it
   is a device the user trusts, as a paired device is ([[ADR-013]]). It needs no new protocol, only a headless build and a
   way to enrol it (the existing device pairing, [[ADR-012]]).

3. **Change log in an x0x store.** *This became the decision; see Decision and Spikes 1-4.* Each member appends Automerge change chunks, encrypted with a Home key, as
   store entries. Others pull what they haven't seen. A replica that cannot read them (a relay, a hub, a friend's device)
   can store and forward. This is the only option that does not require the holder to be trusted with the data. It needs
   a Home key with rotation on member removal, an owner/permission model for the store ([[ADR-005]] noted that stores
   anchor trust to an owner), and a way to garbage-collect the log. It still requires *some* replica to be online.

4. **A hosted relay.** A server run by Veritas Labs holds opaque encrypted blobs. Gives the best reliability, but it
   adds a service to run and trust, and it undoes the "no server" property the design is built on. Not considered further.

## Decision

Proposed, in this order. Each step is independently useful, and a later one is only built if the earlier one is not
enough in use.

**Target topology (the case to optimise for).** A household of two people, each with only an Android phone running Vesta,
plus an x0x daemon with no Vesta on a desktop, laptop or home server that is nearly always on. A headless Vesta hub is not
an option: people do not want the desktop app. So the hub must be a plain x0x agent that knows nothing about Vesta, which
also keeps it reusable by other apps and, later, by a cloud relay.

1. **Do option 1 now. Done.** Sync on a peer's presence transition and on app foreground, as well as on the timer.
2. **A mailbox in an x0x group store, held by an app-agnostic hub.**
   - **Store.** Each household group has a group store named `mailbox` (`POST /groups/{id}/stores`). Every member can
     write and delete, every member replicates it, and x0x encrypts it with the group key. Spikes 1 and 4 show it survives a
     rekey, serves a member who was offline across it, and serves a member who joined after the entries were written, all
     with the writer offline. **No app-level payload key is needed for correctness.**
   - **Phones.** A phone's embedded daemon lives only while the app is open, so a phone is never a long-lived replica and
     is not relied on to serve anyone. While the app is open it keeps using the established DM sync with the peers it can reach.
     The mailbox is the catch-up path for the rest: on open it makes the idempotent open call, reads entries it has not
     applied, and deposits the changes others have not received. The hub is the only member expected to stay up.
   - **Entries.** Each entry is one Automerge change chunk (or a slice of a snapshot), at most 48 KB (value limit 64 KiB).
     Key `<writer agent id>/<sequence>`, so keys never collide. The value starts with a version byte so a sealed form can be
     added later without breaking old entries. Readers list keys, fetch the ones they have not applied and feed them to
     Automerge, which ignores duplicates.
   - **Hub.** An x0xd on an always-on machine that is an *admin* member of the group, so it replicates the store and can admit
     joiners while the phones are away (Spike 6). It runs no Vesta code and no sidecar. Enrolling it is one copy-paste block on that machine (join the group, open the
     `mailbox` store), once per household (Spike 5), followed by the creator promoting it to admin (Spike 6).
     It is enrolled as an ordinary group member by an invite, and Vesta records it in the Home's device roster as the hub
     (x0x agent cards carry no "always on" capability, so x0x discovery cannot be used for this). If the hub belongs to one
     of the household's owners, that owner's other agents are discoverable as such (`GET /users/{id}/agents`), so members
     can tell whose machine it is.
   - **Garbage collection.** Writers delete their own entries once older than a window (proposed 30 days) after
     publishing a compacted snapshot chunk set, so the log, and the hub's disk, stay bounded. x0x applies no total cap.
3. **Only if the hub must not read the data: sealed entries.** For a hub on a machine the other member does not trust, write
   to ordinary per-writer stores (a joiner can replicate them without joining the group) and seal each value with an app
   Home key and a key id, rotated on member removal. This reintroduces key management and a way to tell the hub which
   stores to follow, so build it only if the trusted-hub model proves unacceptable. The version byte above keeps that open.
4. **No hosted relay by Veritas Labs.** A cloud relay run by someone else is the same hub protocol and is only acceptable
   with step 3.

## Consequences

- With option 1 alone the limit remains: two people who are never online at once still can't sync, and a join still needs
  an overlap. The documentation should say so plainly.
- With a hub, a Home is eventually consistent without any two people overlapping, as long as the hub is up. A hub down
  for days is the same as today.
- The hub is an admin, so it can also remove members and mint invites, as the creator can. The hub can read everything a group member can. It is a member of the household's group, so it must be a machine the
  household trusts. Removing it removes it from the group, which rekeys, and it loses access to later entries. Entries it
  already holds stay on that machine.
- Hub admin agents named in invite links lengthen the list that is capped at 5 ([[ADR-018]]); the hub should come first.
- Nothing here changes the Automerge sync or the message formats, so Homes made before this keep working. The mailbox is
  an additional path.
- Each member's phone must reach a daemon to write and read the store. Whether the Android embedded daemon does so
  without the app in the foreground is the undesigned wake-up problem.

## Spike findings (2026-10-05, x0xd 0.46.0)

Run by hand with `curl` against three throwaway daemons (`--name spike-a/b/c`, one machine, same x0x.prod plane). It
answers the questions in Decision step 3. Not yet an automated opt-in test.

**Confirmed: a store replica is a mailbox.**
- A replica keeps and serves entries while the writer is offline, across daemon restarts. A (owner) wrote, B replicated,
  A stopped; C then joined the store while A was down and received everything from B within seconds.
- A replica that was *offline during the write* catches up from another replica with the writer also offline (A wrote,
  C was down, A stopped, C started, got the entry from B).
- The replica needs no special role. A replica that only joined the store is enough, so a hub is just a device that stays up.

**Multi-writer: ordinary `/stores` cannot, group stores can.**
- `POST /stores` takes `signed`, `append_only` or `self_keyed`. A joiner always sees the store as `signed`, even for the
  other two, and a non-owner `PUT` is refused (`store policy is signed; owner is …`). So one shared log for a Home is not
  possible this way. A store per member (each owner writes its own, the others join with `expected_owner`) would work.
- `POST /groups/{id}/stores {"name":…}` gives a store bound to the group. Any active member can write and delete,
  and every member sees the others' entries. The Home group is created with the default `private_secure` preset, so this
  store is TreeKEM-encrypted with the group's key. A hub that is a member of the group can replicate it. A non-member
  cannot open it.
- Removing a member rekeys the group (epoch 2 → 3). The removed member is denied reads and writes at once, and entries
  written afterwards are not readable by them. They keep whatever they replicated before. This is the key rotation that
  option 3 needed, and x0x does it already.
- Deletion by any member propagates to other replicas.

**Limits.**
- A value is at most **64 KiB** (`value too large … exceeds maximum 65536 bytes`); the HTTP body limit is lower than 1 MiB.
  An Automerge change chunk or incremental save fits, but a full snapshot of a large document must be split.
- No total cap hit at 421 keys / 8 MiB (`evicted_keys` stayed empty), so the log must be garbage-collected by us:
  a hub or each member deletes entries every member has acknowledged, or compacts into a snapshot.

**Not verified.**
- Presence-free wake-up: nothing here helps a suspended phone learn there is something to pull. It still polls or
  syncs on foreground ([[ADR-020]] open point).
- Behaviour with the daemon embedded on Android, and over a real network (all three daemons shared one host).
- Invite redemption still needs the group authority online: B's join stayed `pending_authority_commit` until A was
  reachable, and the first invite for B never completed. A hub that is a group *admin* would fix joins; a member hub
  would not.
- On-disk encryption of replicas (a grep of the data directories found no plaintext, but that does not prove encryption).

**What it means for the options.**
- Option 2 (hub) is confirmed workable as a store replica and, for data, needs nothing from x0x beyond joining the group.
- Option 3 is feasible without our own Home key: use a group store and let x0x's group key and rekey do the encryption.
  The cost is that every replica holder, including the hub, is a group member who can read it. A blind relay is only
  possible with ordinary per-member stores and our own encryption, and that brings back the key management.
- Either way each entry stays under 64 KiB and the log needs our own garbage collection.

## Spike 2: a blind, app-agnostic relay (2026-10-05, x0xd 0.46.0)

Goal: a relay that is a plain x0x agent, not a member of any group, knows nothing about the app, and holds only
ciphertext. Four daemons: A (admin), B and C (members), R (relay, not in the group).

**Works.**
- R joined A's *ordinary* store (`POST /stores/{topic}/join`, `expected_owner` = A) and replicated it. It is not in the
  group, cannot see it (`group not found` on decrypt) and holds only the ciphertext envelope.
- With A stopped, B joined the store (owner A offline) and got every entry from R alone.
- So the relay needs no code beyond "join these (owner, topic) pairs". It is app-agnostic as proposed.

**Does not work: using the group's encrypt route as the payload cipher.**
- `POST /groups/{id}/secure/encrypt` output decrypts only at the *current* epoch. After a rekey (A removed C, epoch 2→3)
  B, offline across it, could decrypt entries written after the rekey but not those written before
  (`Invalid epoch: expected 3, got 2`). The writer A could not decrypt its own older entry either.
  `secure/reseal` shares the current secret with a recipient. It does not re-encrypt old ciphertext.
- A mailbox holds entries for people who read them late, so entries must survive a rekey. They don't.
- Overhead is about 3.3 KB of ciphertext per entry (4.5 KB base64), before wrapping it in the store's own base64. With
  the 64 KiB value limit, plan on roughly 40 KB of payload per entry.

**Consequence (superseded by Spike 4 for group stores).** The relay design stands, but the payload key cannot come from the group's `secure/encrypt` epoch. If the relay is not a group member, the app needs
its own key that outlives epochs, which is the original option 3 (a Home key with rotation on member removal). The key
itself can still be distributed through x0x, as a direct message or inside the group's current-epoch encrypt, but entries
are sealed with an app key (e.g. an AEAD key with a key id in each entry, so old keys stay readable). This is the
only app-specific part. Entries that were already synced by Automerge's full-document sync are not lost on a rekey, only
those a device never received.

## Spike 3: the target topology (2026-10-06, x0xd 0.46.0)

Target (the case to optimise for): a household of two people, each with only an Android phone running Vesta, plus an
x0x daemon (no Vesta) on a desktop, laptop or home server that is nearly always on. The daemon may belong to one of the
two people, so it is not necessarily the same owner as every phone. Three daemons: hub H and phone P1 with the same
owner key, phone P2 with a different owner key.

**Owner identity works as documented.**
- `x0x user-id create --name <n> --from-seed <hex>` gives the same `user_id` on any daemon from the same seed.
  `POST /announce {"include_user_identity":true,"human_consent":true}` publishes the certificate. After that, *any* peer, including a
  different owner's, sees `GET /users/{user_id}/agents` listing H and P1 as that owner's agents. So the household can tell
  that the hub belongs to P1's owner. Each agent still has its own agent id and machine id.
- After `x0x sync enroll` on both, `GET /owner/agents` on P1 lists H. P1 → H direct message was accepted with a durable ack
  with no trust setup (owner-trusted).
- Agent cards carry only `dm_capabilities` and `user_id`. There is no "always on" or "mailbox" capability to advertise.
  `x0xd --relay` is a NAT relay role for DMs and does not hold stores. The hub role therefore has to live in our own
  roster, not in x0x discovery.

**The hub mailbox works for the target topology.**
- Household = an ordinary `private_secure` group created by P1; P2 and H joined by invite (each needed the group authority
  online at that moment). All three opened the group store.
- P2 offline, P1 writes, P1 goes offline, P2 returns: P2 got P1's entry from H alone, wrote a reply, and P1 (with P2 now
  offline) later got it from H alone. H kept both across a daemon restart. No two phones were ever online together.
- H did this without any Vesta code. It joined the group and opened the same store by name.

**What is still not solved (unchanged by this spike).**
- (Spike 2 suggested group-store entries might not survive a rekey. Spike 4 shows they do. Only `secure/encrypt`
  output does not.)
- 64 KiB per value, writers garbage-collect their own entries.
- The hub can read everything a member can. For a hub on one household member's machine, the other member is trusting that
  person's machine. A hub not in the group (ordinary per-writer stores sealed with an app key, Spike 2) avoids this at the cost of
  the key management.
- Joining a group still needs the group authority online. A hub that is a group admin would let a joiner be admitted while the
  phones are away; I have not tested admitting through a non-creator admin.
- The Android embedded daemon takes `nativeStart(dataDir, identityDir)`. Whether it honours a `user.key` there is unknown
  (the desktop daemon reads it from its identity directory). The owner key must be moved between devices by hand.
- Leftover: identity directories (`~/.x0x-<name>`) survive deleting a daemon's data directory, so a reused `--name`
  comes back with the same agent id.

## Spike 4: do group-store entries survive a rekey? (2026-10-06, x0xd 0.46.0)

Spike 2 showed `secure/encrypt` output is unreadable after a rekey. Spike 3 left open whether group-store *entries* behave
the same. Four daemons: T1 (admin), T2, hub TH and T3-to-be, plus TC, in one group with a `mailbox` store.
- T2 went offline. T1 wrote an entry at epoch 3, removed TC (epoch 4) and wrote another. T1 then went offline. T2 restarted
  with only the hub up and read **both** entries, including the epoch-3 one.
- A member (T3) that joined *after* both writes opened the store and read both entries, with T1 offline.
So the store re-protects entries per transfer rather than binding them to the epoch they were written in. The payload
key in Spike 2 is only needed if the hub must not read the data (Decision step 3). Not tested: a removed member's
view of entries written before their removal beyond "denied from now on" (they keep whatever they had replicated).

## Spike 5: does the daemon open the store on joining? (2026-10-06, x0xd 0.46.0)

**No.** A daemon that joined a group never opens its `mailbox` store by itself:
- A hub that joined, then watched the admin create the store and write to it, listed no stores (`GET /stores` was empty) and
  answered `store not found`. The same held for a member that joined after the store already existed, and after a daemon restart.
- Opening is one idempotent call, `POST /groups/{id}/stores {"name":"mailbox"}`, and the store id is the same on every member.
  After the hub made it, it caught up the whole existing history, new entries flowed live, and the store was restored on
  its own after a daemon restart.
- It is once per group, not once per daemon start: after the call the store is restored on every restart.
- **No remote trigger exists on a vanilla daemon.** Opening is a local API call. Reading `GET /groups/{id}/stores`,
  `/groups/{id}/stores/mailbox` or `/groups/{id}/state` does not open it, nor did waiting 60 s. There is no config key for it.
  The only remote mechanism in x0x is `x0x exec`, which is off by default and needs the responder's exec ACL file (exact argv
  match; only `<INT>` and `<URL_PATH>` are templated), an Accept-trust contact, and a restart. It can be made to work by
  allowing a `curl` argv with a `<URL_PATH>` suffix, but that lets the trusted sender POST to any route of the hub's API and
  puts its token in the ACL, so it is not recommended.
- The hub's setup already needs one human step on that machine, accepting the group invite. Opening the store is a second
  command in the same step: `x0x group join <invite>` then `x0x group store create <group id> mailbox`. Vesta can show both
  as one copy-paste block when a hub is added. Nothing else runs on the hub: no sidecar, no config, a vanilla daemon.
  The phones' app makes the same call itself when it joins or creates a Home.

## Spike 6: can a non-creator admin admit a joiner? (2026-10-06, x0xd 0.46.0)

**Yes.** Creator W1 made a group, the hub joined, and W1 promoted it (`PATCH /groups/{id}/members/{agent}/role
{"role":"admin"}`). With W1 stopped, the hub minted an invite (`POST /groups/{id}/invite`) and a new daemon redeemed it.
- The join sat in `pending_authority_commit` only briefly: the hub committed it, and all members that were up showed
  three active members within seconds, with W1 still offline.
- The joiner opened the `mailbox` store, read an entry the hub had written, and wrote one the hub then saw.
- When W1 came back it agreed (3 members, no fork quarantine, no warnings) and received both entries from the hub.
So joining no longer needs the creator or a phone online, only the hub. The one-time cost is that the hub must be promoted to
admin by the creator while both are up, as part of enrolment. An admin can also change roles, remove members and mint invites, so
the hub has the same power over the household as the creator. That fits a hub on a trusted machine and is another reason
for Decision step 3 if it is not. Not tested: removal by the hub while the creator is offline, and a Vesta-side invite
link (ADR-018) that names the hub's agent as the agent to contact.

## Spike 7: the Android embedded daemon, run on the emulator (2026-10-06)

The app embeds x0x **0.45.0** (`ffi/x0x-ffi/Cargo.toml`); all earlier spikes ran 0.46.0 on the host.
- **Owner key works.** A `user.key` placed in `files/x0x-identity` is honoured: `/agent` reported that `user_id` and
  `/owner/agents` listed the phone's agent under it. A side effect is that the daemon then provisioned its own Home
  group (`home.json`), because an owned install does that at start.
- **Group join works across versions.** The phone (0.45.0) redeemed an invite from a host daemon (0.46.0) and showed up as
  an active member.
- **Group stores do not work on 0.45.0.** `POST /groups/{id}/stores` on the phone fails with `encrypted stores v1 are
  GSS-backed; other planes are not supported yet`, both for the host's group and for a group the phone created itself
  (default groups use the TreeKEM plane). 0.46.0 opened the same stores fine on the same plane. So **the mailbox cannot
  run on phones until the embedded x0x is upgraded** to at least 0.46.0 (0.46.3 is current on crates.io). **Verified afterwards:** with the embedded x0x bumped to **0.46.3** (rebuilt for the emulator's x86_64 ABI only), the phone
  joined a 0.46.0 host's group, opened the `mailbox` store, read the host's entry and wrote one the host read back.
  So 0.46.3 phones and 0.46.0 hubs interoperate on stores. The arm64 and armv7 libraries still need the normal `preBuild` rebuild.
- The daemon exists only while the app process does (`SharedDaemon`, no foreground service, stopped on mobile data unless
  allowed). A phone reads and writes the mailbox only while the app is open, which the hub design already assumes.
- The emulator test was restored afterwards: groups left, owner key and the files it caused removed, `named_groups.json`
  reset to `{}`. The agent id is unchanged. Two files under `files/x0x/treekem` may be left from the test and are harmless.

## Open points

- Whether a hub can approve joins by itself. It should not: approval is the check on a leaked link ([[ADR-019]] step 4),
  so the hub holds the request and an admin's device still approves.
- Presence reliability: [[ADR-002]] flagged that the offline→online signal may be late or missed, so the timer stays as a
  fallback.
- How a mobile device wakes to sync when the app is closed. Undesigned, and the main reason overlaps are rarer on phones.


## Implementation slices

The detailed plan, with the dependency order and risks, is [docs/plans/ADR-020-implementation-plan.md](../plans/ADR-020-implementation-plan.md). It adds
slices this list lacks, notably a Home x0x group ([[ADR-021]], built on the fake mesh): Vesta has no group per Home today, only the per-person `DeviceSync`
group, and the mailbox needs one.

1. **Done.** Presence-triggered and foreground-triggered sync rounds in `DeviceDataSyncService`, with tests on the in-memory mesh.
   `X0xClient.presenceEvents` and `X0xEventHub.presence` stream `/presence/events`; an `online` event for a peer runs
   `syncWith(peer)` (restart that peer's sync and resend queued join messages); `DeviceDataSync.syncNow()` runs a full
   round and is called from `App` on `ON_START`. The 30 s timer stays as the fallback.
2. **Done.** Document what overlap is required today: [docs/product/SYNC-AND-JOINING.md](../product/SYNC-AND-JOINING.md) (the app has no help screen yet).
3. **Spike done by hand 2026-10-05** against real daemons, written up in "Spike findings". Still to do: an opt-in automated
   test like [[ADR-012]]'s, and a decision on member-hub versus per-member blind stores.
4. Mailbox client in the app: entry format, writing and reading `mailbox`, garbage collection, with tests on the in-memory mesh. **Format, writer and reader done 2026-10-06** (`mailbox/`), and **called by `DeviceDataSyncService` the same day** (fake mesh only); **garbage collection done 2026-10-06** (snapshots, deleting covered entries after 30 days, readers that jump; fake mesh only). Entries carry a per-deposit number and readers go through a writer's entries without gaps.
5. **Prerequisite, done 2026-10-06 for Android:** the embedded x0x in `ffi/x0x-ffi` is now 0.46.3 and the store check passes on
   the emulator. Still to do: rebuild the arm ABIs and check the iOS build.
6. Hub: the enrolment block (`x0x group join` + `x0x group store create`, shown by the app, Spike 5), hub entry in the device roster, invite links
   list the hub first. Real-daemon opt-in test.
