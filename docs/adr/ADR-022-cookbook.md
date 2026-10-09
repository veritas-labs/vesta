# ADR-022: The Cookbook — a recipe document per Home

**Status:** Implemented (2026-10-08); UI never run in the real app, only under Compose UI tests

## Context

Every Home should have a cookbook of recipes that the whole household can read and edit, that works offline, and that
knows what is in the Home's Pantry (ADR-008) so a cook can see at a glance what they still have to buy.

## Decision

A third document per Home, `documentId = "<homeId>:cookbook"` (ADR-006's naming), synced with the Pantry and the
shopping list (ADR-013, ADR-020 mailbox included).

```
ROOT
└─ <recipe id (UUID)>: Map
   ├─ "id", "title", "description", "type", "source", "notes": Str
   ├─ "servings", "prepMinutes", "cookMinutes", "restMinutes", "rating": Int      -- rating 0 = unrated, 1..5
   ├─ "favourite": Bool
   ├─ "tags": List<Str>
   ├─ "media": List<Map{ "uri", "kind" (PHOTO|VIDEO), "caption" }>
   ├─ "ingredients": List<Map{ "name", "milli": Int?, "unit", "note" }>               -- quantity in thousandths
   └─ "steps": List<Map{ "id", "text", "minutes": Int?, "after": List<Str> }>         -- after = ids of steps first
```

- **Recipes are keys of the document's root map**, with no `recipes` wrapper. Every Home that exists before this ADR
  gets a cookbook the first time a device opens it; two paired devices doing that concurrently must not each seed a
  root map (ADR-010's "documents created before" race, where the loser's map is hidden on merge). With no map to seed,
  both just start from an empty document and their recipes merge.
- **Recipes are keyed by a random id**, not by title: two recipes may share a title, and renaming one must not move it.
- **A save rewrites the whole recipe**, like a shopping-list item edit (last writer wins per field). Rating and
  favourite have their own narrow writes so rating a recipe never clobbers a concurrent edit of its text.
- **Quantities are stored as integer thousandths** (1500 = 1½), so the document never needs floating point.
- **Steps form a dependency graph.** Each step lists the ids of the steps that must be finished first. Display order
  is a topological layering: stage 1 is every step with no prerequisites, stage *n* is every step whose prerequisites
  are all in earlier stages. Steps in one stage can be done at the same time. A prerequisite id that no longer exists
  is ignored; a cycle (only possible from a bad merge, the editor can't make one) puts the steps it traps in a final
  stage so nothing is ever hidden.
- **Total time is derived** (prep + cook + rest), never stored, so it can't disagree with its parts.
- **Pantry awareness is computed at display time**, not stored. An ingredient matches a Pantry entry when their names
  are equal ignoring case, whitespace and plurals (each word is singularised: "cherry tomatoes" = "Cherry Tomato",
  "berries" = "berry"). It shows as *In pantry*, *Not enough*, *Out of stock* (entry exists, stock zero) or *Not in
  pantry*. *Not enough* compares the quantity the recipe needs at the servings on screen with what the matching
  entries hold (stock count × package size, summed), within mass, volume or plain counts, converting units (g/kg/oz/lb,
  ml/l/tsp/tbsp/cup/pint). Where the amounts can't be compared (no package size, a "pinch", mismatched kinds) the
  ingredient counts as *In pantry*. Nothing is copied between documents.
- **Servings scale the display**: the detail screen can show the recipe for another number of servings; ingredient
  quantities are multiplied, the stored recipe is untouched.
- **Media are references**, not bytes. A photo picked from the device is a blob reference (hash, size, w, h and a placeholder blur,
  [[ADR-023]]) whose bytes are stored on each device and moved between them by file transfer; a video, or an entry from before
  ADR-023, is a link (`uri` + kind + caption) shown as a labelled tile. The Automerge document never holds the bytes.

Recipe types: Breakfast, Lunch, Dinner, Starter, Side, Dessert, Baking, Snack, Drink, Other.

## Acceptance criteria

1. I can add a recipe with a title, description, type, servings, prep/cook/rest times and rating, and find it in the
   cookbook afterwards.
2. The recipe page shows prep, cook, rest and total time.
3. Each ingredient shows whether the Pantry has it, is out of it, or doesn't know it.
4. The method lists steps in stages; steps that can happen at the same time are grouped in one stage and shown side by side
   in a row that scrolls sideways.
5. I can rate a recipe 1–5 stars from its page, and the rating shows in the cookbook list.
6. I can show the recipe for a different number of servings and the ingredient quantities scale.
7. I can filter the cookbook by type, by favourites, and search by title, ingredient or tag.
8. I can attach photos and videos to a recipe and see them on its page.
9. I can edit and delete a recipe.
10. A recipe added on one device appears on a paired device, and recipes survive an app restart.

## Not in this ADR

- Picking media from the device and transferring its bytes between devices.
- Adding a recipe's missing ingredients to the shopping list in one tap.
- Importing recipes from a web page; agent (ADR-009) commands for recipes.
- Cooking mode (step check-off while cooking) and meal planning.

## Not done yet (as of 2026-10-08)

- **Real-app run.** The screens have only been exercised by Compose UI tests; nobody has looked at them on desktop or
  an emulator, so layout and polish are unchecked.
- **Media pipeline.** Photos are done ([[ADR-023]]): a picker, a placeholder, byte transfer between devices. Not done: video capture and playback,
  and the real-app run of the photo flow.
- **Missing ingredients → shopping list** in one tap (the Pantry matching already knows which are missing).
- **Agent commands (ADR-009)** to add or find recipes.
- **Web import** of recipes from a page.
- **Cooking mode** (tick off steps) and **meal planning**.
