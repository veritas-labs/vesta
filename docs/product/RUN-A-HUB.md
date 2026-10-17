# Run an always-on hub

A hub is a computer that is nearly always on and keeps a copy of a Home's mailbox, so members get each other's changes
without being online at the same time ([Syncing and joining](SYNC-AND-JOINING.md)). It usually runs the plain **x0x** program
and nothing from Vesta: no app, no account, no configuration of its own. A Home can have **more than one** hub, and a
computer of yours that runs Vesta can be one too (see below).

## What you need

- A computer that is on most of the time: a desktop or laptop that is rarely switched off, a Raspberry Pi, a home server.
- **x0x version 0.48.0 or newer** (older versions cannot open the encrypted group store the mailbox lives in). Check with
  `x0xd --version`. Install it from [the x0x project](https://github.com/saorsa-labs/x0x).
- A Home admin's phone, with Vesta open, while you set it up.

## Set it up

1. On the phone, open the Home, tap the **⋮** menu and choose **Manage hubs**, then **Set up a hub**. If it says the group is not ready, wait a
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

The first command carries an invite that **lapses after an hour**, so run the commands soon after you copy them. If x0x says the invite has expired, open
**Always-on hub** again: the phone makes fresh commands whenever the ones it shows are half an hour old.

If you stop x0x on the hub (`x0x stop`), wait about twenty seconds before `x0x start`. Started sooner it can exit saying it
could not release its network sockets; starting it again a little later works.

## More than one hub

A second hub, on another computer or run by another admin, keeps a second copy of the mailbox. While either is on, members
still get each other's changes, so one being off or broken no longer stops syncing.

1. Open **Manage hubs**, then **Set up a hub**. Each hub is listed with whether it is connected, and its own **Remove hub**.
2. Tap **Add another hub** and follow the same steps as above on the other computer. The commands carry a new invite.

Every hub is an admin of the Home's group and **can read everything in the Home**, so each one must be a computer you trust.

## Use your own computer as a hub

On the desktop app, a computer that runs Vesta can be a hub itself. Its x0x program keeps running when Vesta is closed, and
keeps a copy of the mailbox the whole time.

1. Open **Manage hubs**, then **Set up a hub**. Under **Your devices**, the computer you are using is offered if you are an admin of the Home.
2. Tap **Use as hub**. There are no commands to run.

This is for your own devices. A phone can't be a hub, because its x0x program stops when the app is closed. A device of a
member who is not an admin can't be one either. If you remove your computer as a hub, it **stays in the Home** as your
device: only a computer that belongs to nobody is taken out of the group.

## What the hub can do

- It keeps a copy of everything that is in the Home's mailbox, and so **it can read everything in the Home**, like any
  member. Use a computer you trust, and secure it as you would a phone that has the Home on it.
- It is an admin of the Home's group, so while the phones are away it can let a newly approved person's device into the
  group. It does **not** approve anyone: approvals still need an admin's phone.
- It cannot tell Vesta anything or run Vesta code; it is an ordinary x0x member.

## Take it away

Open **Manage hubs** and then **Set up a hub** again and tap **Remove hub** next to the one to remove. It leaves the Home's group and the group's
keys change, so it cannot read anything new; the other hubs keep working. What it already holds stays on that computer: delete
the x0x data there if that matters (`x0x stop`, then remove its data directory).

If the screen says the removal did not work, try again in a minute: x0x sometimes needs a moment to fetch the computer's
key.

**Setting the same computer up again straight away can fail.** x0x refuses a join from a computer that was just removed
(`join_already_pending`), or accepts it and nothing appears under **Joined the group**, until that computer has dropped the
group. This took about two minutes in testing. Wait a few minutes after removing it, then run the two commands again.

## If something is off

- **The computer never appears under "Joined the group".** Check that x0x is running there and that `x0x group join`
  printed no error. The invite is for one group; run the commands exactly as shown. The phone must have Vesta open.
- **It says "Not in the group right now".** The hub left the group (it was removed, or its x0x data was deleted). Remove
  it and set it up again (after the wait described above).
- **Your own computer is not offered under "Your devices".** Only an admin sees it, only on the desktop app, and only once
  that computer is in the Home's group. It disappears from the list once it is a hub.
- **Changes still do not arrive.** The hub only helps while it is on and online. Vesta must be open on each phone; a
  phone that is closed or asleep does not read or write the mailbox.
