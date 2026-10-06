# Run an always-on hub

A hub is a computer that is nearly always on and keeps a copy of a Home's mailbox, so members get each other's changes
without being online at the same time ([Syncing and joining](SYNC-AND-JOINING.md)). It runs the plain **x0x** program
and nothing from Vesta: no app, no account, no configuration of its own.

## What you need

- A computer that is on most of the time: a desktop or laptop that is rarely switched off, a Raspberry Pi, a home server.
- **x0x version 0.46.3 or newer** (older versions cannot open the encrypted group store the mailbox lives in). Check with
  `x0xd --version`. Install it from [the x0x project](https://github.com/saorsa-labs/x0x).
- A Home admin's phone, with Vesta open, while you set it up.

## Set it up

1. On the phone, open the Home, tap the **⋮** menu and choose **Always-on hub**. If it says the group is not ready, wait a
   moment: the Home's group is made the first time the Home's founder opens the app.
2. On the hub computer, start x0x if it is not running (`x0x start`), then run the two commands the phone shows. Use
   **Copy commands** to send them to the computer. They look like this:

   ```
   x0x group join x0x://invite/…
   x0x group store create <group id> mailbox
   ```
3. Back on the phone, wait a few seconds. The computer appears under **Joined the group**. Tap **Use as hub** next to it.
   Only choose a computer you have just set up.
4. The screen now shows the hub as **Connected**. That is all: it needs nothing else.

The two commands are needed once per Home. After that x0x restores the store on its own whenever it restarts.

## What the hub can do

- It keeps a copy of everything that is in the Home's mailbox, and so **it can read everything in the Home**, like any
  member. Use a computer you trust, and secure it as you would a phone that has the Home on it.
- It is an admin of the Home's group, so while the phones are away it can let a newly approved person's device into the
  group. It does **not** approve anyone: approvals still need an admin's phone.
- It cannot tell Vesta anything or run Vesta code; it is an ordinary x0x member.

## Take it away

Open **Always-on hub** again and tap **Remove hub**. It leaves the Home's group and the group's keys change, so it cannot
read anything new. What it already holds stays on that computer: delete the x0x data there if that matters
(`x0x stop`, then remove its data directory).

If the screen says the removal did not work, try again in a minute: x0x sometimes needs a moment to fetch the computer's
key.

## If something is off

- **The computer never appears under "Joined the group".** Check that x0x is running there and that `x0x group join`
  printed no error. The invite is for one group; run the commands exactly as shown. The phone must have Vesta open.
- **It says "Not in the group right now".** The hub left the group (it was removed, or its x0x data was deleted). Remove
  it and set it up again.
- **Changes still do not arrive.** The hub only helps while it is on and online. Vesta must be open on each phone; a
  phone that is closed or asleep does not read or write the mailbox.
