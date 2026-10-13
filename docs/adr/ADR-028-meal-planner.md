# ADR-028: The Meal Planner — a week of meals per Home

**Status:** Implemented (2026-10-13); UI never run in the real app, only under Compose UI tests

## Context

A household decides what it will eat a few days at a time. The Cookbook (ADR-022) holds the recipes; the Meal Planner
says which of them are for which meal. Every Home member reads and edits the same plan, offline, and it reaches the
Home's other devices like the Pantry and the Cookbook do.

## Decision

A fifth document per Home, `documentId = "<homeId>:mealplan"` (ADR-006's naming), synced and mailboxed exactly like
the cookbook (ADR-013, ADR-020).

```
ROOT
└─ "<yyyy-MM-dd>|<SLOT>|<recipe key>": Timestamp      -- when it was planned; SLOT is Breakfast | Lunch | Dinner
```

- **One flat root key per planned recipe**, not days holding slots holding lists. Two devices planning the same new day
  at once would each create the day's map and one would be hidden on merge (the race ADR-010 and ADR-022 describe);
  with flat keys they add two keys and both survive. A set also makes "plan the same recipe twice" the same key, so a
  slot never lists a recipe twice, and a concurrent double-add merges to one.
- **A slot lists its recipes in the order they were planned**, by the timestamp the key holds (ties broken by recipe key,
  so every device orders them the same).
- **Only a cookbook recipe can be planned.** An entry stores the recipe's key, never a title, and the picker offers
  only the cookbook's recipes that are not already in that slot. There is no free-text dish.
- **A slot holds several recipes** (a main and a pudding).
- **Deleting a recipe from the cookbook does not touch the plan.** The entry stays and shows greyed as "Recipe removed",
  with its cross to take it out. (Cleaning up on delete would need the cookbook to know about the plan, and could not
  reach a device that hadn't synced yet.)
- **Slots are Breakfast, Lunch and Dinner**, stored by enum name. A key this version can't read (a future slot, a
  damaged date) is skipped, not an error.
- **The week is the Calendar's:** it starts on the Calendar's first day of the week setting (Monday, Saturday or
  Sunday; ADR-025), and "today" is the Calendar's (`CalendarDate.todayUtc()`). Previous/next arrows move a week at a
  time with no limit either way; "This week" appears when away from the current one.
- **Layout:** a vertical list of seven day cards on phone and tablet alike. Each day card has the three
  meals stacked vertically, each with a plus that opens the recipe picker (a dialog with a search over title and tags) and the recipes
  planned in it, each with a cross.
- Reached from a **Meal planner tile** on the Home dashboard; every member can edit.

## Acceptance criteria

1. The Home's dashboard opens the meal planner.
2. The planner shows a week, a day at a time, each day with breakfast, lunch and dinner.
3. I can set a recipe for any meal of any day, and see it there.
4. Only recipes from the cookbook can be added.
5. A meal can hold several recipes, and I can take any out.
6. I can move to the next and previous week, and back to this one, and each week keeps its own plan.
7. A recipe deleted from the cookbook shows as removed in the plan.
8. The plan survives an app restart and reaches the Home's other devices.

## Not in this ADR

- **Links to the rest of Vesta.** Standalone for now. Later: add a week's (or a meal's) missing ingredients to the
  shopping list net of Pantry stock; mark a meal cooked and take its ingredients out of the Pantry; servings per planned meal.
- Opening a planned recipe from the plan; copying a day or a week; "what shall we have?" suggestions.
- The time zone: "today" is UTC, as in the Calendar, so around midnight the highlighted day can be a day out.
- Agent (ADR-009) commands for the plan; Assistants grants (ADR-027) do not cover it.

## Not done yet (as of 2026-10-13)

- **Real-app run.** Only Compose UI tests and the fake mesh have exercised it; nobody has looked at the screen on
  desktop or an emulator, so layout and polish are unchecked, and the previews have not been opened in the IDE.
- **Android.** `jvmAndroidMain` shares the code with the desktop build, but Android was not built or run.
