---
name: vesta
description: >-
  Acts on a user's Vesta household app from an external AI agent over x0x
  direct messages: lists their Homes, reads a Home's Pantry, adds items to it,
  takes items out and sets exact stock, keeps its shopping list, and reads the
  cookbook and adds or removes recipes
  and keeps the Home's shared calendar (household stock / shopping). Use when the user asks you to add, restock or
  put an item in their pantry or household inventory in Vesta, or asks which
  homes they have in Vesta. Everything goes over x0x; never read Vesta's
  files or database.
---

# Vesta over x0x

Vesta is a household-organisation app running on the user's machine. You are an
external agent with **your own x0x instance**; you talk to Vesta by sending it
x0x direct messages and reading its replies. Never open Vesta's database or
files to find ids — Vesta gives them to you over x0x.

Today you can: **list the user's Homes**, **read a Home's Pantry** (what is in
stock), **add an item** (adds to the stock count), **take some out** (lowers it, never
below 0) and **set the stock to an exact number**. You can also **read the cookbook**
and **add or remove recipes**. Reading and changing are separate permissions the user
grants per feature (Pantry Read/Write, Cookbook Read/Write).

You can also keep a Home's **shopping list**: **read** it, **add** an item, **tick** it off (say how many are
picked up) or **remove** it. Those are the user's Shopping list Read and Shopping list Write switches.

You can also keep a Home's **shared calendar**: **read** its events, **add** one, **change** one or **remove** one. Those
are the user's Calendar Read and Calendar Write switches; their private calendar is never visible to you.

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

The user decides what you may do in Vesta (Settings → Assistants): which Homes,
which features (Read and Write are separate switches per feature; only
Pantry Write does anything today), and whether each action needs their tap. They can add you ahead of time by your
agent id (`x0x agent`), or you can just send a command and they will be asked
to review you (see the outcomes below). You never get a copy of their data.

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
   (optionally) secondary; `--size` is `<amount><unit> <container>` such as `500ml`, `400g tin`, `6 pack`
   (grammar in [references/messages.md](references/messages.md#package-size)); `--gtin` is a barcode; `--qty` is 1–9999 (default 1).
   To see what is in stock first (needs the user's Pantry **Read** switch):
   ```sh
   scripts/agent-pantry-read.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId>
   ```
   Prints `<stock>` TAB `<title>` TAB `<categories>` TAB `<size>` TAB `<gtin>` per item, most-used first (at most 200;
   stderr says how many of the total). Reading changes nothing, so re-running is safe.
   To use some up, or correct the count after a stocktake (the item must already be in the Pantry; Pantry **Write**):
   ```sh
   scripts/agent-pantry-reduce.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Heinz Ketchup" --qty 1
   scripts/agent-pantry-set.sh    --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Heinz Ketchup" --qty 5
   ```
   Prefer **set** when you know the true count (repeating it is harmless); use **reduce** for "we used one". Reduce is not
   safe to repeat: see Retrying safely. Items are found by `--gtin` if you give it, else by name (any case).
   The **shopping list** is separate from the Pantry (adding to it never touches stock). Needs the Shopping list
   **Read** / **Write** switch:
   ```sh
   scripts/agent-shopping-read.sh   --agent-instance <name> --to <vesta-agent-id> --home <homeId>
   scripts/agent-shopping-add.sh    --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Milk" --category Dairy --qty 2
   scripts/agent-shopping-tick.sh   --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Milk" --qty 2
   scripts/agent-shopping-remove.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Milk"
   ```
   `read` prints `<wanted>` TAB `<got>` TAB `<title>` TAB `<categories>` TAB `<size>` TAB `<gtin>` TAB `<out of stock yes|no>` per
   line (at most 200). `add` adds to what is wanted (an item already listed has the amounts added together), `tick`
   sets how many are picked up (`--qty 0` un-ticks; safe to repeat), `remove` takes the whole line off. `tick` and `remove`
   need the item to be on the list already ("unknown item"); items are found by `--gtin`, else name (any case).

   **Cookbook.** Recipes live in the Home's cookbook; Cookbook **Read** and **Write** are their own switches:
   ```sh
   scripts/agent-cookbook-read.sh   --agent-instance <name> --to <vesta-agent-id> --home <homeId>                    # list
   scripts/agent-cookbook-read.sh   --agent-instance <name> --to <vesta-agent-id> --home <homeId> --recipe <recipeId>   # one, in full
   scripts/agent-cookbook-add.sh    --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Pancakes" \
     --category Breakfast --servings 4 --cook PT15M --ingredient "200g plain flour" --ingredient "2 eggs" --step "Whisk" --step "Fry"
   scripts/agent-cookbook-remove.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> --recipe <recipeId>
   ```
   The list prints `<recipeId>` TAB `<name>` TAB `<category>` TAB `<servings>` TAB `<time>` TAB `<keywords>` (at most 200). Read
   the cookbook first so you don't add a duplicate (names are unique, any case). Photos and videos are never sent or accepted.
   Removing is permanent for the whole household: only on the user's request, and say which recipe went.
   **Calendar.** The Home's shared calendar; Calendar **Read** and **Write** are their own switches. Times are UTC instants
   (`2026-10-20T18:30:00Z`, an offset such as `+01:00` is fine, a time with no offset is refused: ask the user their zone) and an
   all-day event uses plain dates (`2026-10-20`) whose end is the LAST day:
   ```sh
   scripts/agent-calendar-read.sh   --agent-instance <name> --to <vesta-agent-id> --home <homeId> --from 2026-10-20 --until 2026-10-27
   scripts/agent-calendar-add.sh    --agent-instance <name> --to <vesta-agent-id> --home <homeId> --title "Dentist" \
     --start 2026-10-20T10:00:00Z --end 2026-10-20T11:00:00Z --location "High St" [--repeat weekly]
   scripts/agent-calendar-update.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> --event <eventId> --start 2026-10-20T14:00:00Z
   scripts/agent-calendar-remove.sh --agent-instance <name> --to <vesta-agent-id> --home <homeId> --event <eventId>
   ```
   `read` prints `<start>` TAB `<end>` TAB `<title>` TAB `<eventId>` TAB `<all-day yes|no>` TAB `<repeat>` TAB `<location>` (at most 200,
   earliest first; `--from`/`--until` are optional and inclusive). Read before you add, so you don't duplicate an event; an
   identical title and time is refused. `update` changes only the fields you give (a new `--start` alone moves the whole event).
   `update` and `remove` need the `<eventId>` from `read` or `add`. Removing is permanent for the whole household: only on the
   user's request, and say which event went.
4. **Tell the user what happened**, from the script's output and exit code.

## Outcomes

| Result | Meaning | What to do |
| --- | --- | --- |
| exit 0, `Accepted: <item> now has <n> in stock` | Done. | Report the item and new stock count. |
| exit 3, "not approved…" | You're trusted on x0x but the user hasn't added you in Vesta. A card is now showing in the app. | Ask the user to open Vesta, tap **Review…**, choose what you may do and save, then run the same command again. |
| exit 3, "not permitted for this home" | You're added, but have no grant to add to that Home (the Home may not exist; Vesta says the same either way). | List the Homes again (you only see Homes you may use). If it isn't there, ask the user to grant you that Home in Settings → Assistants. Don't retry unchanged. |
| exit 3, "waiting for approval…" | The user set your access to *ask each time* and has been asked. | Tell the user to tap **Allow once** in Vesta, then re-send the **same** command (same `--id`). |
| exit 3, "unknown item" | Reduce/set named an item that isn't in that Pantry. | Read the Pantry for the exact name, or add it first. |
| exit 3, "unknown recipe" | A cookbook read or remove named a recipe that isn't in that cookbook. | List the cookbook again for the recipe id. |
| exit 3, "unknown event" | A calendar update or remove named an event that isn't on that calendar. | Read the calendar again for the event id. |
| exit 3, "recipe already exists" | A recipe with that name (any case) is already in the cookbook. | Read it instead of adding it again. |
| exit 3, "unknown home" | That Home id doesn't exist. | List the Homes again and re-ask. |
| exit 3, anything else | Vesta says the request was malformed; the reason is printed. | Fix the request (e.g. quantity out of range, empty title). Don't retry unchanged. |
| exit 4, no reply | Silent drop: the daemons don't trust each other (step 3), or the app isn't running. | Ask the user to check trust in **both** directions and that Vesta is open. Don't loop. |
| "recipient_ack_semantics_unavailable" | Normal for up to ~a minute after a new x0x instance starts. | The scripts retry for you (up to 2 minutes). |

## Retrying safely

Each script run is a **new** command with a fresh id, so running the add script
again adds the stock **again** (and so does the shopping-list add) (and reduce takes it out again; set is harmless to repeat). If you're unsure a command went through, re-send
it with the same `--id <urn:uuid:…>` (the script prints the id): Vesta applies
each id once and simply re-sends its original reply.

## Reference

- [references/messages.md](references/messages.md) — the exact JSON-LD messages and replies, and every field rule.
- [references/without-scripts.md](references/without-scripts.md) — the same flow with plain x0x REST calls.

Design background (for maintainers): `docs/adr/ADR-009-agent-commands-over-x0x.md` in the Vesta repo.
