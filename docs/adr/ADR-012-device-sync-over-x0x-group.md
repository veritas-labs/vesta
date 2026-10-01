# ADR-012: Device sync over a private x0x group

**Status:** Accepted (2026-10-01). Supersedes the device-enrollment half of [[ADR-003]], whose cr-sqlite sync half
[[ADR-005]] already retired.

## Context

A person's own devices need to find and trust each other ([[ADR-003]]'s problem, still unsolved after [[ADR-005]]).
[[ADR-003]] proposed certifying new devices as x0x "riders" under a user key. Spiking against x0x 0.45 showed that
doesn't fit:

- Riders are sub-agents hosted under an owner daemon, not other machines.
- x0x's native owner device set (`x0x sync enroll`) only works between daemons that hold the *same owner key*, so
  pairing would mean copying the owner secret between devices (which [[ADR-003]] itself forbids), writing an identity
  file and restarting the daemon. None of that works on the embedded daemon (Android, iOS), and it syncs x0x's own
  owner data, not Vesta's.
- There is no REST call to create or import an owner identity.

A second open-source project, Watch-It, links devices with a shared 32-byte secret and a derived gossip topic. That
needs no identity and runs everywhere, but it can never revoke a device: anyone holding the secret stays in.

## Decision

Every device of one person is a member, and an admin, of **one private x0x group** named `DeviceSync`
(`private_secure`: invite-only, hidden, TreeKEM-encrypted). Membership is the access control.

**Pairing** reuses the existing pairing link:

1. The new device shows a `PairingLink` — its own x0x agent id, its name and a 5-minute expiry. The link carries no
   secret.
2. A device already in the group pastes it. If it isn't in a group yet it creates one. It creates an invite and sends
   it to that agent id as an x0x direct message (end-to-end encrypted, so only the holder of that agent key can read
   it), repeating it every 10 s until the agent is admitted: gossip on a fresh topic drops early messages.
3. The new device accepts the invite **only while it is showing an unexpired pairing link and is in no group**, and
   only from a sender the daemon marks `verified`. It joins, and waits to be listed `active`.
4. The owner promotes it to admin. Every paired device is an admin, so none is special (a promoted device needs ~15 s
   before the daemon lets it act as admin).

**Names.** x0x's member list carries only agent ids. Devices send each other a `Hello` direct message with their
name whenever they meet a member they hadn't seen; the receiver answers once, so both ends learn each other.
`SyncedDevice` is each device's local cache of the roster. The group stays the authority: `reconcile()` (every 30 s)
adds and drops rows to match it, and a device that finds it is no longer a member forgets the group and its list.

**Removal.** Removing a member rotates the group's keys, so the removed device can't read anything sent afterwards.
x0x only lets the group **creator** be removed by itself: another admin's attempt fails indefinitely with
`member_key_package_pending`. So removing the creator (typically a lost first device) **replaces the group**: the
remaining device creates a fresh `DeviceSync` group and sends the others a `Rotate` direct message (new invite plus
the old group id); each accepts only from a current member of the group it is in, joins, and leaves the old one. A
device that is offline at that moment is not moved, drops off the list, and must be paired again.

Removal stops *future* sync only. Nothing remotely wipes data already on a removed device.

## Consequences

- Works on the embedded and standalone daemons alike: only group, direct-message and agent REST calls are used. No
  owner identity, no key transfer, no restart.
- Real revocation, unlike a shared secret.
- Trust at pairing rests on the human: a pasted link names a device and its agent id, and whoever pastes a wrong link
  invites the wrong agent. The invite is useless to anyone but the agent it was addressed to.
- The new device's acceptance window is bounded (link expiry) but not interactive: it joins the first valid invite
  it receives while showing its link. A confirmation step showing the inviting device's name is a sensible hardening.
- Offline devices miss a group replacement and need re-pairing. Replacing the group changes its id, which is why the
  current id is kept in the local `DeviceSyncGroup` table.
- This ADR creates the roster and the trust boundary only. **No application data is replicated yet.** The next step
  is a per-user Automerge document (device registry, then the Home list) synced over this group with the mechanism
  from [[ADR-005]], rather than the size-capped key-value store Watch-It uses.

## References

- [[ADR-003]] — superseded here; its device-enrollment steps are replaced by the flow above.
- [[ADR-005]] — the Automerge-over-x0x-group mechanism the data sync will reuse.
- x0x 0.45 REST: `POST /groups`, `/groups/{id}/invite`, `/groups/join`, `PATCH /groups/{id}/members/{agent}/role`,
  `DELETE /groups/{id}/members/{agent}`, `DELETE /groups/{id}` (leave), `GET /groups/{id}/state` (creator),
  `POST /direct/send`, `GET /direct/events`.
- `DeviceSyncService`, `DeviceSyncMessage`, `SyncedDeviceRepository`; `DeviceSyncServiceTest` (in-memory mesh) and
  `DeviceSyncDaemonIntegrationTest` (three real daemons, opt-in).
