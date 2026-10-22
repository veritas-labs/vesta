# Implementation plan: browse businesses like a directory (ADR-039)

Written 2026-10-22 after the first by-hand look at the business list ([places plan](ADR-039-places-plan.md), WP-P5). The list works but
reads as a flat feed: 13 broad group chips, rows ordered by a distance that is no longer shown, nothing to browse by trade. The goal
is a "Yellow Pages": the member looks up a trade, then reads an A to Z list of businesses grouped by locality. **Status: WP-Y1 (logic) and WP-Y2 (UI) done; WP-Y3 not started.**

## What changed already (committed or in the working tree)

- Rows no longer show a distance (Vesta does not know where the member is). A row is the name, then `Category, Locality`
  (e.g. "Flooring Contractor, Orlestone"). For a business outside every polygon the locality is the **nearest** one; the row does not say
  "in" or "near", so the ADR's rule (never claim a place is inside a locality it is not) is met by showing only the name.

## Decisions taken in this plan

1. **Browse by trade first.** After the download the member lands on a **Categories** screen, not the list. Two levels: the 13 groups
   (e.g. Food and drink) as large rows with counts, then the specific categories inside a group (Hair Salon, Flooring Contractor) with
   counts. The `unknown` group is shown last as "Other" and its categories are not listed (they have none).
2. **Counts come from the downloaded data**, not the directory (the directory knows places per locality, not per category). That is
   fine: the area is fetched in full before this screen, so the counts are exact for what the member chose.
3. **A to Z everywhere.** Businesses are sorted by name (folded, so "Café" sorts with "Cafe"), never by distance. Distance stays only as
   the order of the locality sections.
4. **Sections by locality.** A list is cut into sections headed by locality name: the home locality first, then the ticked neighbours
   nearest first (the order `AreaPlan.rows` already has). A business under a locality includes both its inside and "just outside"
   places; the "just outside" toggle still decides only what is downloaded. Paging (50 per "Show more") counts businesses, not headers.
5. **Search is global.** The search box on the Categories screen searches business names across the whole area and opens the list
   with that query; inside a category it narrows that category. A search never changes the sort (still A to Z within localities).
6. **Not in this plan:** business detail pages and contact options, the confidence filter, desktop master-detail layout, category
   icons. They are separate decisions (suggestions 4 to 7 in the conversation).

## Work packages

Each for one sub-agent in its own worktree and commit, in order.

### WP-Y1. Logic (`commonMain`, `...location/NearbyBusinesses.kt`)
- `categoriesWithCounts(places, categories, area, groupKey)` returning `CategoryCount(index, name, count)` ordered by count desc then name;
  `groupsWithCounts` already exists for level one.
- `list(...)` gains `categoryFilter: Int?` (a category index) next to `groupFilter`, and a new result type:
  `BusinessSection(localityRow, localityName, items)`; `BusinessPage` carries `sections` (items still available flat for tests).
  Sort inside a section: folded name, then name, then id. Section order: `area.rows` order. Dedupe unchanged.
- Remove the distance from `BusinessRow` only if no caller needs it (the sort no longer does); otherwise leave it, unused by the UI.
- Tests (Arrange/Act/Assert comments): category counts and ordering, filter by category, A to Z with accents, section order,
  inside and nearby mixed in one section, paging cuts across sections, a place with no category, empty area. Extend the real-data
  test: report the number of categories and the biggest group for a real area (Orlestone and Queen's Park).

### WP-Y2. UI (`ui/screens`, `ui/flows`)
- New steps after Loading: `Categories` (groups, then a group's categories) and `Businesses` (the list). Replace the group chip row.
- Categories screen: search field on top, "All businesses" row, then the group rows with counts; tapping a group shows its categories.
  Back goes up one level, then to the Saved card (dropping results, as the list does now).
- List screen: header with the category name and count, section headers by locality, rows (name, `Category, Locality`), Show more,
  empty and partial-failure states as today. The category line is shown even inside a category (it carries the locality).
- Strings under `area_places_*`; previews follow the project rule (light/dark x phone/tablet grouped by device).
- View model: state for the browse level, selected group and category, query; keep the fetch and cancel logic unchanged.
- Tests: view model (navigation up and down, search from the top, search inside a category, counts update with the toggle,
  forget clears) and Compose flow tests for both new screens.

### WP-Y3. Acceptance tests and docs
Given/When/Then in `docs/testing/acceptance-tests.md` for: land on categories after the download; group then category narrows the
list; A to Z within localities and home locality first; search across all and within a category; back steps up one level at a time;
no distance on any row. `MarketplaceBusinessesAcceptanceTest` updated. ADR-039 "As built" gets a short browse subsection and the plan
status.

## Risks

- **Category quality.** Overture categories are uneven, and 5.5% of GB places have none. They sit under "Other" and are reachable by
  search and "All businesses".
- **Long category lists.** A big area can have hundreds of categories; the group level keeps each screen short. If a group has more
  than about 60 categories, the screen needs its own search field (decide on real data in WP-Y2).
- **"Nearest locality" is not "in".** A business listed under Shadoxhurst may be 3 km outside it. The member can read that as being
  in the village; the plain name is the user's choice. Revisit if it misleads in the by-hand run.
