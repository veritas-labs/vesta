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
"guest|<id>": Str                                     -- a person who eats with the Home but isn't a member: just a name
"eaters|<yyyy-MM-dd>|<Slot>|<recipe key>": Bool       -- someone has chosen this planned recipe's eaters
"eat|<yyyy-MM-dd>|<Slot>|<recipe key>|<ref>": Bool     -- <ref> is "m:<member id>" or "p:<guest id>"
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
- **Who is eating (tags).** Each *planned recipe* (a recipe in a slot on a day, not the slot as a whole) has eaters: people
  tagged from the Home's members and from **guests**, people who eat with the Home without being in it, such as children. So
  Tuesday's dinner can be a family stew for everyone and a child-only dish for one. A planned recipe nobody has chosen eaters
  for is eaten by *every Home member*, worked out when shown, so a member who joins later is included and nothing is written
  until someone changes it. The first change writes the `eaters|` marker and a key per ticked person, so "nobody" is a real
  choice and differs from "not chosen". Taking the recipe out of its slot deletes its eaters, so planning it again starts from
  the default. Guests are kept Home-wide in the same document, so one added while editing one recipe is there for every other;
  they are a name, can be renamed, and can be removed, which un-tags them everywhere (their ticks are left in the document and
  ignored). A ref for someone who has left the Home or been removed is ignored too. Flat keys again: two devices choosing the
  eaters of one recipe at once merge to the union of what they ticked, so an untick can be undone by a concurrent seed;
  accepted. The week shows "Everyone", "Nobody" or the names under each recipe; tapping it opens a **Who's eating** screen
  (ticks apply at once; add, rename and remove guests there). A recipe that has left the Cookbook has no eaters line.
- **Slots are Breakfast, Lunch and Dinner**, stored by enum name. A key this version can't read (a future slot, a
  damaged date) is skipped, not an error.
- **The week is the Calendar's:** it starts on the Calendar's first day of the week setting (Monday, Saturday or
  Sunday; ADR-025), and "today" is the Calendar's (`CalendarDate.todayUtc()`). Previous/next arrows move a week at a
  time with no limit either way; "This week" appears when away from the current one.
- **Layout:** a vertical list of seven day cards on phone and tablet alike. Each day card has the three
  meals stacked vertically, each with a plus and the recipes planned in it, each with a cross. The plus opens the **recipe picker, a screen of its
  own** (not a dialog, so it can grow suggestions): the cookbook's recipes not already in that meal, each row showing the
  recipe's photo (if it has one), title, type and time, with a search over title and tags. Picking one plans it and returns
  to the week; back returns without planning. `MealPlannerFlow` holds the move between the two.
- Reached from a **Meal planner tile** on the Home dashboard; every member can edit.

## Acceptance criteria

1. The Home's dashboard opens the meal planner.
2. The planner shows a week, a day at a time, each day with breakfast, lunch and dinner.
3. I can set a recipe for any meal of any day, and see it there.
4. Only recipes from the cookbook can be added, chosen on a screen of their own that I can leave without choosing.
5. A meal can hold several recipes, and I can take any out.
6. I can move to the next and previous week, and back to this one, and each week keeps its own plan.
7. A recipe deleted from the cookbook shows as removed in the plan.
8. The plan survives an app restart and reaches the Home's other devices.
9. Each recipe planned for a meal says who is eating it: every Home member unless I change that.
10. I can untick a member who is not eating a recipe, and add people who are not in the Home (children) and tick them; two
    recipes in the same meal can have different eaters.
11. People I add are kept for the whole Home, available on every recipe, and I can rename or remove them.

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
