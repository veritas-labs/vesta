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

## Seeing what synced

- Open **Sync activity** from the Home's menu (any member can). It says in one line whether the Home is in sync, lists the Home's devices and hubs with
  when each last synced, and keeps a log of what has come in and gone out. **Sync now** starts a round straight away.
- What the top line means:
  - **Up to date**: a sync worked in the last minute or so.
  - **Waiting for others**: nobody else is online right now, and nothing of yours is left unsent. It will sync when someone is.
  - **Changes waiting**: you have changes that no other device or hub has taken yet. They go as soon as one is online. It is a yes or no, not a count.
  - **Only you**: the Home is only on this device. Invite someone to start syncing.
  - **Sync isn't running**: the app can't reach its sync service on this device, or a message to another device has been failing for over an hour.
    Changes stay here until it is back.
- The log is **local to this device** and never sent anywhere. It records only that something synced (for example "Tasks updated by Sam's Pixel"),
  never the item names, quantities or titles. It is kept for **7 days**, and leaving a Home removes its entries.

## Photos

- A photo you add to a recipe is shrunk and kept on your device without its location or camera details; the recipe itself, its caption and a small blurred
  stand-in reach everyone the way any other change does.
- The photo itself travels **only while a device that has it and a device that wants it are online together**, and then without anything to do. A hub does
  not carry photos. Until a photo arrives the recipe shows the blurred stand-in with "Waiting for photo"; nothing is broken.
- Any member's device that has the photo can pass it on, not only the one that added it, so a photo reaches a device that was away the next time it is
  online with any device that has it.
- With **Use mobile data** off in Settings, photos are not fetched over a mobile connection, and the recipe says "Waiting for Wi-Fi".
- A photo nothing refers to any longer is removed from the device after about a week.

## Joining a Home

- After the new person opens an invite link and sends their request, an admin's device and the new person's device must
  be online together **at least once** so the admin can approve the request. A hub does not change this: approving a
  person stays with an admin, which is the check on a link that leaked.
- A link works for 7 days. Vesta keeps retrying the join for up to 14 days.
- After approval, the new person's device is added to the Home's group. With a hub, that needs only the hub, not an
  admin's phone, to be on.

## Unpairing a device (a lost or retired phone)

- In **Settings → Paired devices**, remove the device. It stops being one of your devices, and an admin's device then takes it out of **every Home's group**,
  which changes the group's keys: it can no longer read what is written afterwards, and it is refused if it asks to come back in.
- That takes a few seconds when an admin's device of the Home is online, and happens as soon as one next is if none is. A Home whose admins are all offline
  keeps the device in until then.
- **What stays on the device.** Everything it had already received is still there, in the app, and it keeps its own copy of your Homes. Unpairing cannot reach in
  and wipe it: if you lost the phone, also lock or erase it with the phone's own tools.
- A device unpaired by an **older version** of Vesta was never taken out of its Homes' groups, and there is not yet a screen to do that afterwards.
- To bring a device back, pair it again. If you pair a device you have only just removed, it may take a couple of minutes before it is accepted back into a Home's group.

## What the mailbox keeps, and for how long

- Each device writes what it changed to the Home's mailbox. Entries are deleted after 30 days, once a snapshot of the Home
  covers them, so the mailbox stays small.
- A device that has been away for longer than that, or is new, starts from the snapshot and then reads what came after it.

## What helps

- With a hub: leave it on. A hub that is off for days works like having none; with a second hub, the Home keeps syncing
  while either is on.
- Without one: open Vesta on both devices in the evening, or whenever people are home and online, and keep one device
  (for example a tablet) that is usually on and has Vesta open.
