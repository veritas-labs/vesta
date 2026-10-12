# ADR-024: Weather widget on the Home dashboard

**Status:** Proposed, **parked** (2026-10-09). Not accepted and not implemented. Written down so the thinking isn't lost;
nothing here is a commitment. Relates to the dashboard design in `docs/design/dashboard.md`.

## Context

The idea: a small tile on the Home dashboard showing the current temperature and a one-word condition (sunny, cloudy,
rain...). Nothing more: no forecast, no alerts.

## Estimate

Easy to moderate. About a day with a per-Home place setting; a couple of hours with a hardcoded location. The tile itself is
small; choosing where the location comes from is the real decision.

## What is already there

- `HomeDashboardScreen` is a card layout, so a weather tile sits beside the existing `ActionCard`s.
- Recipe import (`recipes/RecipePageParser.kt`, `RecipeImport.kt`) already downloads web pages, so there is an HTTP path to reuse.

## Proposed approach

1. **Source: Open-Meteo.** Free, no API key, simple JSON. One call returns the temperature and a WMO weather code:
   `api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code`. The code maps to a
   condition and icon through a small lookup table.
2. **`WeatherRepository`.** Fetch, parse, cache for about 30 minutes so the dashboard doesn't hit the network on every
   recomposition. Fail quietly: offline means hide the tile or show the last value.
3. **The card.** Icon, temperature and label, with the usual light/dark x phone/tablet preview matrix, unit tests and a
   Given/When/Then acceptance test.
4. **Units.** °C or °F from the locale or a setting.

## Where the location comes from (the open decision)

| Option | Verdict |
|---|---|
| **A place set on the Home** (town or postcode, geocoded once via Open-Meteo's geocoding API) | Preferred. Same on desktop and Android, no permissions, and a Home is a shared household so one place suits everyone. Could sync in the Home document so every device shows the same weather. |
| **Device location** | More work (Android permission handling, a separate desktop story) and the wrong fit: the weather belongs to the house, not to whoever holds the phone. |
| **Fixed lat/long** | Quickest; only good as a stopgap. |

## Consequences / things to settle if unparked

- First feature that sends an approximate location to a third party. It needs a line in the docs and privacy notes, given
  the local-first stance.
- A place-editing UI in Home settings (about half the work).
- Whether the place lives in the Home Automerge document (synced) or per device.
