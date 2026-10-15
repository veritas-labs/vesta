# ADR-010: Pantry categories in the Pantry document

**Status:** Accepted (2026-09-30); default set replaced 2026-10-13 (see Amendment); migration corrected 2026-10-15 (see Amendment 2)

## Context

Pantry items carry a `primaryCategory` and optional `secondaryCategory` as free text (ADR-008). The app should ship a
predefined category set, and a household must be able to add and delete any category, with those changes syncing
like everything else in the Home's Pantry document.

## Decision

The Pantry document gets a second root map:

```
ROOT
├─ "items": Map ...
└─ "categories": Map
   └─ <categoryKey(name)>: Map          -- key = trimmed, lowercased name (same idea as pantryDedupKey)
      ├─ "name": Str                    -- display name, e.g. "Condiments"
      └─ "secondary": Map
         └─ <categoryKey(name)>: Str    -- display name, e.g. "Ketchup"
```

- **Keyed maps, not lists.** Concurrent adds of the same name collapse into one entry; add/delete are key put/delete;
  concurrent edits to different secondaries of one primary merge. A list would duplicate and conflict.
- **Defaults are seeded into the document** by `newPantryDocument()` (`DefaultPantryCategories`), not layered over
  it from code. Deleting a default is then an ordinary delete — no tombstones, and a newer app's defaults can never
  resurrect a category the household removed.
- **Documents created before this ADR** have no `categories` map; they read as the defaults, and the first category
  write materialises them. Two devices doing that concurrently would race on the root key (the loser's map is
  hidden) — accepted, since it only affects pre-ADR documents and the window is the first category edit.
- **Items still reference categories by name**, unchanged. Deleting a category never edits items; they keep showing
  under their category text on the Pantry. Saving an item registers any category it uses that isn't registered yet.

## Consequences

- No migration of items. Renaming a category is not supported (delete + add); it would need an item rewrite.
- Deleting a primary concurrently with another device adding a secondary under it loses the secondary (Automerge
  drops writes into a deleted object).
- `PantryContents.emptyCategories` is still empty; registered-but-unused categories appear only on
  `PantryCategoriesScreen`, not the Pantry.

## Amendment (2026-10-13): new default set, reset on open

`DefaultPantryCategories` was replaced with a supermarket-aisle-oriented set of 21 primaries (Fruit & Vegetables,
Bakery, Meat & Fish, Deli & Chilled Ready, Dairy & Eggs, Frozen, Pasta, Rice & Grains, Tins & Jars, Cooking
Ingredients, Condiments & Spreads, Herbs & Spices, Baking, Breakfast, Snacks, Drinks, World Foods, Household,
Health & Beauty, Baby, Pets, Other / Uncategorised) and their secondaries. Changes from the first set: Meat and Fish
merged; Dairy became Dairy & Eggs; Cooking Sauces merged into Cooking Ingredients; Condiments split into Condiments &
Spreads; cereal and oats moved to Breakfast; toiletries moved to Health & Beauty.

Existing Pantries were reset to the new set, destructively (decided by the user; custom categories dropped). Superseded by Amendment 2, which keeps custom categories:

- The document root gets a `categoriesVersion` integer, written whenever the defaults are seeded (currently `2`).
- `migratePantryCategories` runs in `openPantryDocumentSource`. If a document has a `categories` map but a missing
  or older `categoriesVersion`, it re-seeds the map from the defaults. Categories added afterwards survive, because
  the version now matches.
- A document with no `categories` map (an empty one waiting for its creator's content) is left alone, so it takes
  the creator's registry on sync instead of racing it.
- Items are not rewritten: an item tagged with a category that is no longer registered (e.g. "Meat") keeps its text.
- ~~Two devices migrating before they sync each write an identical registry; Automerge keeps one, nothing is lost.~~ **Wrong** — see Amendment 2.
- Changing the defaults again means bumping `CategoriesVersion` in `PantryCategories.kt`.

## Amendment 2 (2026-10-15): the migration converges and keeps custom categories

**What was wrong.** The sentence struck above is false. `seedCategories` did `set(ROOT, "categories", MAP)`, and every
device that opened an old document ran it. Two devices migrating before they synced each created a *different* map
object under the same key; Automerge keeps one as the winner and the other subtree becomes invisible, so a category
added on the losing side was lost. It also wrote on every device on load, and was destructive by design.
`PantryCategoriesMigrationTest.aCustomCategoryAddedBeforeBothDevicesMigrateSurvivesTheMerge` failed against that code.

**The fix** (ADR-034 WP3). `migratePantryCategories` now edits the existing `categories` map in place:

- It never replaces the root map. For each current default it writes the entry at its deterministic key
  (`categoryKey(name)`) only if it is missing, so concurrent runs write the same keys with the same values and merge.
- It deletes only version 1 defaults (`LegacyDefaultPantryCategoriesV1`) that the current set dropped or renamed, and
  a dropped primary only when nothing is left under it; a custom secondary under a dropped primary keeps it alive.
- It writes `categoriesVersion` last, only when the stored value is older, and a second run writes nothing.
- A document with no `categories` map is still left alone, and `newPantryDocument` still seeds with `seedCategories`
  (the creator's one write, on a key that does not exist yet).

**Semantics change.** The destructive reset is no longer applied: **custom categories are preserved**. Documents that
already ran the destructive v2 reset keep their result (it is not undone). The 2026-10-13 decision that "custom
categories are dropped" is reversed because it could not be made safe across devices and nothing else justified losing
household data. Current defaults the household had deleted from a version 1 document come back once (the version
number cannot tell a deletion from a never-seeded entry); items are still not rewritten.

**Residual risk.** A migration that creates a default primary absent on both devices still races the same key if
someone concurrently adds into that same-named primary; one of the two objects wins. Deleting a dropped default primary
while another device concurrently adds a secondary under it loses that secondary (Automerge drops writes into a deleted
object). Both need a category edit in the same sync window as the first open after the update.
