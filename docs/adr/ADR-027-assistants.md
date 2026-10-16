# ADR-027: Assistants — scoped grants for external AI agents

**Status:** Implemented (2026-10-12), uncommitted; the UI has only run under Compose UI tests, never in the real app, and the real-daemon
flow (a live bot) is untested. Extends [[ADR-009]] (agent commands) and answers its Open Question 1 (approved-agent management).

## Amendment (2026-10-16): grants are feature × Read/Write

Capabilities are no longer one per command. Each top-level feature (Pantry, Shopping list, Cookbook, Meal planner, Tasks and
chores, Calendar, Merchants) has a **Read** and a **Write** capability, so a grant reads "Pantry: Write" and the Home card in
Settings → Assistants shows a Read and a Write switch per feature. `ListHomes` stays implied by any grant.

- **A command declares the capability it needs** (`AddPantryItem` needs `PantryWrite`), so a new command in an existing feature
  needs no new switch, grant row or migration.
- **Write does not imply Read.** The add reply still states the item's new stock count; that single-value leak is accepted.
- **Switches without a command are shown off and disabled** ("Coming soon") until one is built (`AssistantCapability.available`).
  Live today: Pantry Write and Pantry Read. A bounded query command (never a replica) is what makes a Read switch live.
- **Policy stays per Home** (`ask` applies to all the Home's granted capabilities) and defaults to Ask for Read and Write alike.
- The stored capability name changed from `PantryAdd` to `PantryWrite`; old rows are rewritten the first time they are read
  (`AssistantRepository.renameLegacyGrants`), so the kits schema and migration 18 are untouched.
- The private calendar and tasks documents (ADR-029, ADR-031) are not covered by the Calendar and Tasks features; they will
  be separate features, off by default, when a command for them exists.

- **Pantry Read (2026-10-16)** is `Read` of `urn:vesta:document:<homeId>:pantry` (`ReadPantry`). The reply is an `Accept` with a `Collection`
  of `schema:Product`s (name, categories, size, gtin, stock), most-used first, capped at 200 with `totalItems` giving the real count.
  It is a bounded answer, not a replica; it is not recorded in the command log (answered fresh), and under Ask the agent re-sends the
  same id after Allow once. Desktop only, like every assistant command. Skill: `scripts/agent-pantry-read.sh`.

- **Reduce and set stock (2026-10-16)** are `Remove` and `Update` on a Pantry (`ReducePantryStock`, `SetPantryStock`), both under
  **Pantry Write**, so no new switch. Remove lowers by 1–9999 and stops at 0; Update sets 0–9999 exactly, using the same
  delta-from-local-value method as the UI (`correctStock`, ADR-008: not exact under concurrent edits). Items must exist ("unknown item")
  and are found by gtin, else name ignoring case. Skill: `agent-pantry-reduce.sh`, `agent-pantry-set.sh`.

- **Shopping list Read and Write (2026-10-16)** use the document id `urn:vesta:document:<homeId>:shopping-list` (the `shopping-list`
  document feature). `Read` returns up to 200 lines with wanted quantity, `got` (picked up) and `outOfStock`. Write is `Add` (put
  1-9999 on the list, summing with an existing line; category defaults to "Uncategorized"), `Update` (set the picked-up count, 0-9999;
  0 un-ticks) and `Remove` (the whole line); `Update`/`Remove` need an existing line ("unknown item"; found by gtin, else name ignoring
  case). A write to a list that has not yet arrived from the Home's creator is refused ("the shopping list is not ready yet"). It never
  touches the Pantry. Code: `ShoppingListCommands.kt` (commands, parser, replies), `ShoppingListCommandHandler.kt` (grant gate and
  apply). Skill: `agent-shopping-read.sh`, `-add.sh`, `-tick.sh`, `-remove.sh`.
- **Cookbook Read and Write (2026-10-16)** are live. Addresses: `urn:vesta:document:<homeId>:cookbook` (the collection) and
  `...:cookbook:<recipeKey>` (one recipe). `Read` of the collection lists recipes as `schema:Recipe` summaries (id, name, description,
  category, yield, total time, keywords), by name, capped at 200 with the real `totalItems`; `Read` of one recipe adds ingredients
  (`schema:QuantitativeValue`), steps (`schema:HowToStep`, ISO 8601 `performTime`), prep/cook time and `isBasedOn`. Photos and videos are
  references that stay on the devices (ADR-022/023) and are never sent or accepted. Not logged for replay; Ask holds until Allow once.
  `Add` of a `schema:Recipe` to the collection (name required; yield 1–999, durations up to 7 days, at most 100 ingredients, 100 steps, 20
  keywords; ingredients and steps may be plain strings) and `Remove` by `schema:identifier` or `schema:name` are under **Cookbook Write**,
  replayed by command id. A name already in the cookbook (ignoring case) is "recipe already exists" so a looping bot cannot pile up
  duplicates; a missing recipe is "unknown recipe". Rating, favourite and notes are not exposed. Code: `CookbookCommands.kt` (parser,
  replies, commands) and `CookbookCommandHandler.kt`, hooked into `PantryCommandHandler` by two calls. Skill: `agent-cookbook-read.sh`,
  `agent-cookbook-add.sh`, `agent-cookbook-remove.sh`.
- **Meal planner Read and Write (2026-10-16)** use the document id `urn:vesta:document:<homeId>:mealplan` (the `mealplan` document
  feature). The plan holds only cookbook recipe keys per day and slot, so that is all a read returns: an `Accept` with a `Collection`
  of up to 200 meals (`schema:startDate`, `slot`, `schema:recipe.id`), earliest day first, optionally limited by `schema:startDate` /
  `schema:endDate` on the request's `object`. Write is `Add` (plan a recipe in a slot; repeating it changes nothing) and `Remove` (needs
  it planned there, else "unknown meal"); `Update` is refused. The key is not checked against the cookbook (the planner shows a missing
  recipe as removed), and `|` is refused in a key because it separates the parts of a document key. The plan is flat root keys, so there
  is no unseeded state to wait for. Code: `MealPlanCommands.kt`, `MealPlanCommandHandler.kt`. Skill: `agent-mealplan-read.sh`,
  `-plan.sh`, `-unplan.sh`.

- **Tasks Read and Write (2026-10-16)** reach only the Home's **shared** tasks document, `urn:vesta:document:<homeId>:tasks` (the
  `tasks` feature of ADR-031); the private list (`private-tasks`, ADR-029/031) is never addressable, so a message for it is rejected
  and the handler is only ever given the shared source. A task is a `schema:Action`. `Read` returns up to 200 tasks (dated first, real
  `totalItems`) with name, description, due day (`schema:endTime`), status, repeat (`schema:repeatFrequency`) and assignees
  (`schema:agent`). Write is `Add` (name; optional description and day; refused as "task already exists" for the same name and day),
  `Update` (done / not done via `schema:actionStatus`, for the task's current occurrence: a one-off's due day, a repeating task's latest
  due day so far, "anytime" for an undated one) and `Remove` (the done history stays); `Update`/`Remove` find the task by id, else by
  name ignoring case, and refuse an unknown or ambiguous name. Done records made this way carry no member (`by` is empty). Setting
  repeats, assignees and reminders (ADR-032) is not offered. No seed check is needed: each task is its own root map. Code:
  `TasksCommands.kt`, `TasksCommandHandler.kt`. Skill: `agent-tasks-read.sh`, `-add.sh`, `-done.sh`, `-remove.sh`.
- **Calendar Read and Write (2026-10-16)** reach only the Home's **shared** calendar, `urn:vesta:document:<homeId>:calendar`; the user's
  private calendar (ADR-029, `private-calendar`) has no address in the vocabulary and the handler is given only the shared source, so it
  stays unreachable whatever the grants. `Read` returns up to 200 `schema:Event`s earliest first (real `totalItems`), optionally
  restricted to an inclusive `schema:startDate`..`schema:endDate` range. Times: timed events are UTC instants (`...Z`; input may carry an
  offset, a time with no offset is refused because Vesta shows each device's own zone, ADR-029), all-day events are plain dates with the
  last day as `schema:endDate`, plus `allDay`. `Add`, `Update` (only the fields given; a new start alone moves the whole event) and
  `Remove` (by `schema:identifier`) are under **Calendar Write**, replayed by command id; an identical title and time is "event already
  exists", a missing event "unknown event". Location, description and a simple repeat (`schema:eventSchedule`) are carried; participants,
  colour, notifications and exceptions are not. Code: `CalendarCommands.kt`, `CalendarCommandHandler.kt`. Skill: `agent-calendar-read.sh`,
  `-add.sh`, `-update.sh`, `-remove.sh`.

The rest of this ADR still says `PantryAdd` in places; read it as `PantryWrite`.

## Context

The user runs a personal AI agent (a "Grok Bot") on a cloud VPS with its own x0x instance, and wants it to act for the
household in Vesta. Two existing ways to give a party access to a Home both fit badly:

- **Pair it as a device** ([[ADR-012]], [[ADR-013]]). A device is the user. It joins the DeviceSync group as an admin
  (and can remove other devices), and receives a full replica of the Home registry and every document (Pantry,
  shopping list, cookbook, merchants). A prompt-injectable LLM on a VPS would hold the user's whole authority.
- **Make it a Home member** ([[ADR-014]], [[ADR-018]]). A member is a person: full document sync, equal say, and a row in
  the member list and in invites.

[[ADR-009]] already gives a third path: the agent never syncs documents. It sends x0x direct messages (`Read` homes,
`Add` to a Pantry) and Vesta executes them, behind two gates (x0x trust, then an in-app approval card). What it lacks:

- approval is **global to the app**, not per Home (`AgentAccess(agentId, status, updatedAt)`), so approving the bot for
  one Home approves it for all;
- it is **all or nothing** — an approved agent may run every command that exists, and each new command (shopping list,
  cookbook, merchants) would widen that silently;
- there is **no way to see, rename, narrow or revoke** an approved agent, and no record of what it did;
- the agent is shown as a shortened id.

## Decision

An **assistant** is an external agent identified by its x0x agent id that holds a **grant**: a set of
(Home, capability) pairs plus an approval policy. It is neither a device nor a member. It has no document replica, no
DeviceSync membership, no Home x0x group membership, and appears nowhere in the member list.

### Grant model

Local and unsynced, like `AgentAccess`. A grant is deliberately not shared between a user's devices: each device decides
what may command it. (Revisit if users want one grant to cover all their devices; see Open Questions.)

```
Assistant(agentId PK, name, addedAt, status)          -- status: pending | active
AssistantGrant(agentId, homeId, capability, policy,   -- PK (agentId, homeId, capability)
               updatedAt)                              -- policy: ask | auto
AssistantActivity(id PK, agentId, homeId, capability, commandId, outcome, summary, at)
```

- **Capabilities** are a fixed, named set, one per command family the handler implements. Initially:
  `ListHomes` (read the Home list, filtered to granted Homes), `PantryAdd`. Later families (`ShoppingListAdd`,
  `CookbookRead`, `MerchantsRead`) each add a capability; **a new command never works under an old grant**. A command
  whose capability is not granted for its target Home gets a `Reject` ("not permitted for this home"). Reads are off by
  default, writes are off by default; the user turns each on.
- **Policy** per capability: `ask` (every command shows an approval card and is held until allowed or denied) or `auto`
  (applied at once and logged). Default for a new grant is `ask`. `ListHomes` is always answered from granted Homes only,
  so an assistant cannot learn of Homes it has no grant for.
- **Held commands under `ask`.** ADR-009 holds a command and tells the agent to retry. That stays for the first
  approval of an unknown agent. For `ask`-policy grants, the command is held *pending* (not rejected): the card offers
  Allow once / Deny, the agent receives a `Reject` with a "waiting for approval" summary, and the agent re-sends. Allow
  once applies to that command id only, so the re-send is applied and logged. (Auto-approving "from now on" is the
  user changing the policy to `auto`, not a card button.)
- **Trust floor unchanged.** The daemon gate (`verified`, `trust_decision == accept`, `sender`) still runs first and
  still drops silently. A grant never overrides it.
- **Revocation** deletes the assistant and all its grants, and leaves the x0x trust entry alone (that is shared by other
  apps, ADR-009). The agent's later commands are treated as from an unknown trusted agent: they raise a fresh pending
  request. Revoking an assistant, or all its grants for a Home, takes effect on the next command; there is nothing to
  claw back from the assistant because it holds no data.
- **Migration from `AgentAccess`.** Existing `allowed` rows become an active assistant with `PantryAdd` + `ListHomes`
  granted for **every Home that exists at migration time**, policy `auto` (that is exactly what they could do before),
  named by their shortened id. Homes created afterwards are not granted. `pending` rows become pending assistants. The
  `VESTA_AGENT_ALLOWLIST` variable keeps working: its ids are treated as active assistants with the same all-Homes
  grant, held in memory and not persisted.

### Adding an assistant

Two entry points converge on the same `Assistant` row.

1. **Add by id (primary).** Settings → Assistants → **Add assistant**: paste or scan the assistant's agent id (the bot
   shows its id, or a QR / `vesta-assistant:<agentId>?name=<name>` link). The user names it, picks Homes, picks
   capabilities (checklist per Home) and policy. Saving creates an *active* assistant with those grants before the bot
   ever sends a message, so its first command works and no card is needed.
2. **First contact (existing flow).** A trusted agent we have no row for sends a parseable command. As in ADR-009 it
   gets the "not approved" `Reject` and a pending assistant; the card now reads "**<name or short id>** wants to act for
   your household" with **Review…**, opening the same grants screen as (1) prefilled, and **Deny**. Allow is replaced by
   the review step so approval is never a blind permanent yes-to-everything.

The bot's display name is shown when x0x can supply one (ADR-009 Open Question 1), else the user's own name for it,
else the shortened id.

### Management screen

Settings → **Assistants**: a list of assistants (name, id suffix, "last active", counts of Homes/capabilities).
Detail screen: rename; per Home a capability checklist and a policy toggle; **Activity** (newest first: when, Home,
capability, outcome, one-line summary such as "Added 2 × Heinz Ketchup to Pantry"); **Remove assistant** with a
confirmation. A pending request shows in the list too, so a dismissed card is recoverable.

### Activity log

Every command from a known assistant, applied or rejected for permission/validation, appends an `AssistantActivity`
row. It is local, unsynced, and capped (newest 500 per assistant). `AgentCommandLog` (idempotency) is unchanged and is
still the source of truth for re-sent replies.

### What this does not do

- It gives the assistant no continuous read access. If one is needed ("what's low in the pantry?") it comes as a new
  bounded **query command** returning a limited answer, never as a document replica. A read-only *observer* role that
  syncs documents is a separate ADR, because it puts household data on the assistant's host.
- It does not change the x0x transport, message vocabulary or `Accept`/`Reject` replies of [[ADR-009]], except for the
  new "not permitted for this home" reject summary.
- It does not sandbox what the assistant *does* with replies it receives. Granting it a Home's name is granting it that
  name.

## Consequences

- A compromised or prompt-injected bot can do only what was granted, in the Homes granted, and every such action is on
  the Activity list. Under `ask` it can do nothing without a tap.
- Every new command family now needs a capability, a row in the grants UI, a skill update and an acceptance test; that
  is the intended cost.
- Two approval paths (once-per-command card, standing policy) are more states than ADR-009's single "allowed". Mapping
  today's allowed agents to `auto` + all current Homes keeps their behaviour unchanged.
- Grants are per device. A user with a phone and a laptop approves the bot on each (unless the open question below is
  decided the other way).
- `AgentApprovalCard`/`AgentApprovalHost` change (Allow → Review…) and `PantryCommandHandler`'s access check becomes
  `(agentId, homeId, capability) -> Decision {Allow, Ask, Deny}`.

## Implementation notes

- Tables `Assistant`, `AssistantGrant`, `AssistantActivity` (`Assistant.sq`, migration `18.sqm`, schema version 19). Grants cascade
  with their assistant and with their Home. `AgentAccess` stays in the schema, unused. The `policy` column holds the `GrantPolicy`
  names (`Ask`, `Auto`); an unknown value reads as `Ask`.
- `AssistantRepository` (commonMain) implements `AssistantAccess`, which replaced `AgentAccess`. Held commands (`pendingCommands`) and
  Allow once are **in memory**: after a restart the bot re-sends and is asked again. One instance per database lives in
  `AgentCommandService.assistants(database)`, shared by the handler and the UI, which `VestaApp(assistantsFor = …)` picks up.
- `PantryCommandHandler` order is now: daemon gate → parse → active assistant → `ListHomes` (filtered, no policy) → **replay of an
  already-answered command** → grant gate (`NotPermitted` / `Ask` held / `Allow`) → apply → record. The replay check moved ahead
  of the grant gate after the acceptance tests showed that a re-sent, already-applied command under `Ask` raised a second card.
- A Home the assistant has no grant for answers "not permitted for this home" whether or not the Home exists, so a bot can't probe.
- `ListHomes` is not itself a switch in the UI: saving any grant adds `ListHomes` (policy `Auto`) for that Home.
- `AgentApprovalCard` is now generic (title, body, confirm label). `AgentApprovalHost` shows a pending assistant first (Review… /
  Deny), else a held command (Allow once / Deny). Review… opens the editor over whatever screen is showing.
- Screens: `AssistantsScreen` (list), `AssistantEditScreen` (add/edit, activity, remove), hosts in `AssistantHosts.kt`; Settings →
  Assistants (row only when `assistants` is provided).
- The add/edit screen shows `x0x trust set <agentId> trusted` once an agent ID is present: adding an assistant does not set x0x trust (Vesta has no client call for it), and until the daemon trusts the agent its commands are dropped silently.
- Not done: the QR / `vesta-assistant:` link for adding by scan, the per-assistant rate limit (Open Question 4), Android command
  wiring (ADR-009's TODO still stands; the screens do work there), and showing times in the activity list.
- The `VESTA_AGENT_ALLOWLIST` agents are active with every capability on every Home, policy auto, and leave no activity rows.

## Acceptance criteria

Written as the user's scenarios (see `docs/testing/acceptance-tests.md`); each needs at least one acceptance test.

1. **Add the bot ahead of time.** Given I have two Homes, when I add my bot by its agent id in Settings → Assistants,
   grant it Pantry-add for "The Johnsons" only with policy `auto`, and the bot sends an add for that Home, then the
   item is in that Pantry with no approval card.
2. **Other Homes stay closed.** Given that grant, when the bot sends an add for my other Home, then it gets a `Reject`
   ("not permitted for this home") and that Pantry is unchanged; and when it lists Homes, only "The Johnsons" is
   returned.
3. **Unknown bot asks first.** Given I have not added the bot, when it sends a command, then it gets a "not approved"
   `Reject`, a pending assistant appears in Settings → Assistants, and a card offers Review… and Deny; nothing changed.
4. **Review before allowing.** Given a pending assistant, when I tap Review…, tick Pantry-add for one Home and save,
   then the assistant is active and its re-sent command is applied.
5. **Ask policy.** Given a grant with policy `ask`, when the bot sends an add, then an approval card appears and the
   Pantry is unchanged; when I choose Allow once and the bot re-sends the same command id, then the item is added once.
6. **New capabilities are not inherited.** Given a bot granted only Pantry-add, when it sends a command from a
   capability I have not granted, then it is rejected for permission.
7. **Revoke.** Given an active assistant, when I remove it, then its next command is treated as from an unknown agent
   (pending, nothing applied) and its grants are gone after restarting the app.
8. **Activity.** Given the bot added an item and was rejected once, then both appear in its Activity list with the Home,
   outcome and summary.
9. **Upgrade keeps working.** Given a database with an `allowed` AgentAccess row from before this ADR, when the app
   migrates, then that agent is an active assistant with Pantry-add and list-Homes for every existing Home, and its
   commands still apply with no card.
10. **Survives restart.** Given assistants, grants and activity, when the app restarts, then they are all still there.

## Open Questions

1. **Per-device or per-household grants.** Syncing grants would let one approval cover all the user's devices, but
   makes "which device does the bot command" ambiguous, and a grant document would itself need protecting from the bot's
   own host. Starting local.
2. **Several people in a Home.** Is any member allowed to grant an assistant for a shared Home, or only the Home's
   creator/admin? Today the grant is a personal decision on one device, so the question only matters once grants sync.
3. **Assistant-supplied display name.** Does x0x expose an agent's display name to a peer? Needed for the card and list.
4. **Rate limits** on `auto` grants (a looping bot could add thousands of items). A per-assistant cap per minute, with a
   "paused — too many commands" state, is likely worth adding before this ships.
5. **Machine-readable reject reasons** (ADR-009 OQ 2) matter more now that "not permitted" and "waiting for approval"
   are distinct outcomes the agent should branch on.
6. **Observer role** for continuous read access (see "What this does not do").

## References

- [[ADR-009]] — agent commands over x0x; two gates; `AgentAccess`, `AgentApprovalCard`.
- [[ADR-012]], [[ADR-013]] — devices are the user's own, admins, full replica (why an assistant is not one).
- [[ADR-014]], [[ADR-018]] — members are people who share the Home's documents (why an assistant is not one).
- `skills/vesta/` — the agent-facing skill; needs a section on grants and the new `Reject` summaries when implemented.
