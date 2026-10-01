# ADR-011: Structured package-size input

**Status:** Accepted (2026-10-01)

## Context

`packageSize` is free text in the Pantry document ([[ADR-008]]) — `"500ml"`, `"6 pack"`, `"1 bottle"`. Typing it is
fiddly, values drift (`"500 ML"`, `"half litre"`), and an AI agent ([[ADR-009]]) has no way to know what a good value
looks like.

## Decision

- **Storage is unchanged:** the Pantry still holds one canonical string. No schema change, no migration; old items and
  un-updated devices keep working.
- **Grammar:** `<amount><unit> <container>` — an amount plus a unit and/or a container. Metric units attach to the
  amount (`500ml`, `1.5kg`); others are spaced (`2 pint`, `15 oz`); the container follows (`400g tin`, `6 pack`).
- **Built-in catalog** (`uk.veritaslabs.vesta.pantry.PackageSize`): units `g kg ml l oz lb "fl oz" pint gal`;
  containers `tin jar bottle packet box bag carton tub tube pouch sachet tray roll pack`. Aliases and plurals are
  accepted when parsing (`ounce`, `litres`, `can` → `tin`). The lists are fixed in code, not household-editable
  (unlike categories, [[ADR-010]]); stored text is English.
- **UI:** the edit form shows package size as a read-only row that opens `PantryItemEditPackageScreen` (same
  draft-handoff pattern as the categories screen). There, `PackageSizeField` offers amount field + unit chips +
  container chips + a live preview. Existing text is parsed to prefill the chips; text that doesn't parse opens in a
  free-text mode, and the user can switch to free text at any time, so nothing is ever blocked or lost.
- **Loose items** (apples, a bunch of bananas) have no packaging: the screen's "Loose (no packaging)" choice stores a
  **blank** `packageSize`, which agents already produce by omitting `schema:size`. Package size is therefore no longer
  required to save an item (it was before this ADR); the edit form shows blank as "Loose (no packaging)" and the
  Pantry list simply omits the size.
- **Agents:** `schema:size` goes through `PackageSize.normalise` — recognised text is canonicalised, anything else is
  stored trimmed (never rejected; size stays optional). The `Accept` echoes the stored size. The grammar and lists are
  documented in the `vesta` skill (`skills/vesta/references/messages.md`); there is no vocabulary-discovery message.

## Consequences

- Canonical strings make later grouping or unit conversion possible by re-parsing, without storing structure.
- Free-text values that don't parse are not editable via chips until re-entered.
- Adding a unit or container is a code change; the skill docs list must be kept in step.
- "tin" is canonical; a user wanting "can" sees "tin".
