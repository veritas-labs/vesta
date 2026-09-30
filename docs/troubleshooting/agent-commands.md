# Agent commands (ADR-009): setup and debugging

An AI agent on this machine, running as its own x0x instance, can add items to a Home's Pantry by sending Vesta a
direct message. Design: [ADR-009](../adr/ADR-009-agent-commands-over-x0x.md). Desktop app only for now.

## One-time setup

1. Vesta's own daemon must be running (`x0x start`); the desktop app's x0x indicator should be green.
2. Start the agent's instance and note both agent ids:
   ```sh
   x0x start --name vesta-agent
   x0x agent                        # Vesta's daemon: the id the agent sends TO
   x0x agent --name vesta-agent     # the agent's id: goes on Vesta's allowlist
   ```
3. Trust each side on the other (x0x direct messages need mutual trust; trust between your own agents is not
   automatic):
   ```sh
   x0x trust set <vesta-agent-id> trusted --name vesta-agent
   x0x trust set <agent-id> trusted
   ```
4. Tell Vesta who may command it, **before launching the app** (comma-separated x0x agent ids; unset or empty accepts
   nothing):
   ```sh
   export VESTA_AGENT_ALLOWLIST=<agent-id>
   ./gradlew :desktopApp:run
   ```
5. Wait about a minute. The first send after starting a new instance can fail with
   `recipient_ack_semantics_unavailable` until the daemons have exchanged capability adverts;
   `scripts/agent-pantry-add.sh` retries for two minutes.

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

Vesta drops messages from unverified, untrusted or non-allowlisted senders **silently** (no reply, so the app can't be
probed), and only logs it. Vesta's log is the app's stderr — look for lines starting `[agent-commands]`; each drop says
which check failed (`verified`, `trust`, `allowlisted`). Then check, in order:

- The app was started with `VESTA_AGENT_ALLOWLIST` set (it is read once at startup) and the id is the *agent
  instance's*, not Vesta's.
- Both daemons trust each other (`x0x contacts list`, with and without `--name vesta-agent`).
- The `--home` id came from `agent-list-homes.sh` and is a Home that exists on this device — otherwise Vesta replies `Reject` ("unknown home").
- `agent-list-homes.sh` timing out with no reply means the same as a silent drop, below.
- A `Reject` with a `summary` means the payload was authorised but malformed; the summary says what.
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
