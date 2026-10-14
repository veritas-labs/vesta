# UI / UX audit, October 2026

**Method:** a read of the Compose code in `shared/src/commonMain/.../ui` (about 75 screens, 14 components, 6 flows, `strings.xml`) plus
cross-screen greps for each kind of element. **The app was not launched.** Running it would touch the live x0x daemon and Home data, and
there is no screenshot tooling in the repo. Everything below is therefore from the code. Items marked *(verify live)* are visual
conclusions I could not confirm. File and line references are as of commit `e6a3ffd` plus the uncommitted `TasksScreen.kt` edit.

## Status of the high-priority items

- **H1 fixed:** Delete Home asks first, from both the dashboard menu and the Edit Home menu.
- **H2 and H3 fixed with undo:** deleting a task, calendar event, pantry item, shopping item (including swipe-to-delete) now shows an "… deleted" snackbar with Undo (`ui/components/Undo.kt`, host in `App.kt`). Undo puts the task or event back with the same id, a pantry item back with its stock, and a shopping item back with how much was picked up (from the list; the edit form restores the item without progress). Only the latest delete can be undone. Recipe, merchant, member, hub and other deletes keep their confirm dialogs. Profile delete is not wired into the app, so it was left alone.

## Summary

The app has a coherent visual language: theme tokens, the art-paper illustrations, one `EmptyState` component, shared
`PrimaryButton` / `SecondaryButton`. The weak points are:

1. **Destructive actions are inconsistent, and the worst ones have no confirmation.**
2. **There are several different top-bar and "add" patterns** for what are the same kind of screen.
3. **Feedback after actions is absent** (no snackbars or undo anywhere).
4. **Accessibility gaps**: undersized touch targets, low-contrast white-on-pastel tiles, and `clickable` rows without roles.
5. **Copy drift**: Home / home capitalisation, Delete / Remove / Leave, and one string reused as everyone's "Back".

## Severity key

- **High**: data loss or a likely user error.
- **Medium**: noticeable inconsistency or an accessibility problem.
- **Low**: polish.

---

## 1. Findings

### High

**H1. Delete Home has no confirmation.**
- The Home dashboard overflow menu "Delete home" calls `onDeleteHome()` directly (`HomeDashboardScreen.kt:281-288`).
- That is wired to `homeRepository.delete(homeId)` (`App.kt:504`, `App.kt:957`).
- The Edit Home overflow does the same (`HomeEditScreen.kt:211-216`).
- It is the most destructive action in the app, and it sits in the same menu as "Leave Home", one row above. Leave *does* confirm.
- **Fix:** an AlertDialog like Leave's, ideally naming the Home and saying what is lost. Consider type-the-name for a Home with members.

**H2. Other deletes with no confirmation or undo:**
- Task delete from the Tasks bottom sheet (`TasksScreen.kt:318-320`) and from the Task edit top bar (`TaskEditScreen.kt:174`).
- Calendar event delete (`CalendarEventEditScreen.kt:201`).
- Pantry item delete (`PantryItemEditScreen.kt:352-355`).
- Profile delete (`ProfilePickerScreen.kt:185-187`) *(verify the wired layer; no dialog in the screen)*.
- Compare the ones that do confirm: recipe, merchant, assistant, member, hub, category, synced device, shopping "delete all".
- Task and event are shared with the Home (the private ones sync to the user's own devices), so an accidental tap propagates.
- **Fix:** confirm, or a Snackbar "Deleted. Undo", which fits low-stakes items better. Shopping swipe-to-delete (`ShoppingListScreen.kt`) is the one place where undo is most needed and none exists.

**H3. No transient feedback anywhere.** `Snackbar` is not used in the codebase. Saves, "Added to Pantry", ticked tasks and sync results give no
confirmation. `AddedToPantryScreen` exists as a full screen for one case, so the pattern is known, but other actions are silent.
Failed actions show ad-hoc error text in some screens (22 files use `colorScheme.error`) and nothing in others.

### Medium

**M1. Top-bar variants.** Similar screens use different title treatments.

| Pattern | Screens |
|---|---|
| Single-line title, left-aligned | Settings, Merchants, Cookbook, Tasks, Marketplace, Assistants, Licenses, Hub manager, Network *, Pantry categories, Shopping filter |
| Home name + icon, `headlineSmall` | Dashboard |
| Two-line: item title `titleLarge` + tiny `labelSmall` caption | Recipe detail, Merchant detail, Meal-planner picker / copy / eaters |
| Two-line: title + `bodySmall` subtitle **below** | Meal-planner required items, Pantry package size, Member detail |
| Centred title (month / year), the only `CenterAlignedTopAppBar` | Calendar |
| Brand-coloured glow shadow on the bar | Shopping list (`ShoppingListScreen.kt:234-236`) only; **not** the Pantry, Tasks, Cookbook, etc. |

Two-line bars put the caption above in some places and below in others. Calendar's centred bar is the odd one out.
The Shopping shadow is deliberate (`ShoppingAccent` is shared with the dashboard tile), but no other area has one, so it reads as a bug *(verify live)*.

**M2. Missing or hidden back navigation.**
- `MembersScreen` has no navigation icon (`MembersScreen.kt:123-129`). That is fine because it is a bottom-nav tab, but it is a different shell from the other dashboards.
- Shopping list does have a back arrow (`ShoppingListScreen.kt:281`); my first grep window missed it.
- Edit screens use an X (close) icon. Detail/list screens use an arrow. That is correct Material practice, but `HomeEditScreen` uses an arrow (`home_edit_back`) while Recipe / Merchant / Task / Event edit screens use a close X. `AssistantEditScreen` and `PantryItemEditScreen` also use an arrow. **Inconsistent for the same kind of form screen.**

**M3. "Add" affordances differ.**
- A FAB: Cookbook, Merchants, Pantry, Shopping, Assistants, Pantry categories, Synced devices, Calendar. (Recipe detail's FAB is "start cooking", a different job, so it shares the visual style of the add FABs.)
- A FAB in one view only: Tasks List (not Week, which has a plus per day).
- A top-bar action: none. Meal Planner uses per-slot buttons.
- Pantry hides its FAB in selection mode (good), and Shopping does not need to.
- FAB bottom clearance is hand-set (88dp in Cookbook, Categories). Check that every list uses the same bottom padding or content hides behind the FAB.
- **Fix:** document the rule (list → FAB, calendar-like views → per-cell plus) and make clearance a shared constant.

**M4. Save / primary-action placement and sizing.**
- Every edit form puts a `PrimaryButton` at the end of a scrolling column. The top padding differs: 24dp (Home, Pantry), 8dp (Event, Task), none (Merchant, Recipe). Event and Task buttons are `fillMaxWidth`, Home and Pantry are not (the button itself caps at `PrimaryButtonMaxWidth`).
- Long forms (Recipe, Merchant, Event) push Save below the fold, and there is no sticky bar. `PantryItemEditPackageScreen` pins its Done button, so it does the right thing and the others don't.
- No form warns on discarding unsaved changes (the X closes immediately) *(verify live)*.
- Save labels: "Save", "Create home", "Done" (package size), "Add" (dialogs). Fine, but see M7.

**M5. Bottom sheets vs full screens vs dialogs for the same job.**
- Pick-an-action sheet: Tasks (`skipPartiallyExpanded = true`), Meal planner, Shopping.
- The profile picker's sheet uses the default `rememberModalBottomSheetState()` (`ProfilePickerScreen.kt:~150`), so it opens half-height, unlike the others. That was explicitly fixed for the other three in `ecb838e` and this one was missed.
- Pickers: date/time in a popup (`DatePickerPopup`, `TimePickerPopup`) on Tasks and Calendar, a dialog on Calendar's month pick, a full screen on meal planner (Picker, Copy, Eaters).
- Member selection is a Checkbox dialog on Task and Event, a full screen on Meal planner eaters.

**M6. Touch targets below 48dp.**
- Meal planner remove button: `IconButton` forced to `size(32.dp)` with a 16dp icon (`MealPlannerScreen.kt:590-595`).
- Clear-date / clear-time X in Task edit: icon 18dp inside a default IconButton (target OK), but the same X as a chip trailing icon is 16dp and likely not independently tappable (`TaskEditScreen.kt:314`, `CalendarEventEditScreen.kt:356`).
- Calendar colour-pick swatch is 16dp inside a chip (`CalendarEventEditScreen.kt:532`).
- **Fix:** keep the visual size, add `minimumInteractiveComponentSize()` or padding.

**M7. Copy and terminology drift.**
- **Home vs home:** "Create a home", "Edit home", "Delete home", "Leave Home". Tour copy uses "Home". Pick one (the app treats Home as a proper noun, so capitalise).
- **Delete vs Remove vs Leave:** "Delete merchant / recipe / task / home", "Remove assistant / category / member / hub / device / eater / recipe from a day". Meal-planner "Remove" and "Delete recipe" are different operations with the same noun.
- **Create vs Add:** "Create home", "Create profile", "Create an invitation" vs "Add recipe / merchant / task / category".
- **Done is overloaded:** a button label, a task state ("Done recently", "Done by…"), an assistant activity status.
- **OK vs Done** for dismiss-style dialog buttons (`event_edit_pick_ok`, `task_edit_pick_ok`, `members_leave_blocked_ok` vs `*_done`).
- **Duplicated "add" titles:** "Add pantry item" is both the FAB description and the screen title, same for recipe, merchant, shopping, task (fine), but there is one string per place, so edits will drift.
- **Back:** 17 separate `*_back` strings, all "Back". Several screens borrow `pantry_back` (Cookbook, Merchants, Tasks, Meal planner, Recipe/Merchant detail, Shopping filter, Barcode), so a translator or rewording would change Pantry's string and these others silently. Use one shared `common_back`. Same for Cancel (15 strings).
- **Hardcoded string:** `Text("Camera preview")` in `BarcodeScannerScreen.kt:149` (desktop placeholder, not localisable).

**M8. Colour.**
- White icons on pastel dashboard tile circles (`HomeDashboardScreen.kt:360`, tint `Color.White`). Sage `#8FA07A`, khaki `#A89A6E`, gold `#C9973A` and teal `#6FA5A0` give roughly 2.3-3.0:1 for white, short of the 3:1 non-text minimum in some cases *(verify live, calculation approximate)*.
- Meal planner tile terracotta `#D9743A` and cookbook tile coral `#D9694F` are almost indistinguishable, and terracotta is also the meal planner's Monday colour (`MealPlannerScreen.kt:123`).
- Status greens (`NetworkPeersScreen.kt:96`, `NetworkDiagnosticsScreen.kt:84`) are the same literal `#4CAF50` defined twice, not themed, and not tuned for dark mode. Colour is the only state cue unless a text label sits beside it *(verify live)*.
- Event colours are 8 fixed hues, with no accessible name beyond index for the picker chips *(verify live)*.

**M9. Accessibility semantics.**
- 14 screens use bare `.clickable(...)` on rows or tiles, and most have no `role = Role.Button` (only a few `semantics` hits). Screen readers announce them as plain text.
- 25 files use `contentDescription = null` on icons. Most are decorative beside text and fine, but check: `ProfileAvatar`, `BlobImage`, `RadioOptionCard` (8), `ActionCard` (4), `HomeNavigationBar` (3), `TasksScreen` (3), `MealPlannerScreen` (3).
- The calendar day cells, task tick circles and day cards need state descriptions ("done", "overdue") *(verify live)*.

### Low

**L1. Safe-area handling varies.** Some screens use `safeDrawingPadding()` on the root (Tasks, Shopping), others use `windowInsetsPadding(safeDrawing.only(Horizontal + Top))` (Members, Calendar). The two differ at the bottom edge and with the keyboard. Test every form with the keyboard up on a phone *(verify live)*.

**L2. Content-width caps are a mix of constants.** 720dp is redeclared as a private val in at least five files (MealPlanner, Tasks, MerchantDetail/Edit, RecipeEdit, MealPlannerRequired as a literal) while others use 300-560dp. Hub manager's empty state escapes its cap (last commit `e6a3ffd`). Move to one `ContentMaxWidth` token.

**L3. Content padding is recomputed per screen** (`if (isTablet) 24.dp else 16.dp`, 25+ copies). Some use a boolean, some `maxWidth >= XTabletBreakpoint` with one breakpoint constant per screen. Candidate for one `LocalContentPadding` / `WindowSizeClass`.

**L4. Empty states.**
- Most use the shared `EmptyState` with art.
- These use plain text instead: member agents (`MemberDetailScreen.kt:181`), licences, network peers, diagnostics log, meal planner required items, assistant activity, pantry category.
- Fine for sub-lists, but "Network peers" and "Required items" are full-screen empties.
- Search no-results re-uses `NoResults` art in Cookbook/Merchants (good) but not in Pantry or Shopping search *(verify live)*.

**L5. Row containers.**
- Lists use different containers: `OutlinedCard` (Recipe, Merchant, Meal picker), `Surface` (Members, Hub, Tasks), custom `*ListItem` (Pantry, Shopping, Profile, Home), `Card` (Day cards).
- Corner radii, borders and padding are therefore per-screen.
- Two "list of people" renderings exist, `MemberListItem` in Members/Tasks/Event, and similar rows on the Meal planner eaters screen.
- Consider a shared `ListRow` once the number of variants is down.

**L6. Inputs.**
- Only 4 files set `KeyboardOptions` (Pantry, Merchant, Recipe, Task). Home name, Profile name, Event, Shopping, Category, Redeem and Paste dialogs rely on defaults, so Enter doesn't advance or submit and capitalisation is unset *(verify live)*.
- Date entry is a button that opens a picker (Task, Event). Merchant hours are typed `HH:MM` text with a validation message. Pick one pattern: time picker.
- Inline errors: Home name and Merchant fields show messages. Recipe and Event mostly disable Save with no hint why.

**L7. Loading states.** Only 4 screens show progress (Invite links, Hub, Hub manager, Pairing progress). Lists that depend on Automerge/DB load (Pantry, Cookbook, Tasks) appear to show the empty state until data arrives, which can flash "nothing here" *(verify live)*.

**L8. Dashboard and tiles.**
- Seven tiles are in a two-column grid, so the last row is a single tile.
- Tile order is Pantry, Shopping, Cookbook, Meal planner, Calendar, Tasks, Marketplace. Consider grouping food (Cookbook, Meal planner, Pantry, Shopping) vs. household (Calendar, Tasks).
- Tasks shows overdue counts and Calendar / Shopping / Pantry show nothing, so the badge idea is applied to one tile only.
- The tab bar (Home, Members) exists only on the dashboard and Members; entering any area drops it. Fine, but the Members tab is the only other peer of Home, so the nav bar has two items, which is thin for a bottom bar. Consider a Members action in the top bar plus the overflow instead.

**L9. Overflow menus mix routine and destructive items.** Dashboard: Manage hubs / Edit home / Leave / Delete home (only Delete is red). Edit screens: the only entry is Delete behind a three-dot menu (Pantry, Home), where Task and Event show a trash icon directly. A one-item overflow menu is two taps for no benefit.

**L10. Calendar / Meal planner / Tasks week views** have three independent day-card implementations (`DayCard` in Tasks and Meal planner, month grid in Calendar). Visuals should line up (they appear to have been copied deliberately), but they are separate code.

---

## 2. Cross-screen consistency tables

### Edit / form screens

| Screen | Close icon | Save location | Delete | Confirm delete | Unsaved warning |
|---|---|---|---|---|---|
| Home | arrow | button, end of form | overflow | **no** | no |
| Pantry item | arrow | button, end | overflow | **no** | no |
| Recipe | X | button, end | detail screen overflow | yes | no |
| Merchant | X | button, end | detail screen overflow | yes | no |
| Task | X | button, end | trash icon in bar | **no** | no |
| Event | X | button, end | trash icon in bar | **no** | no |
| Assistant | arrow | (own pattern) | remove button | yes | no |
| Package size | arrow | button **pinned** | n/a | n/a | n/a |

### Confirm-dialog wording

| Dialog | Title | Confirm | Dismiss |
|---|---|---|---|
| Recipe / Merchant | "Delete this X?" | "Delete" | "Cancel" |
| Member | "Remove %1$s?" | "Remove" | "Cancel" |
| Leave Home | "Leave this Home?" | "Leave" | "Cancel" |
| Shopping all | "Delete all items?" | "Delete all" | "Cancel" |
| Hub | "Remove hub %s?" | "Remove" | "Cancel" |

Wording and structure are consistent here, which is good. The gap is the actions that skip the dialog entirely (H1, H2). Confirm buttons are plain `TextButton`s, some in the error colour (Hub, Member, Home leave) and some not (Recipe, Merchant), so check that all destructive confirms are red.

### Add / create entry points

| Area | Entry | Notes |
|---|---|---|
| Cookbook, Merchants, Assistants, Pantry, Shopping, Categories, Synced devices | FAB | |
| Calendar | FAB | |
| Tasks | FAB in List view; per-day plus in Week | |
| Meal planner | per-slot button | |
| Recipe detail | FAB = start cooking | not an add action, but looks identical to the add FABs |

### Empty states

Shared `EmptyState` (art + title + body + optional actions): Cookbook, Merchants, Pantry, Shopping, Categories, Assistants, Tasks, Hub manager, Meal picker. Plain text: licences, peers, diagnostics log, required items, member agents, assistant activity.

---

## 3. What is working well

- One theme, light/dark art variants (`isDarkArt`), and the wide-screen empty-state layout.
- Test tags and previews everywhere (light/dark × phone/tablet), which makes the fixes above cheap to verify.
- `HomeNavigationBar` badge has a proper content description.
- Back/cancel icons all carry content descriptions.
- Confirm-dialog structure and wording are uniform where used.
- The Tasks FAB comment shows the right instinct, only showing an add control where it is not redundant.

## 4. Suggested order of work

1. H1 and H2: confirm or undo for Home, Task, Event, Pantry item and Profile deletes. This is small and the most valuable.
2. H3: add a `SnackbarHost` at the app shell with an `Undo` action, and use it for deletes (which replaces some confirms).
3. M6, M8, M9: touch targets, tile contrast and roles, which are mechanical.
4. M7: one set of common strings (`common_back`, `common_cancel`, Home capitalisation, a Delete/Remove rule: *Delete* = destroys data, *Remove* = detaches, *Leave* = you go).
5. M1 to M5: document and enforce the top-bar, FAB and form patterns, ideally as small shared components (`VestaTopBar(title, subtitle?, …)`, `FormScaffold`).
6. L-items as the screens are next touched.

## 5. Not covered

- Live behaviour: focus order, animations, keyboard, scroll performance, actual rendering and screenshots.
- Android-specific permission flows (camera, notifications) and deep-link handling.
- Onboarding tour art content and the Hub tutorial text beyond skimming.
- Real contrast ratios (estimates above only).
- Localisation: there is a single `values/` locale.
