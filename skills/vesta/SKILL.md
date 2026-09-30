---
name: vesta
description: >-
  Acts on a user's Vesta household app from an external AI agent over x0x
  direct messages: lists their Homes and adds items to a Home's Pantry
  (household stock / shopping). Use when the user asks you to add, restock or
  put an item in their pantry or household inventory in Vesta, or asks which
  homes they have in Vesta. Everything goes over x0x; never read Vesta's
  files or database.
---

# Vesta over x0x

Vesta is a household-organisation app running on the user's machine. You are an
external agent with **your own x0x instance**; you talk to Vesta by sending it
x0x direct messages and reading its replies. Never open Vesta's database or
files to find ids — Vesta gives them to you over x0x.

Today you can: **list the user's Homes**, and **add an item to a Home's
Pantry** (adds to the stock count; it does not set it).

## Before you start — ask the user, don't guess

You need all of these. If any is missing, tell the user exactly what to do
(with their values filled in) instead of trying things:

1. **Your x0x instance name** (e.g. `my-agent`), running: `x0x start --name <name>`.
2. **Vesta's agent id** — the user runs `x0x agent` on the machine running Vesta
   (their default daemon) and gives it to you.
3. **Mutual trust** — x0x needs each side to trust the other. Your id is
   `x0x agent --name <name>`. The user runs:
   ```sh
   x0x trust set <vesta-agent-id> trusted --name <name>
   x0x trust set <your-agent-id> trusted
   ```
4. **The Vesta app is running** on that machine.

There is nothing to configure in Vesta itself: the first time you send a
command, Vesta shows the user an approval card (see the outcomes below).

## Workflow

Run the scripts from this skill's `scripts/` directory (bash, needs `curl`,
`jq`, `base64`). If you can't run scripts, use
[references/without-scripts.md](references/without-scripts.md).

1. **List the Homes**
   ```sh
   scripts/agent-list-homes.sh --agent-instance <name> --to <vesta-agent-id>
   ```
   Prints one line per Home: `<homeId>` TAB `<name>`.
2. **Decide which Home.** More than one → **ask the user which they mean**; never
   guess. Exactly one → use it and say which you used. Never reuse a Home id from
   an earlier conversation without listing again (Homes get deleted).
3. **Add the item**
   ```sh
   scripts/agent-pantry-add.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> \
     --title "Heinz Ketchup" --category Condiments --category Ketchup --size "1 bottle" --qty 2
   ```
   Only `--home` and `--title` are required. `--category` is primary then
   (optionally) secondary; `--gtin` is a barcode; `--qty` is 1–9999 (default 1).
4. **Tell the user what happened**, from the script's output and exit code.

## Outcomes

| Result | Meaning | What to do |
| --- | --- | --- |
| exit 0, `Accepted: <item> now has <n> in stock` | Done. | Report the item and new stock count. |
| exit 3, "not approved…" | You're trusted on x0x but the user hasn't approved you in Vesta. A card is now showing in the app. | Ask the user to open Vesta and tap **Allow**, then run the same command again. |
| exit 3, "unknown home" | That Home id doesn't exist. | List the Homes again and re-ask. |
| exit 3, anything else | Vesta says the request was malformed; the reason is printed. | Fix the request (e.g. quantity out of range, empty title). Don't retry unchanged. |
| exit 4, no reply | Silent drop: the daemons don't trust each other (step 3), or the app isn't running. | Ask the user to check trust in **both** directions and that Vesta is open. Don't loop. |
| "recipient_ack_semantics_unavailable" | Normal for up to ~a minute after a new x0x instance starts. | The scripts retry for you (up to 2 minutes). |

## Retrying safely

Each script run is a **new** command with a fresh id, so running the add script
again adds the stock **again**. If you're unsure a command went through, re-send
it with the same `--id <urn:uuid:…>` (the script prints the id): Vesta applies
each id once and simply re-sends its original reply.

## Reference

- [references/messages.md](references/messages.md) — the exact JSON-LD messages and replies, and every field rule.
- [references/without-scripts.md](references/without-scripts.md) — the same flow with plain x0x REST calls.

Design background (for maintainers): `docs/adr/ADR-009-agent-commands-over-x0x.md` in the Vesta repo.
