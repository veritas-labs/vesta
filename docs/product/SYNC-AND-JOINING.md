# Syncing and joining: when devices need to be online

Vesta has no server. Devices talk to each other directly, and end-to-end encrypted. Without more, a change only travels
when the devices involved can reach each other. An **always-on hub** removes that limit; this page says what happens with
and without one. See [ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md) for the reasons and
[Run an always-on hub](RUN-A-HUB.md) to set one up.

## Syncing changes

- Every device keeps the whole Home, so nothing is lost while a device is away and you can always use the app offline.
- Two devices that have Vesta open and online **at the same time** sync within seconds: a sync starts as soon as a device
  comes online or Vesta is opened, and repeats about every 30 seconds.
- **With a hub**, a change is also left in a shared mailbox that every member's device and the hub keep a copy of. Two
  people who are never online together still get each other's changes: the second person's app picks them up from the
  hub, or from any member's device that has them, when it is opened. The hub has to be on while each person opens the
  app, not at the same time as the other person. A Home can have several hubs: any one that is on is enough, and a
  computer of an admin's that runs the desktop app can be one.
- **Without a hub**, two people who each have one device and are never online together do not see each other's changes. A
  third device that meets both (a tablet, a laptop, another member's phone) carries changes between them.
- A phone that is asleep, or has Vesta closed, does not sync. It catches up the next time Vesta is opened.

## Joining a Home

- After the new person opens an invite link and sends their request, an admin's device and the new person's device must
  be online together **at least once** so the admin can approve the request. A hub does not change this: approving a
  person stays with an admin, which is the check on a link that leaked.
- A link works for 7 days. Vesta keeps retrying the join for up to 14 days.
- After approval, the new person's device is added to the Home's group. With a hub, that needs only the hub, not an
  admin's phone, to be on.

## What the mailbox keeps, and for how long

- Each device writes what it changed to the Home's mailbox. Entries are deleted after 30 days, once a snapshot of the Home
  covers them, so the mailbox stays small.
- A device that has been away for longer than that, or is new, starts from the snapshot and then reads what came after it.

## What helps

- With a hub: leave it on. A hub that is off for days works like having none; with a second hub, the Home keeps syncing
  while either is on.
- Without one: open Vesta on both devices in the evening, or whenever people are home and online, and keep one device
  (for example a tablet) that is usually on and has Vesta open.
