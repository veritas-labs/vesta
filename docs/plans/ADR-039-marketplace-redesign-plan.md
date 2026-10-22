# Plan: Marketplace is "Neighbourhood businesses" with favourites (ADR-039, ADR-026)

Written 2026-10-22. **Status: WP-M1 (favourites logic) done; WP-M2 to M6 planned.**

## Decisions (from the user)

1. **No more hand-made merchants.** All business listings come from Overture Maps (downstream of OpenStreetMap and others). The
   "Local Merchants" list and its add/edit screens go.
2. **The Marketplace has one option, "Neighbourhood businesses"; for now it opens the area flow directly (see Decided).**
3. **"Add a missing business"** is a button that only explains: add it on OpenStreetMap (it reaches Vesta when Overture next releases).
4. **Favourites replace merchants as the thing Pantry and Shopping link to.** The member marks businesses from the full listing; those
   are what "Preferred merchants" on a Pantry item and the shopping list offer. The feature is called **My Shops** (decided).
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
- **Unfavouriting something linked from Pantry/Shopping** asks first ("Used by 3 pantry items").
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

Order: M1, then M2 and M4 in parallel, then M3, M5, M6.

## Decided

- **Name: "My Shops".** Pantry's "Preferred merchants" is reworded to match in WP-M3.
- **No Marketplace screen for now.** Opening the Marketplace from the Home goes straight into the area flow (WP-M2 removes the
  `MarketplaceScreen` destination from navigation; the screen file can stay for when a second option returns). Back from the area flow
  returns to the Home.

## Open

- Directions links (WP-M5) are optional.
