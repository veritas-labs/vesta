# ADR-008: Shopping List Document Model

## Status

Proposed — 2026-09-26 (drafted), amended 2026-09-28. Vesta's first concrete feature document under [[ADR-006]]'s
scheme. Amended three times, all by explicit user decision:

1. **2026-09-26** — Item state (was Open Question 1) — resolved: accept Automerge's default conflict-resolution
   behavior, no custom merge policy.
2. **2026-09-26** — Item-suggestion history (originally designed as a local, per-device SQLite table — Open
   Question 2) — superseded within that revision: history became a second, shared, synced Automerge document
   ("Pantry"), not a local cache.
3. **2026-09-28** — Five more resolutions against that Pantry design (former Open Questions 2, 3, 6, 7, 8):
   shopping-list items get a back-reference to their Pantry entry; Pantry entries received over the wire (not just
   locally added) must also populate the local Pantry; shopping-list deletion is confirmed hard-delete; Pantry
   items gain a stock count (inventory, not just usage history); Pantry entries can be deleted by a user. See
   Decision.

**4. 2026-10-05 — Shopping list simplified (supersedes the shopping-list half of this ADR).** A Home has exactly
**one** shopping list, shared by all members, and the list is *static*: items have no `state`. Checking items off
belongs to a future "Shopping session" (tied to a store), not the list. The Pantry half of this ADR stands. See
"Fourth amendment: one static list per Home" below, which overrides the shapes and API in the Decision section
wherever they disagree (multiple lists, `state`/`ItemState`, `setItemState`, `PantryContributedItem`).

## Context

Shopping lists are the first feature being built on top of [[ADR-005]]'s "one Automerge `Document` per Home" and
[[ADR-006]]'s `documentId = "$homeId:$feature"` envelope. Neither ADR designed any feature's actual data shape —
ADR-006 explicitly left "where a given feature's data should live" as "a data-modeling call for whoever builds
each feature." This ADR makes that call for shopping lists, and gives [[ADR-007]]'s generic
`AutomergeDocument`/`AutomergeSyncState`/`AutomergeSearchIndex` tables their first real `feature` value and
`entityId`s to key against.

Requirements (from the user, 2026-09-26): a Home has one shopping-list document that holds **multiple** shopping
lists. Each list has a title. Each list holds items, and each item has: primary category (e.g. "Condiments"),
secondary category (e.g. "Ketchup"), title (e.g. "Heinz Ketchup"), quantity (e.g. 2), package size (e.g. "6 pack"),
and a barcode.

Amended, 2026-09-26: each item is in one of three states — `TODO` (not yet gotten), `CHECKED` (fully in the
cart/basket), or `PARTIAL_CHECKED` (only some of the requested quantity is in the cart/basket). Separately, adding
an item must also be recorded so the app can suggest previously-used items when adding to a list again.

Second amendment, 2026-09-26: rather than a local, per-device suggestion cache, the user wants this history to be
**shared across the household** — and reframed as a "Pantry": a synced record of products the household knows
about, referenceable when building any shopping list, not merely an autocomplete side-table.

Third amendment, 2026-09-28, resolving five of the Open Questions the second amendment left behind:

- **Shopping items should back-reference their Pantry entry** (former Open Question 2, resolved: yes).
- **"Pantry is also a household shared construct. Any item received over the wire should also be in the
  Pantry."** (former Open Question 3, resolved: yes — not just locally-initiated adds.)
- **Shopping lists should be hard deleted** (former Open Question 6, confirmed: the already-designed hard-delete
  primitive is correct, no archival needed).
- **"Pantry items should include a stock count, to act as an inventory."** (former Open Question 7, resolved:
  yes — Pantry models both usage history and on-hand stock.)
- **"Pantry will need delete functionality. A user should be able to delete an item from their Pantry."** (former
  Open Question 8, resolved: yes.)

**Implementation progress (2026-09-28):** the Pantry document-model layer (`PantryItem`, `newPantryDocument`,
`readPantry`/`readPantryItem`, `upsertPantryItem`, `adjustStock`/`correctStock`, `deletePantryItem`,
`pantryDedupKey`) is implemented at
`shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/PantryDocument.kt`, with unit tests at
`shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/automerge/PantryDocumentTest.kt`, following `TaskListDocument.kt`'s
extension-function API shape. `upsertPantryItem` takes Pantry's own fields directly rather than a `ShoppingItem`
(the shape below still shows `ShoppingItem` as the eventual caller shape once the shopping-list document exists) —
the shopping-list document, [[ADR-006]]'s `MultiDocumentSyncHub`/envelope, [[ADR-007]]'s SQLite persistence/search
tables, `PantryContributedItem`, and `contributeSyncedItemsToPantry` are all still unbuilt. Placed in `jvmMain`
only (not a shared source set) because that's where `automerge` is wired up today, matching
`TaskListDocument.kt`'s placement on `poc/automerge-over-x0x`; sharing this code with `androidMain` (which also
declares the `automerge`/`automerge-androidnative` dependencies) would need a new intermediate KMP source set, not
addressed here.

### Prior art in this codebase

`TaskListDocument.kt` (on `poc/automerge-over-x0x`, not on `main`) is the only existing example of modeling app
data with `org.automerge`'s Java bindings directly (not `org.automerge.repo`): a `title` string at
`ObjectId.ROOT`, and an `ObjectType.LIST` of plain strings for `tasks`. It establishes two conventions this ADR
follows: (1) a small top-level `Read`/`Transaction` extension-function API per document type, not a generic
object-mapping layer; (2) a missing key reads as an empty/default value rather than the API distinguishing
"absent" from "empty" — there is no separate `null` handling.

Shopping-list items are one level deeper than a task (several scalar fields per item, not one string), and this
document holds many lists rather than one, so this ADR has two structural questions `TaskListDocument` didn't
face: how multiple lists are addressed within one document, and how each list's items are addressed within it.

## Decision

### `feature` names: `"shopping-list"` and `"pantry"` — two documents, not one

[[ADR-006]]'s example feature vocabulary (`"tasks"`, `"chores"`, `"lists"`) was illustrative, not a reservation.
This ADR defines two features, each its own `Document`/`documentId`:

- `"shopping-list"` → `"$homeId:shopping-list"` — the lists and their items, as originally specified.
- `"pantry"` → `"$homeId:pantry"` — the household's shared product record (see below).

Pantry is a **separate document**, not another top-level key inside the shopping-list document, for the same
reason [[ADR-006]] splits features apart at all: the two have different churn patterns (shopping-list items are
edited constantly during an active shop; Pantry entries change only when a genuinely new product is added, or now,
when stock is adjusted) and different lifecycles (a Pantry entry outlives every shopping list it was ever added
from, including lists that get deleted entirely) — bundling them would mean every Pantry-only read (e.g. rendering
autocomplete suggestions while typing a new item, with no shopping list even open) pulls in the shopping-list
document's sync/storage weight for no reason, and vice versa.

### One document, both "which list" and "which item" addressed by a `Map` keyed on a UUID — not a `List`

```
ROOT (map)
└─ "lists": Map<listId, ShoppingList>          -- ObjectType.MAP, keyed by UUID string

ShoppingList (map)
├─ "id": Str            -- same value as its key in the parent map
├─ "title": Str
├─ "createdAt": Str     -- ISO-8601; display/sort key, see below
└─ "items": Map<itemId, ShoppingItem>          -- ObjectType.MAP, keyed by UUID string

ShoppingItem (map)
├─ "id": Str                  -- same value as its key in the parent map
├─ "primaryCategory": Str     -- e.g. "Condiments"
├─ "secondaryCategory": Str   -- e.g. "Ketchup"
├─ "title": Str               -- e.g. "Heinz Ketchup"
├─ "quantity": Int            -- e.g. 2
├─ "packageSize": Str         -- e.g. "6 pack"
├─ "barcode": Str             -- key omitted entirely when there is no barcode
├─ "state": Str               -- "TODO" | "CHECKED" | "PARTIAL_CHECKED"; key omitted == "TODO"
└─ "pantryItemKey": Str       -- the Pantry entry this item was added from/against; see below
```

Both nesting levels use `ObjectType.MAP` keyed by a UUID the app generates on create (`java.util.UUID` string, the
same convention `Home`/`HomeMember` already use for SQLite primary keys), **not** `ObjectType.LIST`. This is the
one real structural design choice for the shopping-list document, so it gets its own rationale:

- **Point addressing.** Editing a specific item's quantity, or deleting one item, or renaming one list, needs to
  reach that exact object directly (`tx.get(itemsMapId, itemId)` → that item's own `ObjectId`, then `tx.set`/
  `tx.delete` into it). A `List`'s only native addressing is by position, which shifts under concurrent
  insert/delete and would force scanning by an `id` field inside each element to find the right index anyway —
  the `Map` gets the same O(1)-by-id addressing `List` would need a workaround for.
- **Display order was never insertion order anyway.** A shopping list's natural presentation is grouped by
  `primaryCategory` → `secondaryCategory` → `title` (an aisle-by-aisle view), not the order items were typed in.
  Since insertion order isn't the thing being displayed, `List`'s main advantage — CRDT-preserved sequence order —
  buys nothing here. Sort/group happens at read time (see below), the same place [[ADR-007]]'s search projection
  already has to walk every entity to index it.
- **Concurrent creation composes cleanly.** Two members adding a list (or an item) concurrently just add two
  distinct map keys — no interleaving to reason about, unlike two concurrent `List` insertions at the same
  position.
- **Cost accepted:** `Map` key iteration order across peers is not guaranteed or meaningful, so *something* must
  provide a stable display order. `createdAt` (set once, at creation, never rewritten) is that field for lists;
  items don't need one since they're always grouped/sorted by category+title, not by creation order.

`id` is stored redundantly as both the map key and a field inside the object it keys, because `org.automerge`'s
`Read`/`Transaction` API hands back an object's contents once you already have its `ObjectId` — round-tripping
back to "which key did I look this up under" isn't always convenient (e.g. a search-index hit resolves straight
to an `ObjectId`/entity, not to a parent-map traversal). This mirrors `AmValue.Map`/`AmValue.List` always exposing
their own `.id` — the object doesn't need to ask its parent who it is. (Pantry's items use the same "`id` field
mirrors the map key" convention, but with a different *source* for that id — see below.)

### Rejected alternative: `ObjectType.LIST` for items, matching `TaskListDocument`'s `tasks` list

Considered, to stay consistent with the one existing precedent in this codebase. Rejected because
`TaskListDocument`'s `tasks` is a list of bare strings with no per-element identity at all — there's nothing to
address by id because there's no id, only content. Shopping-list items have real per-entity identity (needed for
[[ADR-007]]'s `entityId`, for point edits, and for barcode-driven "already have this item" lookups a barcode field
implies is coming) that a bare `List` doesn't model. This ADR's `Map`-keyed shape is a deliberate departure from
`TaskListDocument`'s precedent, not an oversight of it.

### `quantity` is a plain `Int`, not an Automerge `Counter`

`org.automerge` supports a `Counter` type whose concurrent increments/decrements merge additively instead of one
write winning arbitrarily. Rejected for `quantity`: the stated interaction is typing/setting a quantity (e.g. "2"),
not stepping it with +/- taps, and `Counter` only merges well under increment/decrement operations — a concurrent
"set to 2" and "set to 5" against a `Counter` doesn't compose any better than a plain register would. If the UI
later becomes stepper-based, revisit; not assumed here. (Contrast Pantry's `useCount` and `stockCount` below,
which genuinely are increment/decrement-driven and are modeled as `Counter`s.)

### Item state: a three-value string enum, not a boolean — Automerge's default conflict handling accepted as-is

`state` is `"TODO"` | `"CHECKED"` | `"PARTIAL_CHECKED"`, stored as a plain `Str` register (same conflict-resolution
character as `title`/`quantity`/every other scalar field on the object). A missing key reads as `"TODO"`,
consistent with this ADR's existing missing-key-means-default convention — a freshly-added item needs no explicit
write to be in the default state. Represented in Kotlin as an `enum class ItemState { TODO, CHECKED,
PARTIAL_CHECKED }`, mapped to/from the stored string name at the `Read`/`Transaction` boundary rather than exposed
as a raw string in `ShoppingItem`.

Not modeled as a boolean plus a separate "how much did you get" quantity (e.g. `checkedQuantity: Int`) — the
requirement as given is a three-state status, not a partial-quantity count. A `checkedQuantity` field is a natural
follow-on if the product need becomes "3 of 6 are in the basket" rather than just "some of them are," but that's
additive (a new optional field) and not assumed here. (This gap resurfaces below when Pantry stock enters the
picture — see "Pantry stock count.")

**Concurrent conflicts:** two members setting different states on the same item before syncing (e.g. one marks
`CHECKED` while another marks `PARTIAL_CHECKED`) resolve to whichever write Automerge's default register
conflict-resolution picks — confirmed by explicit user decision as acceptable, at least for now, rather than
designing a custom three-state merge policy. No further work needed here unless this proves confusing in practice.

### Shopping lists are hard-deleted — confirmed

`deleteShoppingList` is a `Map` key removal on the `lists` map, exactly as originally designed. Confirmed by
explicit user decision: no archival/soft-delete needed. A deleted list's items go with it; nothing about a list's
history survives deletion (contrast Pantry, where the *products* a deleted list mentioned live on independently,
since Pantry already received its own upserts at the time each item was added — see below).

### Pantry: a second, shared Automerge document — not local SQLite, not a synced copy of a suggestion table

`documentId = "$homeId:pantry"`. One document per Home (same "one Document per feature" scoping the shopping-list
document uses), registered with [[ADR-006]]'s `MultiDocumentSyncHub` alongside the shopping-list document — no new
sync mechanism, no new envelope handling. This is exactly the scenario `MultiDocumentSyncHub` was designed for:
one Home, more than one document.

```
ROOT (map)
└─ "items": Map<key, PantryItem>          -- ObjectType.MAP

PantryItem (map)
├─ "id": Str                  -- same value as its key in the parent map (see "Dedup key" below)
├─ "primaryCategory": Str
├─ "secondaryCategory": Str
├─ "title": Str
├─ "packageSize": Str
├─ "barcode": Str             -- key omitted when there is no barcode
├─ "useCount": Counter        -- how many times this product has been added to a list; incremented only
├─ "lastUsedAt": Str          -- ISO-8601, plain register; display/sort hint only
└─ "stockCount": Counter      -- how many the household currently has on hand; incremented and decremented
```

**Dedup key — deterministic, not a random UUID.** Unlike `ShoppingList`/`ShoppingItem`, a `PantryItem`'s map key
(and its `id` field, kept equal to that key per this ADR's existing convention) is `barcode` when the item being
added has one, else `normalizeTitle(title)` (trim + lowercase). This is deliberate: two household members' devices
adding "Heinz Ketchup" independently must land on the **same** map key for their writes to converge into one
Pantry entry rather than two. A random per-device UUID (as `ShoppingList`/`ShoppingItem` use) would guarantee two
separate, never-reconciled entries for the same real-world product — Pantry's entire point is one shared record
per product, so identity has to be derived from the product, not minted per write.

**`useCount` as a `Counter`, not a plain `Int` — the mirror image of the `quantity` decision above.** Every write
to `useCount` is "this product was added to a list," an increment, never a "set to N" — exactly the pattern
`Counter` merges correctly.

**Write path — upsert on every item that becomes visible to this device, whether typed locally or received over
the wire.** This is the resolution of former Open Question 3: Pantry isn't populated solely by the device that
happened to run `addItem` — by explicit user decision, *any* item this device learns about (via a local add, or
via an incoming shopping-list sync message from another member) must also be reflected into this device's local
Pantry. Two triggers, both driving the same upsert logic:

1. **Local `addItem`.** Compute the dedup key, look it up in Pantry's `items` map; if present, `tx.increment` its
   `useCount` by 1, set `lastUsedAt` to now, and refresh `title`/categories/`packageSize`/`barcode` to the
   just-added item's values; if absent, create a new `PantryItem` at that key with `useCount` initialized to 1.
   The new item's `pantryItemKey` field (see below) is set to this same dedup key.
2. **Incoming shopping-list sync.** [[ADR-007]]'s existing `onChanged`/`onDocumentChanged` hook (already fired
   after any applied incoming sync message, for search re-indexing) is extended: for every item currently in the
   shopping-list document, if this device hasn't already contributed that specific item to Pantry, run the same
   upsert. "Already contributed" is tracked in a small new **local, per-device** SQLite table (not synced, not
   part of any Automerge document):

   ```sql
   CREATE TABLE PantryContributedItem (
       documentId TEXT NOT NULL,   -- the shopping-list documentId
       itemId     TEXT NOT NULL,   -- ShoppingItem.id
       PRIMARY KEY (documentId, itemId)
   );
   ```

   Without this, re-running the upsert on every `onChanged` (which fires for *any* document change, including an
   unrelated item's `state` toggle or `quantity` edit) would re-increment `useCount` for every item on every
   unrelated edit — wildly overcounting. With it, each item on this device contributes to Pantry exactly once,
   the first time this device ever sees it (locally added, or synced in), regardless of how many times it's
   edited afterward.

**Chosen over the alternative of only incrementing on the originating device.** Since Pantry is itself a synced
document, an increment written by the originating device would eventually reach every other member's device
through Pantry's *own* sync channel anyway, without any other device needing to independently notice the
shopping-list item and re-upsert. That alternative gives an exact one-increment-per-real-add-event count, at the
cost of relying entirely on Pantry's own sync succeeding and needing a second code path (create-if-absent-only,
no increment, for sync-observed items). Rejected in favor of what the user asked for — every device that sees an
item ensures Pantry reflects it, independently — which is simpler (one upsert function, two trigger points) and
more robust (doesn't solely depend on Pantry's own sync channel), at the cost described in Consequences: `useCount`
becomes a per-device-contribution count, not a precise per-event count, once more than one device ever observes
the same add.

**Read path — autocomplete when adding a new item.** `Read.readPantry(): List<PantryItem>`, ordered by `useCount`
descending then `lastUsedAt` descending: "what has this household bought most/most recently."

**Search:** Pantry entries also project into [[ADR-007]]'s `AutomergeSearchIndex` (`feature = "pantry"`, `entityId
= <dedup key>`, `text = "${primaryCategory} ${secondaryCategory} ${title} ${packageSize}"`) — the same mechanism,
a second feature using it, so "do we have ketchup?" is answerable the same way as searching a shopping list.

**Persistence:** fits [[ADR-007]]'s `AutomergeDocument`/`AutomergeSyncState` tables unchanged — one more row per
table, keyed by `"$homeId:pantry"`. No schema change to those two tables; `PantryContributedItem` above is the one
genuinely new table this ADR needs.

### Pantry stock count: a `Counter`, deliberately not wired to any shopping-list trigger yet

`stockCount` answers "how many of this product does the household currently have," making Pantry a real
inventory, not just a usage-history/suggestion source. Modeled as a `Counter` (supports both increment, e.g.
"bought 6 more," and decrement, e.g. "used 2") rather than a plain `Int`, for the same reason `useCount` is a
`Counter`: concurrent adjustments from different members (one restocking, one using some up) should both apply,
not have one clobber the other. An absolute correction (a manual stock-take, "we actually have 3") is implemented
as `increment(current, target - current)` computed from the locally-known value — this is a best-effort operation
under concurrent edits (the same class of "CRDT counters don't support a clean reset" limitation as any
`Counter`-based design), accepted rather than solved here.

**Deliberately not touched by `addItem`, `setItemState`, or the Pantry-upsert write path above.** Adding an item
to a shopping list means "we want more of this," which is not the same signal as "we now have more of this" —
sometimes the opposite (you add something to the list *because* stock ran out). Checking an item off
(`setItemState` → `CHECKED`/`PARTIAL_CHECKED`) is a much more plausible trigger for a stock increase, but isn't
wired up here: see Open Questions for why (the `PARTIAL_CHECKED` quantity-ambiguity problem, and whether
automatic stock adjustment is even wanted vs. a manual-only flow). For now, `stockCount` is a field that exists
and can be read/written directly (`adjustStock`/`correctStock`, see API shape below); nothing in the shopping-list
write path calls those functions automatically.

### Pantry entries can be deleted — same hard-delete primitive as shopping lists

`deletePantryItem(doc, key)` removes the entry from the `items` map, mirroring `deleteShoppingList`'s hard delete.
Two consequences worth being explicit about:

- **Deletion isn't permanent if the product keeps getting used.** Because a `PantryItem`'s map key is derived
  deterministically from the product (barcode or normalized title), adding that same product to a shopping list
  again later — locally or via sync — simply recreates the entry at the same key through the ordinary upsert path
  above. This mirrors how a real pantry works (removing something from a "things we buy" list doesn't stop you
  buying it again) and needs no special-casing in `upsertPantryItem`.
- **A `ShoppingItem.pantryItemKey` can end up dangling** — pointing at a key no `PantryItem` currently occupies,
  if the Pantry entry was deleted after the shopping item was created. Readers resolving `pantryItemKey` must
  treat a missing lookup as "no linked Pantry entry" (null-safe), not an error.
- Automerge's typical behavior for a key concurrently deleted on one device and updated (e.g. `stockCount`
  adjusted) on another is, in general CRDT map semantics, for the update to win (the entry survives) rather than
  the delete silently destroying a concurrent edit — **stated here as the generally-expected behavior, not
  verified against this specific `org.automerge` version**; worth a confirming test before relying on it.

### Shopping-list items carry a back-reference to their Pantry entry

`ShoppingItem.pantryItemKey` (added to the shape above) stores the same dedup key used to upsert Pantry at
creation time — a plain string, set once by `addItem` and **not** re-derived if the item's `title`/`barcode` are
later changed via `updateItem`. This resolves former Open Question 2.

Still not a live cross-document reference — `org.automerge` `ObjectId`s don't cross `Document`s, so this is data
(a string), not a pointer; resolving it into an actual `PantryItem` means having the Pantry document loaded and
doing a `Map` lookup by key, same as any other Pantry read. Kept as a *separate* field rather than always
recomputing `barcode ?: normalizeTitle(title)` from the item's current values, so that later edits to a shopping
item's title don't silently redirect which Pantry product it's considered linked to — the link reflects what the
item was matched against when added, not whatever its title currently says.

`ShoppingItem`'s other fields (`primaryCategory`/`secondaryCategory`/`title`/`packageSize`/`barcode`) stay inline,
duplicated from Pantry rather than read through the reference, for the same reasons the first draft of this ADR
gave: ad hoc items should still work if a `pantryItemKey` lookup ever fails, and a shopping list should be able to
render without also having Pantry loaded. `pantryItemKey` is additive to that design, not a replacement for it.

### Fits directly into ADR-007's generic tables for document persistence/search — one new local table

- `AutomergeDocument`: one row for `"<homeId>:shopping-list"`, one for `"<homeId>:pantry"` — both
  `feature`/`bytes` as ADR-007 already defines, no schema change.
- `AutomergeSearchIndex`, three kinds of row now, all fitting the existing `(documentId, entityId)` key:
  - One row per shopping list (`documentId` = the shopping-list doc, `entityId = list.id`, `text = list.title`).
  - One row per shopping-list item (`entityId = item.id`, `text = "${primaryCategory} ${secondaryCategory}
    ${title} ${packageSize}"`; `barcode` excluded — exact-match key, not free-text, see Open Question 2).
  - One row per Pantry entry (`documentId` = the pantry doc, `entityId` = the dedup key, same
    category/title/packageSize text shape).
  Re-indexing trigger and whole-document re-derivation are exactly ADR-007's existing design for both documents —
  this ADR doesn't change that mechanism, only supplies what text/entityId to feed it.
- `PantryContributedItem` (defined above under Pantry's write path) is the one genuinely new SQL table this ADR
  needs — local-only bookkeeping, not part of ADR-007's document-persistence/search design.

### Read/write API shape, following `TaskListDocument`'s precedent

```kotlin
enum class ItemState { TODO, CHECKED, PARTIAL_CHECKED }

data class ShoppingItem(
    val id: String,
    val primaryCategory: String,
    val secondaryCategory: String,
    val title: String,
    val quantity: Int,
    val packageSize: String,
    val barcode: String?,
    val state: ItemState = ItemState.TODO,
    val pantryItemKey: String,
)

data class ShoppingList(val id: String, val title: String, val createdAt: String, val items: List<ShoppingItem>)

fun Read.readShoppingLists(): List<ShoppingList>          // order: by createdAt
fun Read.readItems(listId: String): List<ShoppingItem>    // order: by primaryCategory, secondaryCategory, title

fun createShoppingList(doc: Document, title: String): String   // returns new listId
// Derives pantryItemKey, upserts pantryDoc, and marks the item PantryContributedItem-contributed.
fun addItem(doc: Document, pantryDoc: Document, listId: String, item: ShoppingItem): String
fun updateItem(doc: Document, listId: String, itemId: String, item: ShoppingItem)  // pantryItemKey untouched
fun setItemState(doc: Document, listId: String, itemId: String, state: ItemState)
fun removeItem(doc: Document, listId: String, itemId: String)
fun deleteShoppingList(doc: Document, listId: String)

data class PantryItem(
    val id: String,
    val primaryCategory: String,
    val secondaryCategory: String,
    val title: String,
    val packageSize: String,
    val barcode: String?,
    val useCount: Int,
    val lastUsedAt: String,
    val stockCount: Int,
)

fun Read.readPantry(): List<PantryItem>   // order: by useCount desc, lastUsedAt desc
fun upsertPantryItem(doc: Document, item: ShoppingItem)   // dedup key = barcode ?: normalizeTitle(item.title)
// Called from the shopping-list document's onChanged hook, for sync-observed items not yet contributed locally.
fun contributeSyncedItemsToPantry(shoppingDoc: Document, pantryDoc: Document, documentId: String)
fun adjustStock(pantryDoc: Document, key: String, delta: Int)       // delta may be negative
fun correctStock(pantryDoc: Document, key: String, targetValue: Int) // best-effort; see "Pantry stock count"
fun deletePantryItem(pantryDoc: Document, key: String)
```

Same shape as `setTitle`/`addTask` in `TaskListDocument.kt`: free functions taking a `Document`, each opening and
committing its own `Transaction`, no shared mutable wrapper object. `setItemState` is split out from `updateItem`
rather than folded in, matching how `TaskListDocument` gave `setTitle` its own function separate from a general
update — the state toggle is the highest-frequency edit on an item (tapped repeatedly while shopping) and doesn't
need to round-trip the item's other fields to make one change. `addItem` takes both documents because it must
write to both — the caller doesn't need a separate call site to remember to make for the Pantry side.

### Fourth amendment: one static list per Home

Requirements (2026-10-05): shopping lists are downstream of the Pantry (the list knows the Pantry; the Pantry knows
nothing of lists); a list is static; adding an item that the Pantry lacks adds it to the Pantry; a Home has one list.

```
ROOT (map)
└─ "items": Map<key, ShoppingItem>     -- key = pantryDedupKey(barcode, title), like the Pantry

ShoppingItem (map)
├─ "id": Str                  -- same value as its key
├─ "primaryCategory": Str
├─ "secondaryCategory": Str   -- key omitted when none
├─ "title": Str
├─ "quantity": Int
├─ "packageSize": Str
└─ "barcode": Str             -- key omitted when none
```

- **No list object, no list id.** "One list per Home" means the document *is* the list; `lists`, `createdAt`,
  `createShoppingList` and `deleteShoppingList` go away. `documentId` stays `"$homeId:shopping-list"`.
- **Items are keyed by the Pantry dedup key**, not a random UUID. This is what makes "the same product added twice"
  one line: adding an item whose key is already on the list *adds the quantities together*. Concurrent first-time
  adds of the same product on two devices land on the same key (they conflict as the Pantry's do, see Consequences).
  The key doubles as the link to the Pantry entry, so `pantryItemKey` is dropped.
- **Editing an item** (any field, any time, no session needed) rewrites it in place; if the title/barcode change moves
  its dedup key, the entry moves to the new key (merging quantities if that key is already on the list), as the
  Pantry's own edit does.
- **Pantry write path.** Adding an item ensures the Pantry has it: absent -> created (`stockCount` 0, `useCount` 1,
  its categories registered); present -> only `useCount`/`lastUsedAt` are bumped and the Pantry's fields are left
  alone. Editing a list item creates the Pantry entry if the new key lacks one, without counting a use. The Pantry
  is written by the caller (the source), so the list's document functions stay pure.
- **Cross-device Pantry population by observing synced list items (`PantryContributedItem`) is dropped.** The Pantry
  is itself synced between devices, so the originating device's write reaches the others; this also removes the
  `useCount` overcounting of Open Question 4.
- **Sync (2026-10-05).** `"$homeId:shopping-list"` syncs like the Pantry ([[ADR-013]]): between a user's paired devices
  and with every device on the Home's roster, over `DeviceDataSyncService`. A Home that arrives from another device
  (registry projection or invite join) is given an *empty* stored list document (`storeEmptyShoppingList`) so it fills
  from the creator's seeded one rather than creating a competing `items` map.
- **Shopping sessions** (phase two) will add `state` back, per session, and store-specific ordering; nothing here
  prevents that.

## Consequences

- **Vesta's second real feature document, and the first to use a `Map`-of-`Map` shape.** `TaskListDocument`'s
  `List`-of-strings precedent doesn't generalize to entities with fields and identity; this ADR's shape is the one
  future features with similar needs (a chore with an assignee and due date, say) should look to, not
  `TaskListDocument`.
- **Vesta's first Home with more than one registered document, for real.** Pantry alongside shopping-list is the
  first actual exercise of [[ADR-006]]'s `MultiDocumentSyncHub` multi-document registration — until now it was a
  design on paper with no concrete second document to prove it against.
- **Sort/group-by-category becomes a read-time concern, always.** There is no "natural" order to fall back on for
  items (unlike a `List`'s CRDT order), so every read path (UI list rendering, search-hit resolution) must apply
  the same category/title sort rather than trusting document order. One missed call site would show items in
  unstable, peer-dependent order.
- **Deleting a list, item, or Pantry entry is a `Map` key removal, not bounded by position.** No index-shifting
  concerns on delete, unlike a `List`. Both shopping-list and Pantry deletion are confirmed hard deletes — no
  archival/soft-delete/undo designed.
- **`barcode` absence is modeled as key-absence, not an empty string or null value written into the document** —
  consistent with `TaskListDocument`'s existing "missing key reads as default" convention, but means "no barcode"
  and "barcode not yet loaded/synced" are indistinguishable from the data alone (not a new problem this ADR
  introduces — same is already true of every optional field in this API style).
- **A concurrent item-state conflict silently picks one side, by explicit acceptance.** Not a defect to fix later
  — the user confirmed Automerge's default register behavior is fine for now (see Decision).
- **A concurrent *first-time* Pantry entry creation can still produce a duplicate.** The deterministic dedup key
  fixes convergence for writes to an *already-agreed* `PantryItem` object, but if two members' devices both add
  "Heinz Ketchup" for the first time before either has synced, each creates its own `PantryItem` object at the
  same map key — Automerge registers this as a conflicting write, one object becomes the visible value, and the
  other's `useCount`/`stockCount`/fields aren't merged into it. Bounded and self-recovering (worst case: a
  duplicate-looking Pantry entry a household member can ignore or delete), not data loss, and only possible in the
  narrow first-ever-mention-of-this-product race window. Not solved here — see Open Question 1.
- **`useCount`/`stockCount` as `Counter`s correctly merge concurrent increments once a `PantryItem` is
  established** — two members separately adjusting the same already-known product's usage or stock both count,
  rather than one overwriting the other the way a plain register would.
- **`useCount` is now a per-device-contribution count, not a precise per-event count.** Because every device that
  ever sees a given item (locally added *or* synced in) contributes its own increment (see "Write path" above,
  the explicitly chosen design over the Pantry-sync-only alternative), a single real "item added to a list" event
  can add more than 1 to `useCount` once more than one member's device has observed it — once per device that
  independently noticed it, not once per event. Fine as a rough popularity signal for ranking autocomplete
  suggestions; not fine if ever needed as an exact "how many times has this been bought" statistic. Flagged, not
  fixed — see Open Question 4.
- **`PantryContributedItem` is local, per-device bookkeeping — not synced, not backed up.** Reinstalling the app
  or losing local data resets a device's "already contributed" tracking, so previously-contributed items can
  increment `useCount` again the next time this device happens to re-observe them (e.g. on the next full document
  sync after a fresh install). Minor, self-correcting toward the "rough signal" framing above, not toward data
  loss.
- **Pantry now models two independent numbers per product that must not be confused:** `useCount` (how often this
  product has been added to a list — a popularity/suggestion-ranking signal) and `stockCount` (how many the
  household currently has on hand — an inventory figure). Nothing in the shopping-list write path keeps them in
  sync with each other; see Open Questions 5 and 6 for whether/how they ever should be.
- **Shopping-list items now duplicate Pantry data but also carry `pantryItemKey`, a link that can go stale or
  dangling** — stale if the item's title is edited after creation (the link still points at the original match,
  not a re-derived one, by design), dangling if the linked Pantry entry is later deleted (readers must treat a
  missing lookup as "unlinked," not an error).
- **Pantry history accumulates forever; nothing prunes it**, beyond the new user-initiated `deletePantryItem`.
  Fine at expected household scale (tens to low hundreds of distinct grocery items over years, not an unbounded
  catalog).

## Open Questions

1. **Concurrent first-time Pantry entry creation.** No reconciliation/merge-UI designed for the same-key object
   creation race described in Consequences. Likely low-frequency; revisit if duplicate Pantry entries turn out to
   be common in practice.
2. **Barcode lookup/dedup for the item itself against the *current shopping list*** (e.g. "scan a barcode, find or
   create the matching item in this list") — a different lookup from Pantry's own barcode-keyed dedup, not
   designed here.
3. **Cross-list item reuse beyond what Pantry's autocomplete and stock tracking give.** No richer per-product
   history (e.g. "bought monthly," typical quantity trends, consumption rate) is modeled — Pantry tracks usage
   count, last-used time, and current stock, nothing more analytical than that.
4. **`useCount` overcounting across devices.** As described in Consequences, every device that observes an item
   contributes independently, so `useCount` isn't an exact per-event count once a household has more than one
   active device. Accept as a rough ranking signal, or move to the "Pantry-sync-only propagation" alternative
   described (and rejected) under "Write path" if precision ever matters.
5. **What should trigger a `stockCount` adjustment?** Currently nothing beyond direct, presumably manual, calls to
   `adjustStock`/`correctStock` — no wiring from `setItemState` (e.g. auto-incrementing stock when an item is
   checked off) or any other shopping-list event. Whether automatic stock adjustment from shopping activity is
   wanted at all, and if so from which event, is a product decision not made here.
6. **If checking off a shopping-list item ever does auto-adjust `stockCount`, how does a `PARTIAL_CHECKED` item
   translate into a delta?** This ADR deliberately didn't add a `checkedQuantity` field (see "Item state" above),
   so there's no tracked number for "how much was actually obtained" — only `quantity` (what was requested) and a
   three-value status. Resolving Open Question 5 in the "yes, auto-adjust" direction would very likely require
   revisiting that decision.
7. **Pantry stock correction under concurrent edits.** `correctStock`'s "set to an absolute value" is implemented
   as a delta computed from a possibly-stale locally-read value — a known limitation of `Counter`-based designs,
   not solved here.

## References

- [[ADR-005]] — one `Document` per Home, the container this document's `documentId` fits inside; Pantry is a
  second `Document` under the same Home, not a change to that scoping.
- [[ADR-006]] — the `"$homeId:$feature"` `documentId` scheme and `MultiDocumentSyncHub`; this ADR is the first
  concrete case of a Home actually registering two documents (`shopping-list` and `pantry`) with it, and the first
  to rely on its `onChanged`/incoming-sync hook for something beyond search re-indexing (Pantry's
  sync-observed-item contribution).
- [[ADR-007]] — the generic `AutomergeDocument`/`AutomergeSyncState`/`AutomergeSearchIndex` tables both of this
  ADR's documents slot into unchanged; this ADR supplies the `entityId`/text values ADR-007 left feature-specific,
  for both features, and adds the one genuinely new table (`PantryContributedItem`) ADR-007's design didn't
  anticipate.
- `shared/src/jvmMain/kotlin/uk/veritaslabs/vesta/automerge/TaskListDocument.kt` (on `poc/automerge-over-x0x`) —
  the one existing precedent for modeling app data directly with `org.automerge`'s `Read`/`Transaction` API; this
  ADR follows its extension-function API shape and "missing key = default" convention, and explicitly departs
  from its `List`-of-strings shape for entities that need identity.
