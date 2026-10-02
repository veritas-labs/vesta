# ADR-017: Retrying messages to offline devices

**Status:** Accepted (2026-10-02). Extends [[ADR-014]] and [[ADR-015]].

## Context

Home membership is signalled with x0x direct messages ([[ADR-015]]): a member leaving tells the others (`homejoin.left`),
and an admin removing a member tells that member's devices (`homejoin.removed`). A direct message to an offline device
can vanish without an error, and a removed or departed member's devices no longer share the roster document, so the
roster cannot carry the news instead. Until now each message was sent once; a device that was off at that moment never
learned it had been removed, or that someone had left.

Document sync already recovers from this: every round (30 s) restarts the sync with every peer.

## Decision

- **Outbox.** Sending `left` or `removed` first records the message in the local `PendingMessage` table, one row per
  recipient device, Home and kind (a repeat replaces the row), then tries it immediately.
- **Retry.** Every sync round resends every pending message. A row older than 14 days is dropped.
- **Acknowledgement.** A recipient answers any verified `left` or `removed` with `homejoin.ack` (Home id and the kind
  being acknowledged), whether or not it acted on it, since the sender only needs to stop. The sender deletes the row
  for that recipient only, so an ack from a stranger cannot clear someone else's message.
- **Idempotent receivers.** Authentication is unchanged (`left` only from a device the roster lists for that member;
  `removed` only from a device of an admin), and handling a message twice does nothing the second time. The Home is
  already gone, or the member already removed.
- **Lost `accepted`.** A joiner that already has a Home and receives the invite again from the inviter that the roster
  lists answers `accepted` again, so the admin's resend loop ends instead of timing out.

Not covered: the `Hello` and `Rotate` messages of [[ADR-012]] (the group's own state converges through `reconcile()`;
a missed `Rotate` still needs re-pairing).

## Consequences

- An offline device is told within one round of coming back, as long as the sender is online then and within 14 days.
- Messages survive a restart of the sender.
- A recipient that never returns costs one failed send per round until the row expires.
- If the sender is itself gone for good (uninstalled), nothing is retried. The recipient keeps its copy, as ADR-014 notes.

## References

`PendingMessage.sq`, `migrations/10.sqm`, `HomeJoinService.resendPending`, `HomeJoinMessage.Ack`;
`DeviceDataSyncServiceTest` (offline removal, offline departure, expiry, repeated invite).
