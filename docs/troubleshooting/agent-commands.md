# Agent commands (ADR-009): setup and debugging

An AI agent on this machine, running as its own x0x instance, can add items to a Home's Pantry by sending Vesta a
direct message. Design: [ADR-009](../adr/ADR-009-agent-commands-over-x0x.md). Desktop app only for now.

## One-time setup

1. Vesta's own daemon must be running (`x0x start`); the desktop app's x0x indicator should be green.
2. Start the agent's instance and note both agent ids:
   ```sh
   x0x start --name vesta-agent
   x0x agent                        # Vesta's daemon: the id the agent sends TO
   x0x agent --name vesta-agent     # the agent's id (Vesta shows it, shortened, when asking you to approve it)
   ```
3. Trust each side on the other (x0x direct messages need mutual trust; trust between your own agents is not
   automatic):
   ```sh
   x0x trust set <vesta-agent-id> trusted --name vesta-agent
   x0x trust set <agent-id> trusted
   ```
4. Start the app: `./gradlew :desktopApp:run`. That's all — you approve the agent in the app the first time it
   asks (see below). Two gates, on purpose: x0x trust (step 3) says the agent may *message* you, and is shared by
   every app on the daemon; Vesta's own approval says it may *change Vesta's data*.
   For a headless or scripted setup you can pre-approve agents instead, before launching the app (comma-separated x0x
   agent ids, read once at startup): `export VESTA_AGENT_ALLOWLIST=<agent-id>`.
5. Wait about a minute. The first send after starting a new instance can fail with
   `recipient_ack_semantics_unavailable` until the daemons have exchanged capability adverts;
   `scripts/agent-pantry-add.sh` retries for two minutes.

## Approving an agent

The first command from a trusted agent Vesta hasn't approved gets a `Reject` ("not approved: ask the user to approve
this agent in the Vesta app, then retry") and a card appears at the bottom of the app: *Allow this agent to manage
your home?* with the agent's shortened id. **Allow** approves it for good; **Deny** just dismisses the request (the
agent is asked about again if it sends another command). Several waiting agents are shown one at a time. There is no
in-app screen to revoke an approved agent yet: untrust or block it with `x0x trust`/`x0x contacts`, or remove its row
from the `AgentAccess` table.

## Sending a command

The agent never needs to read Vesta's database: everything goes over x0x. It works out the Home id first, then adds.

1. **List the Homes** — Vesta answers with each Home's name and id:
   ```sh
   scripts/agent-list-homes.sh --agent-instance vesta-agent --to <vesta-agent-id>
   # id-aaa<TAB>The Johnsons
   # id-bbb<TAB>Flat 4
   ```
2. **Ask the user which Home they mean** if there is more than one (the agent's job, not the script's).
3. **Add**, using the chosen id:
   ```sh
   scripts/agent-pantry-add.sh --agent-instance vesta-agent --to <vesta-agent-id> --home <homeId> \
     --title "Heinz Ketchup" --category Condiments --category Ketchup --qty 2
   ```

The add script prints the command id (`urn:uuid:…`). Re-sending the same id (`--id`) never applies twice; Vesta just
re-sends its original reply. Vesta's `Accept`/`Reject` reply shows up on the agent instance's `GET /direct/events`
(`agent-list-homes.sh` reads it for you). An agent that isn't using the scripts sends the same two messages itself —
see ADR-009 for the exact JSON.

## Nothing happened?

Vesta drops messages that its x0x daemon doesn't vouch for (not `verified`, or trust not `accept`) **silently** — no
reply, and no approval card, so an unknown agent can't make the app prompt. Vesta's log is the app's stderr; look for
lines starting `[agent-commands]`. Each drop says which check failed, and `held command …` means the agent is trusted
but waiting for your approval. Then check, in order:

- A `Reject` saying "not approved": open the app and tap **Allow** on the card (it appears over whichever screen is
  showing), then retry.
- Both daemons trust each other (`x0x contacts list`, with and without `--name vesta-agent`).
- The `--home` id came from `agent-list-homes.sh` and is a Home that exists on this device — otherwise Vesta replies
  `Reject` ("unknown home").
- `agent-list-homes.sh` timing out with no reply means a silent drop: the daemons don't trust each other, or the app
  isn't running.
- Any other `Reject` `summary` means the agent was approved but the payload was malformed; the summary says what.
- No log line at all: the message never arrived — check the daemons can reach each other, and that Vesta's
  connectivity indicator is green. The app reconnects to its daemon's event stream by itself (1 s to 30 s backoff).

## Existing local databases

Commands are de-duplicated in a new local table (`AgentCommandLog`, migration `2.sqm`). It is created when an
existing database is opened by the new app version. If a database somehow lacks it, delete `~/.vesta/vesta.db`.

## Cleaning up after testing

```sh
x0x stop --name vesta-agent
rm -rf ~/.x0x-vesta-agent ~/.local/share/x0x-vesta-agent
x0x contacts remove <agent-id>   # on the default daemon
```
