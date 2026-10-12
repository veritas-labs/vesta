# ADR-026: Merchants — a list of sellers per Home

**Status:** Implemented (2026-10-10); UI never run in the real app, only under Compose UI tests

## Context

A Home buys from many kinds of seller, from a market stall to a supermarket. The household wants one shared list of them:
what they are, what they sell, when they are open and where they are. Later work (shopping sessions, agents) will want to
point at a merchant, so each one also carries the ids other systems know it by.

## Decision

A fourth document per Home, `documentId = "<homeId>:merchants"` (ADR-006's naming), synced with the Pantry, shopping list
and cookbook (ADR-013, ADR-020 mailbox included). It follows the cookbook (ADR-022): merchants are keys of the root map,
so a Home that existed before this ADR gets its document the first time a device opens it, with no root map to race over.

```
ROOT
└─ <merchant id (UUID)>: Map
   ├─ "id", "name", "type", "address", "gersId", "agentId", "hoursNote": Str
   ├─ "lat", "lon": Int                       -- millionths of a degree; absent when there is no location
   ├─ "services": List<Str>                   -- a MerchantService name, or a typed tag
   └─ "hours": Map<weekday name, List<Int>>   -- [open1, close1, open2, close2, ...] minutes after midnight
```

- **Per-Home, not per-device or global.** Every device of every member sees the same list, and a merchant is part of the
  Home's data like a recipe.
- **Type is a fixed enum**, stored by name: MarketTrader, IndependentTrader, LocalChain, NationalChain, OnlineRetailer, Other.
  An unknown value (from a newer version) reads as Other.
- **Services are a fixed set plus typed tags**: Groceries, Butcher, Greengrocer, Fishmonger, Bakery, Deli, Cafe, Pharmacy,
  Household, and any free text. Both live in one list of strings; on reading, a string equal (ignoring case) to a
  predefined name is that service and the rest are custom tags. Duplicates ignoring case collapse. Predefined services are
  labelled (so can be translated); typed ones are shown as typed.
- **Opening hours are a weekly schedule**: per weekday zero or more ranges (a lunch break is two ranges), no range
  meaning closed, plus a free-text note ("market days only") for what a weekly grid can't say. Ranges are validated
  (open before close, within the day, 24:00 allowed as a close), sorted and dropped if invalid when saved. There are no
  dates, holidays or time zones: the hours are the merchant's local wall-clock times.
- **Location is coordinates plus words.** Latitude and longitude are stored as integer millionths of a degree (about
  0.1 m), so the document never holds a float; both are set or neither. A free-text address / "where to find it"
  covers what coordinates can't ("stall 14, north aisle"). what3words is not used: it is a proprietary, licensed service and
  can't be converted offline. If wanted later it can be added as one more string field.
- **GERS ID** is the Overture Maps Global Entity Reference System id of the place, stored as typed (trimmed, not
  validated). **x0x agent ID** is the merchant's agent id, also stored as typed: the codebase has no agent-id validator, so
  this slice doesn't invent one.
- **A save rewrites the whole merchant** (last writer wins per field), clearing a coordinate that was removed. There are
  no narrow single-field writes, as there is nothing like a rating to update on its own.
- Merchants hold no blobs, so they are not part of blob transfer (ADR-023).

Screens: the list (search by name, address or service; a chip per type in use), a detail page (overflow menu: Edit, and
Delete after confirming) and a form. The dashboard tile is **Marketplace** (listed alphabetically by its label with the others); it opens the Marketplace screen, whose only option for now is **Local Merchants**, which opens the merchants. Back from the merchants returns to the Marketplace.

## Acceptance criteria

1. The Home's dashboard offers the Marketplace, whose one option, Local Merchants, opens the merchants.
2. I can add a merchant with a name, type, services, opening hours, location, GERS ID and x0x agent ID, and see them all
   on its page.
3. I can't save a location with one coordinate or one out of range, or a time that isn't a time.
4. I can edit a merchant, including clearing its location.
5. I can delete a merchant after confirming.
6. I can filter by type and search by name, address or service.
7. Merchants survive an app restart and reach a paired device.

See `MerchantsAcceptanceTest`.

## Not in this ADR

- Linking a shopping-list item, Pantry entry or recipe to a merchant ("buy at").
- Showing whether a merchant is open now (needs the local time zone, see ADR-025), or special / holiday hours.
- A map, distance, or opening the location in a maps app; picking the location from the device.
- Looking a merchant up by GERS ID, validating GERS and agent ids, or messaging the merchant's agent.
- Agent (ADR-009) commands to list or add merchants.
- Quick ways to fill the hours ("same every weekday"); each day is entered separately.

## Not done yet

- **Real-app run.** The screens have only been exercised by Compose UI tests; nobody has looked at them on desktop or an
  emulator, so layout and polish are unchecked.
- Sync is tested on the fake mesh only, as with the other documents.
