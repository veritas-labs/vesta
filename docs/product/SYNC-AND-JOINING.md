# Syncing and joining: when devices need to be online

Vesta has no server. Devices talk to each other directly, so a change only travels when the devices involved can reach
each other. This page says what that means today. It will change if the always-on hub in
[ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md) is built.

## Syncing changes

- Every device keeps the whole Home, so nothing is lost while a device is away and you can always use the app offline.
- Changes travel when two devices of the same Home have Vesta open and online **at the same time**. A sync round takes
  about 30 seconds at most, and starts at once when a device comes online or Vesta is opened.
- Two people who each have one device and are never online together will not see each other's changes. A third device
  that meets both (a tablet, a laptop, another member's phone) carries changes between them.
- A phone that is asleep, or has Vesta closed, does not sync. It catches up the next time Vesta is opened.

## Joining a Home

- After the new person opens an invite link and sends their request, an admin's device and the new person's device must
  be online together **at least once** so the admin can approve the request.
- A link works for 7 days. Vesta keeps retrying the join for up to 14 days.
- After approval, the new person's device receives the Home the next time it is online with a device that already has it.

## What helps today

- Open Vesta on both devices in the evening, or whenever people are home and online.
- Keep one device (for example a tablet) that is usually on and has Vesta open.
