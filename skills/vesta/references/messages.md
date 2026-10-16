# Vesta command messages

Direct-message payloads are UTF-8 JSON (JSON-LD, compact form). Vesta does **not** run a JSON-LD processor: use
this exact `@context` and these exact term names, or the message is ignored. Payloads over 16 KiB are rejected.

`CONTEXT` below is always:

```json
["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]
```

Every command has a unique `id` (use `urn:uuid:<new uuid>`); it is the idempotency key. Replies arrive on your
instance's `GET /direct/events`, flat JSON `{sender, machine_id, payload (base64), received_at, verified,
trust_decision}`; the reply's own `inReplyTo` matches your command's `id`. Only trust replies whose `sender` is
Vesta's agent id.

## List Homes — `Read` of `urn:vesta:homes`

```json
{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…",
 "object": {"type": "Collection", "id": "urn:vesta:homes"}}
```

Reply — an `Accept` whose `result` lists only the Homes the user has granted you (possibly none; `schema:identifier` is the bare Home id to use below):

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "Collection", "id": "urn:vesta:homes", "totalItems": 2,
            "items": [{"type": "Object", "id": "urn:vesta:home:id-aaa",
                       "schema:identifier": "id-aaa", "schema:name": "The Johnsons"}, …]}}
```

Read-only, so it is answered freshly each time.

## Read the Pantry — `Read` of a Home's pantry

Needs the user's Pantry **Read** permission for that Home (a Write-only grant is refused with "not permitted for this home").

```json
{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…",
 "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry"}}
```

Reply — an `Accept` whose `result` lists the most-used items first, at most 200 (`totalItems` is how many the Pantry holds):

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry", "totalItems": 1,
            "items": [{"type": "schema:Product", "id": "<pantry key>", "schema:name": "Heinz Ketchup",
                       "schema:category": ["Condiments", "Ketchup"], "schema:size": "1 bottle",
                       "schema:gtin": "5000157024671", "schema:amountOfThisGood": 3}]}}
```

`schema:size` and `schema:gtin` are left out when the item has none; `schema:amountOfThisGood` is the stock count.
Read-only, so it is answered freshly each time and a repeat is never "already processed".

## Add to the Pantry — `Add` of a `schema:Product`

```json
{"@context": CONTEXT, "type": "Add", "id": "urn:uuid:…",
 "object": {
   "type": "schema:TypeAndQuantityNode",
   "schema:amountOfThisGood": 2,
   "schema:typeOfGood": {
     "type": "schema:Product",
     "schema:name": "Heinz Ketchup",
     "schema:category": ["Condiments", "Ketchup"],
     "schema:gtin": "5000157024671",
     "schema:size": "1 bottle"}},
 "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry"}}
```

| Field | Rule |
| --- | --- |
| `id` | Required, non-blank, unique per command. |
| `target.id` | Required: `urn:vesta:document:<homeId>:pantry` with a Home id from the list. |
| `schema:name` | Required, non-blank. |
| `schema:category` | Optional. A string, or an array of 1–2 strings: `[primary, secondary]`. New item without one → category "Uncategorized". |
| `schema:gtin` | Optional barcode. Items are matched by barcode if present, else by name (case-insensitive). |
| `schema:size` | Optional package size — see [Package size](#package-size). |
| `schema:amountOfThisGood` | Optional integer 1–9999, default 1. **Added to** the current stock. |

If the item already exists, omitted optional fields keep their existing values.

Reply on success:

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "schema:Product", "id": "<pantry key>", "schema:name": "Heinz Ketchup",
            "schema:amountOfThisGood": 3}}
```

`result.schema:amountOfThisGood` is the stock **after** your add.

## Take stock out — `Remove`, and set it exactly — `Update`

Both need Pantry **Write** and an item already in the Pantry; both reply with the same `Accept` as an add (the stock **after**).
The item is found by `schema:gtin` if given, else by `schema:name` (case-insensitive, also for items stored under a barcode).
Missing item → `Reject` "unknown item". Both are recorded by command `id`, so a re-sent id is answered, not applied twice.

```json
{"@context": CONTEXT, "type": "Remove", "id": "urn:uuid:…",
 "object": {"type": "schema:TypeAndQuantityNode", "schema:amountOfThisGood": 2,
            "schema:typeOfGood": {"type": "schema:Product", "schema:name": "Heinz Ketchup", "schema:gtin": "5000157024671"}},
 "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:pantry"}}
```

| Type | `schema:amountOfThisGood` | Effect |
| --- | --- | --- |
| `Remove` | Optional integer 1–9999, default 1 | Stock is **lowered** by it, stopping at 0. |
| `Update` | **Required** integer 0–9999 | Stock is **set** to it (0 allowed). |

`schema:gtin` is optional; `schema:name` is required.

## Shopping list — `Read`, `Add`, `Update`, `Remove`

The target (or, for a read, the object) is `urn:vesta:document:<homeId>:shopping-list`. Reads need the user's Shopping list
**Read** permission, the rest **Write**; the Pantry's permissions do not cover it. It never touches the Pantry.

**Read** — `{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…", "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:shopping-list"}}`.
The `Accept` result is a `Collection` (at most 200 items; `totalItems` is the real count) of

```json
{"type": "schema:Product", "id": "<key>", "schema:name": "Milk", "schema:category": ["Dairy"], "schema:size": "1l",
 "schema:gtin": "5000", "schema:amountOfThisGood": 3, "got": 1, "outOfStock": false}
```

`schema:amountOfThisGood` is how many are wanted, `got` how many are picked up (it counts as ticked off when above 0 and not
`outOfStock`). `schema:size` and `schema:gtin` are left out when the item has none. Answered fresh each time.

**Write** — same `schema:TypeAndQuantityNode` / `schema:Product` object as a Pantry add, with the shopping-list `target`:

| Type | `schema:amountOfThisGood` | Effect |
| --- | --- | --- |
| `Add` | Optional integer 1–9999, default 1 | Puts that many on the list; an item already there has the amounts added together. Category (default "Uncategorized"), size and gtin as for the Pantry. |
| `Update` | **Required** integer 0–9999 | Sets how many are **picked up** (0 un-ticks). The item must be on the list. |
| `Remove` | Not used | Takes the item's whole line off the list. The item must be on the list. |

`Update` and `Remove` find the item by `schema:gtin` if given, else by `schema:name` (case-insensitive). Each write replies with an
`Accept` whose `result` is the line afterwards (for `Remove`, as it was), in the shape above. Writes are recorded by command
`id`, so a re-sent id is answered, not applied twice. A missing item → `Reject` "unknown item".
## Cookbook — `Read` (needs Cookbook **Read**), `Add` / `Remove` (need Cookbook **Write**)

Addresses: the collection is `urn:vesta:document:<homeId>:cookbook`; one recipe is `urn:vesta:document:<homeId>:cookbook:<recipeId>`.

**List** — `Read` of the collection (answered fresh each time):

```json
{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…",
 "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:cookbook"}}
```

Reply: an `Accept` whose `result` is a `Collection` of summaries, by name, at most 200 (`totalItems` is the real count):

```json
{"type": "Collection", "id": "urn:vesta:document:<homeId>:cookbook", "totalItems": 1,
 "items": [{"type": "schema:Recipe", "id": "<recipeId>", "schema:identifier": "<recipeId>", "schema:name": "Pancakes",
            "schema:description": "…", "schema:recipeCategory": "Breakfast", "schema:recipeYield": 4,
            "schema:totalTime": "PT15M", "schema:keywords": ["quick"]}]}
```

**One recipe** — `Read` with `"id": "urn:vesta:document:<homeId>:cookbook:<recipeId>"`. The `result` is that recipe with the extra fields:

```json
{"type": "schema:Recipe", "id": "<recipeId>", "schema:name": "Pancakes", "schema:prepTime": "PT5M", "schema:cookTime": "PT10M",
 "schema:recipeIngredient": [{"type": "schema:QuantitativeValue", "schema:name": "plain flour", "schema:value": 200.0,
                              "schema:unitText": "g", "schema:description": "sifted"}],
 "schema:recipeInstructions": [{"type": "schema:HowToStep", "schema:text": "Whisk", "schema:performTime": "PT2M"}],
 "schema:isBasedOn": "https://…"}
```

Photos and videos are never included (they stay on the user's devices). Unknown id → `Reject` "unknown recipe".

**Add** — `Add` of a `schema:Recipe`, `target` the collection:

```json
{"@context": CONTEXT, "type": "Add", "id": "urn:uuid:…",
 "object": {"type": "schema:Recipe", "schema:name": "Pancakes", "schema:description": "…",
            "schema:recipeCategory": "Breakfast", "schema:recipeYield": 4, "schema:prepTime": "PT5M", "schema:cookTime": "PT10M",
            "schema:keywords": ["quick", "sweet"], "schema:isBasedOn": "https://…",
            "schema:recipeIngredient": ["2 eggs", {"type": "schema:QuantitativeValue", "schema:name": "plain flour", "schema:value": 200, "schema:unitText": "g"}],
            "schema:recipeInstructions": ["Whisk", {"type": "schema:HowToStep", "schema:text": "Fry", "schema:performTime": "PT10M"}]},
 "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:cookbook"}}
```

| Field | Rule |
| --- | --- |
| `schema:name` | Required, at most 200 characters; unique in the cookbook ignoring case, else `Reject` "recipe already exists". |
| `schema:description`, `schema:isBasedOn` | Optional strings, at most 2000 characters. |
| `schema:recipeCategory` | Optional: Breakfast, Lunch, Dinner (default), Starter, Side, Dessert, Baking, Snack, Drink, Other (any case; anything else → Other). |
| `schema:recipeYield` | Optional integer 1–999, default 4. |
| `schema:prepTime`, `schema:cookTime`, `schema:performTime` | Optional ISO 8601 `PT#H#M` (or whole minutes), at most 7 days. |
| `schema:recipeIngredient` | Optional array, at most 100. Each a string, or an object with `schema:name` (required), `schema:value` (number ≥ 0), `schema:unitText`, `schema:description`. |
| `schema:recipeInstructions` | Optional array, at most 100, in order. Each a string, or an object with `schema:text` and optional `schema:performTime`. |
| `schema:keywords` | Optional string (comma-separated) or array, at most 20. |

Reply: an `Accept` whose `result` is `{"type": "schema:Recipe", "id": "<recipeId>", "schema:identifier": "<recipeId>", "schema:name": …}`.

**Remove** — `Remove` of a `schema:Recipe` named by `schema:identifier` (the recipe id) or, failing that, `schema:name` (ignoring case);
`target` the collection. Same `Accept` reply (the removed recipe); unknown → `Reject` "unknown recipe". Removing is permanent for every device in the Home.

Both writes are recorded by command `id`, so a re-sent id is answered, not applied twice.

## Meal plan — `Read`, `Add`, `Remove` of `urn:vesta:document:<homeId>:mealplan`

The plan holds cookbook **recipe keys** per day and slot, nothing else. Slots are `Breakfast`, `Lunch`, `Dinner` (any case on input).

```json
{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…",
 "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:mealplan",
            "schema:startDate": "2026-10-12", "schema:endDate": "2026-10-18"}}
```

`schema:startDate` / `schema:endDate` are optional, inclusive `yyyy-mm-dd` limits (a bad date or an end before the start is rejected).
Needs Meal planner **Read**. Reply — an `Accept` whose `result` lists the meals earliest day first, at most 200 (`totalItems` counts all that match):

```json
{"@context": CONTEXT, "type": "Accept", "id": "urn:uuid:…", "inReplyTo": "<your id>", "object": "<your id>",
 "result": {"type": "Collection", "id": "urn:vesta:document:<homeId>:mealplan", "totalItems": 1,
            "items": [{"type": "Object", "id": "urn:vesta:meal:2026-10-13:Dinner:<recipe key>",
                       "schema:startDate": "2026-10-13", "slot": "Dinner",
                       "schema:recipe": {"type": "schema:Recipe", "id": "<recipe key>"}}]}}
```

Planning and unplanning need Meal planner **Write**:

```json
{"@context": CONTEXT, "type": "Add", "id": "urn:uuid:…",
 "object": {"type": "Object", "schema:startDate": "2026-10-13", "slot": "Dinner",
            "schema:recipe": {"type": "schema:Recipe", "id": "<recipe key>"}},
 "target": {"type": "Collection", "id": "urn:vesta:document:<homeId>:mealplan"}}
```

`Add` plans the recipe (planning it where it already is changes nothing and is still accepted); `Remove` takes it out and needs it to
be planned there (else `Reject` "unknown meal"). `Update` is not accepted. Vesta does not check the key against the cookbook. The
`Accept` `result` is the meal, in the shape above. Writes are recorded by command `id`, so a re-sent id is answered, not applied twice.
## Tasks and chores — `Read` (needs Tasks **Read**), `Add` / `Update` / `Remove` (need Tasks **Write**)

The Home's **shared** tasks only: `urn:vesta:document:<homeId>:tasks`. The user's private tasks are a different document and
cannot be addressed (a message for `…:private-tasks` is rejected). A task is a `schema:Action`.

**Read** — `{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…", "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:tasks"}}`.
Answered fresh each time. The `Accept` result is a `Collection` of at most 200 tasks (dated ones first; `totalItems` is the real count):

```json
{"type": "schema:Action", "id": "<taskId>", "schema:identifier": "<taskId>", "schema:name": "Take the bins out",
 "schema:description": "Both bins", "schema:endTime": "2026-10-14",
 "schema:actionStatus": "schema:PotentialActionStatus", "schema:repeatFrequency": "P1W", "schema:agent": ["m:abc"]}
```

`schema:endTime` is the task's first/only due day (left out for a task with no date). `schema:actionStatus` is
`schema:CompletedActionStatus` when the task's current occurrence is done (a repeating task is done one occurrence at a time:
its latest due day so far), else `schema:PotentialActionStatus`. `schema:repeatFrequency` (an ISO 8601 duration such as `P1W`,
`P2D`, `P1M`, `P1Y`) and `schema:agent` (the member refs it is assigned to) are left out when there are none. You can read
repeats and assignees but not set them.

**Write** — a `schema:Action` object with the tasks `target`:

| Type | Object fields | Effect |
| --- | --- | --- |
| `Add` | `schema:name` (required, ≤ 200 chars), optional `schema:description` (≤ 2000), optional `schema:endTime` (`yyyy-MM-dd`) | Adds a task. Same name (any case) and same day as one already there → `Reject` "task already exists". |
| `Update` | `schema:identifier` or `schema:name`, and `schema:actionStatus` (`schema:CompletedActionStatus` or `schema:PotentialActionStatus`) | Marks the task's current occurrence done / not done. |
| `Remove` | `schema:identifier` or `schema:name` | Deletes the task (its done history stays). |

The task is found by `schema:identifier` if given, else by `schema:name` ignoring case; no match → `Reject` "unknown task", and a
name shared by several tasks → "several tasks have that name: use schema:identifier". Each write replies with an `Accept` whose
`result` is the task afterwards (for `Remove`, as it was), in the shape above. Writes are recorded by command `id`, so a re-sent
id is answered, not applied twice.
## Calendar — `Read` (needs Calendar **Read**), `Add` / `Update` / `Remove` (need Calendar **Write**)

The address is `urn:vesta:document:<homeId>:calendar` (the Home's shared calendar; the user's private calendar has no address).

**Times.** A timed event's `schema:startDate` / `schema:endDate` are UTC instants like `2026-10-20T18:30:00Z`; an offset
(`2026-10-20T19:30:00+01:00`) is accepted and converted, a time with no offset is refused (Vesta shows each device's own zone, so it
will not guess one). An all-day event uses plain dates `2026-10-20`, and its `schema:endDate` is the **last** day, inclusive. Both ends
of an event are the same kind. Replies always give instants in UTC (timed) or dates (all-day) and `"allDay": true|false`.

**Read** — `{"@context": CONTEXT, "type": "Read", "id": "urn:uuid:…", "object": {"type": "Collection", "id": "urn:vesta:document:<homeId>:calendar"}}`,
optionally with `"schema:startDate"` and/or `"schema:endDate"` on the object to keep only events touching that range, both ends
inclusive (a plain date covers the whole day, UTC). Answered fresh each time. The `Accept` result is a `Collection` (earliest first, at
most 200; `totalItems` is the real count) of

```json
{"type": "schema:Event", "id": "<eventId>", "schema:identifier": "<eventId>", "schema:name": "Dentist",
 "schema:startDate": "2026-10-20T10:00:00Z", "schema:endDate": "2026-10-20T11:00:00Z", "allDay": false,
 "schema:location": "High St", "schema:description": "…",
 "schema:eventSchedule": {"type": "schema:Schedule", "schema:repeatFrequency": "P1W"}}
```

`schema:location`, `schema:description` and `schema:eventSchedule` are left out when empty / not repeating. Who an event is for and its
colour are not exposed.

**Add** — `Add` of a `schema:Event` to `target` `urn:vesta:document:<homeId>:calendar`: `schema:name` and `schema:startDate` required;
`schema:endDate` (default: the same day for an all-day event, one hour for a timed one), `schema:location` (≤ 500 characters),
`schema:description` (≤ 2000), and `schema:eventSchedule` with `schema:repeatFrequency` `P1D` / `P1W` / `P1M` / `P1Y` are optional.
An event with the same title (any case) and the same times is refused: "event already exists".

**Update** — `Update` with `schema:identifier` (the event id) and any of the fields above; only the fields you give change. A new
`schema:startDate` without a new `schema:endDate` moves the whole event (a timed one keeps its length). An `schema:eventSchedule`
with no `schema:repeatFrequency` makes it stop repeating; a blank `schema:location` / `schema:description` clears it.

**Remove** — `Remove` with `schema:identifier`.

Each write replies with an `Accept` whose `result` is the event afterwards (for a remove, as it was). Writes are recorded by command
`id`, so a re-sent id is answered, not applied twice. Unknown id → `Reject` "unknown event".

## Rejections

`{"type": "Reject", "inReplyTo": "<your id>", "summary": "<human-readable reason>", …}`. Summaries include:

- `not approved: ask the user to approve this agent in the Vesta app, then retry`
- `not permitted for this home` — you have no grant for that Home (a Home that doesn't exist looks the same)
- `waiting for approval: the user has been asked, retry once they have answered` — re-send the **same** command id after the user taps Allow once
- `unknown item` — a `Remove`/`Update` named an item that isn't in that Pantry
- `unknown event` — a calendar `Update` or `Remove` named an event that isn't on that calendar
- `unknown recipe` — a cookbook `Read` of one recipe, or a `Remove`, named a recipe that isn't there
- `recipe already exists` — a recipe with that name (ignoring case) is already in the cookbook
- `unknown home`
- `quantity out of range`, `title is required`, `type must be Add or Read`, `target.id must be …`, `payload too large`, …

## Silence

Vesta never replies to messages its x0x daemon doesn't vouch for (sender unverified, or not trusted), nor to JSON
that isn't one of these commands. No reply at all almost always means missing trust in one direction, or the app
not running.

## Package size

Send `schema:size` as `<amount><unit> <container>` — an amount, then a unit and/or a container. Vesta tidies what you
send into its canonical form and echoes the stored value back as `result."schema:size"` in the `Accept`.

| Part | Values (aliases such as `litre`, `ounce`, `can`, plurals and any case are accepted) |
| --- | --- |
| amount | a number: `500`, `1.5` |
| unit | `g` `kg` `ml` `l` `oz` `lb` `fl oz` `pint` `gal` |
| container | `tin` `jar` `bottle` `packet` `box` `bag` `carton` `tub` `tube` `pouch` `sachet` `tray` `roll` `pack` |

Good: `500ml`, `1kg bag`, `15 oz can` (stored as `15 oz tin`), `6 pack`, `1 bottle`. Use a count with a container
(`6 pack`) when there is no weight or volume. Text Vesta doesn't recognise is still stored (trimmed), not rejected, but
it won't appear in the app's pickers — stick to the grammar. Omit `schema:size` for loose items with no packaging (apples, a bunch of bananas) or if you don't know it.
