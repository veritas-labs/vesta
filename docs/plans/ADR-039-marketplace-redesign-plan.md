# Plan: Marketplace is "Neighbourhood businesses" with favourites (ADR-039, ADR-026)

Written 2026-10-22. **Status: all work packages (WP-M1 to WP-M7) done; UI never run in the real app.**

## Decisions (from the user)

1. **No more hand-made merchants.** All business listings come from Overture Maps (downstream of OpenStreetMap and others). The
   "Local Merchants" list and its add/edit screens go.
2. **The Marketplace has one option, "Neighbourhood businesses"; for now it opens the area flow directly (see Decided).**
3. **"Add a missing business"** is a button that only explains: add it on OpenStreetMap (it reaches Vesta when Overture next releases).
4. **Favourites replace merchants.** The member marks businesses from the full listing; the feature is called **My Shops** (decided).
   *Amended 2026-10-22 (WP-M7):* Pantry and Shopping no longer link to My Shops; they were first built to offer them as "Preferred merchants".
5. **Visual guide:** the Stitch screen (area card with Change, search, category tiles) and its listing cards, used as a guide only, in
   Vesta's own fonts and theme.

## Design

- **A favourite is a merchant record.** The merchants document (ADR-026) already holds `name`, `gersId`, `lat/lon`, `services`, `type`
  and `address`. Favouriting a business writes one, filled from the place: `gersId` = the place id, name, coordinates, `services` =
  its category, `address` = its locality name, `type` = IndependentTrader. Unfavouriting removes it. **No document change, so no
  migration, and Pantry/Shopping links by merchant key keep working.** Merchants made by hand before this stay and show in My Shops,
  removable but not editable.
- **One record per business.** Favouriting twice finds the existing record by `gersId`; the heart state is "a merchant with this
  gersId exists".
- **My Shops list:** a row at the top of the Categories screen ("My Shops, 6") opening a list of them, A to Z, each with its heart.
  It works offline (the records are in the Home document, not the places pack).
- **Unfavouriting** is immediate. (It first asked "Used by 3 pantry items" when Pantry/Shopping linked merchants; removed in WP-M7.)
- **Not in the pack, so not shown:** photos, ratings, opening hours, phone, badges, "km away". The Stitch listing card is cut down to
  name, `Category, Locality`, a heart, and Directions (a `geo:` / maps link built from the pack's own coordinates; WP-M5, optional).

## Work packages

- **WP-M1 Favourites logic.** `FavouriteBusinesses`: place to merchant mapping, favourite/unfavourite by `gersId`, usage count for the
  confirm, My Shops list. Unit tests against the in-memory merchants source.
- **WP-M2 Marketplace entry and flow.** The Marketplace opens the area flow directly (no screen with options); remove `Merchants` destination,
  `MerchantsFlow` route, `MerchantEditScreen`, `MerchantDetailScreen` (My Shops rows replace it) and the Local Merchants strings.
- **WP-M3 Heart on business rows and the My Shops list** in the area flow; Pantry/Shopping pickers offer My Shops with a line pointing
  to Neighbourhood businesses when empty.
- **WP-M4 Add a missing business.** Footer on the Categories screen and on the end of a list / empty search: short explanation,
  "Open OpenStreetMap" opens the site, no data leaves the device.
- **WP-M5 Visual refresh to the Stitch guide:** area summary card with Change, search bar, 2-column category tiles, card rows.
  Previews as light/dark by phone/tablet.
- **WP-M6 Tests and docs:** acceptance criteria (Given/When/Then) for each of the above; ADR-026 amended ("hand entry removed,
  favourites"), ADR-039 and the acceptance-test doc updated.

- **WP-M7 Drop the Pantry and Shopping links (done 2026-10-22).** The user decided nothing in Pantry or the shopping list links to
  merchants. Removed: the Pantry item form's *Preferred merchants* section, the merchant picker and batch update (and the Pantry's
  long-press selection that existed only for it), the merchants line on Pantry and shopping rows, the shopping list's *Filter by
  shop* chips, `MerchantUsage`, `FavouriteBusinesses.usageCount`/`usageOfKey`, the "used by N items" dialog and its view-model state
  (`pendingRemoval`, `confirmRemoval`, `cancelRemoval`), their strings, tags and tests. Unfavouriting and removing from My Shops are
  immediate. **Kept dormant:** the `merchantKeys` field in the Pantry document (read as `PantryItem.merchantKeys`, written by
  `setPantryItemMerchants`, carried across a rename, never touched by a saved edit) so older devices round-trip (ADR-034); My Shops,
  the merchants document and its sync stay. This amends decision 4, the unfavourite question below and the "Pantry/Shopping pickers" part of WP-M3; ADR-026 amended.

Order: M1, then M2 and M4 in parallel, then M3, M5, M6.

## Decided

- **Name: "My Shops".** (Pantry's "Preferred merchants" was reworded to match in WP-M3, then removed in WP-M7.)
- **No Marketplace screen for now.** Opening the Marketplace from the Home goes straight into the area flow (WP-M2 removes the
  `MarketplaceScreen` destination from navigation; the screen file can stay for when a second option returns). Back from the area flow
  returns to the Home.

## Open

- Directions links (WP-M5) are optional.

## By-hand run (desktop app, owed)

1. Open a Home and tap Marketplace: the area flow opens with no options screen in between; back returns to the Home.
2. Pick an area and download businesses: the area card shows the area and total; Change reopens the area choice.
3. Check the tiles: My Shops (once one exists), All businesses, groups, Other last.
4. Heart a business, open My Shops, unheart it: it goes at once, with no question.
5. Tap Directions on a row: the browser opens OpenStreetMap on the place.
6. Search for something absent: the missing-business note shows; Open OpenStreetMap opens the site.
7. In Pantry and Shopping there is no shops section, picker, shops line or "Filter by shop" (WP-M7).
