# ADR-039: Grounding a Home to a place (H3 cell, GERS later)

**Status:** Proposed (2026-10-19). Not accepted and not implemented. The H3 **library choice is deliberately left open**
(see "Open decisions"). Supersedes the "where does the location come from" question left open in ADR-024 (weather widget).

## Context

New features need a Home tied to a real-world place. The question that started this was "what is the GERS division of this
Home?", so Vesta can join the Overture/Colo world (inspections, division x0x groups, places). Overture's public data gives
divisions as polygons; the only input Vesta has is a place the user names. Two requirements shape everything:

- **Transparent.** The user must be able to see what is stored, what leaves the device, and who sees it.
- **Private.** Vesta has no server and a local-first stance. A location should be no more precise than the feature needs.

Nothing today stores a location for a Home. Merchants (ADR-026) have coordinates and a free-typed GERS id, and the weather
widget (ADR-024, parked) needs the same input, so one Home location should feed both.

## Spike findings (desktop only; scratch code, not in the repo)

Overture release `2026-08-19.0`, DuckDB on Linux x64.

| Approach | Bytes per lookup | Result |
|---|---|---|
| Overture `division_area` direct, bbox + point-in-polygon | **41-95 MB**, 3-15 s | Exact, but unusable on mobile data |
| Overture bbox candidates only, no polygons | about 4 MB | Ambiguous: 2-4 overlapping local divisions (Hackney vs Tower Hamlets; Brenzett vs Snargate) |
| Fetching polygons by id | 4.4 GB | Never: ids are random UUIDs, so no row-group pruning |
| **Precomputed H3 cell to division index** (Kent + east London, 1,260 polygons) | **42-70 KB**, over HTTP range | 97.0% exact at res 9/10 (0.4 MB), 91.3% at 8/9 (0.14 MB); almost every wrong answer sits next to a cell whose answer differs, so it can be flagged |

H3 maths itself (`com.uber:h3` 4.5.1, desktop): cells match DuckDB's H3 extension; a res-8 cell is about 0.53 km edge /
0.74 km2; the centre of the cell holding a point was 308 m from it; 200k conversions took 75 ms; latitude 95 does **not**
throw (callers must range-check), NaN does.

**Not measured:** any Android run (the emulator crashed on start, so the test APKs were built but never installed), iOS, a
UK-wide or world index (size is extrapolated, not measured), and Overture's behaviour across releases.

## Decision

### 1. A Home's location is an H3 cell, not coordinates

A cell says only that the point is somewhere inside it; it does not say where. Storing the cell (default **resolution 8**,
about 0.5 km edge) is therefore a real coarsening, and replaces the "round to two decimals" idea. Anything needing a
coordinate (weather, distance) uses the cell centre, accurate to about the cell size. The exact point the user typed or
picked is never stored.

### 2. GERS divisions are derived from the cell, as a later opt-in

The cell centre is enough input for the division lookup, so GERS needs no second question to the user. It is a separate,
optional step, "Link your area to Overture divisions", not part of setting a location, and it is not built in this ADR's first
slice. It will be designed when something needs it (Colo interop, merchants), choosing then between the options in
"Open decisions". Because a cell can straddle a division boundary, that step must show the candidates and ask the user to
confirm when the cell's neighbours disagree. It must also record the Overture release it used, since GERS ids can change
between releases.

### 3. The flow

1. **Entry.** Home settings, "Location". Optional; nothing prompts for it and nothing runs in the background.
2. **Town or postcode first.** The user types a place. A geocoder turns it into one point; manual coordinates are the
   second option; device location (an OS permission, no desktop equivalent) is last, and may never be built.
3. **Disclosure before sending the text.** A screen states, before any request: who receives it (the geocoder's host), what
   is sent (the typed text only), what is not (Home name, members, device id, any coordinate), and what cannot be hidden (the
   host sees the device's IP address). Buttons: Continue, Enter coordinates instead, Not now.
4. **Show the cell.** The result is drawn as a hexagon, labelled with its rough size ("about 1 km across"), and the user can
   pick a coarser one. Nothing is saved until they confirm.
5. **Saved.** Only the cell id and the resolution. The geocoder's point is discarded.
6. **Forget.** One button clears the location from the Home and every device.
7. **Audit.** The geocoding request is recorded in the sync activity surface (ADR-033): host, date, text sent.

After step 5 no further request is made; the Home never re-geocodes unless the user edits the location.

### 4. Where it lives

On the Home, synced to the user's paired devices like the Home's name and shape (ADR-013's registry). Whether it is also
shown to other Home members (a Home is a shared household, and a 0.5 km cell is coarse) is an open decision, because the
member-facing copy depends on which document carries the Home's shared fields.

## Open decisions

1. **H3 library.** Untested on Android; the candidates and their known limits:
   - **`com.uber:h3`** (JNI). Verified on desktop. The jar carries Android natives for **arm and arm64 only, no x86_64**, so
     an x86_64 emulator image cannot load it; the loader extracts the `.so` from jar resources at runtime, which d8 drops, so
     the build must package it explicitly (resource or `jniLibs`). No iOS native, and Vesta has an `iosMain` source set.
   - **A small pure-Kotlin implementation** of just point to cell, cell to centre, parent and neighbours. Runs everywhere,
     no native packaging, but is code we own and must verify against the reference library on a large set of points.
   - `io.github.luneo7:h3` is a repackage of Uber's library under another group id, not a different implementation.
2. **Geocoder.** Open-Meteo's geocoding API is the working assumption (shared with the weather widget); not tested here.
3. **GERS step: how.** Options: (a) a precomputed cell-to-division file, bundled with the app (fully offline, updates with app
   releases) or hosted as a static file (the host sees a coarse request); (b) reading Overture directly (about 4 MB for
   candidates, 41-95 MB for the exact answer; desktop only until a Parquet reader is shown to run on Android). A UK-wide
   index size must be measured first.
4. **Region.** UK only or global, for any bundled index.
5. **Visibility to members** (see 4 above).
6. **Default resolution.** 8 is the working default. 9 is closer to the earlier "1 km rounding"; coarser reveals less.

## Acceptance criteria

1. With no location set, no network request is ever made for it, and the Home works as before.
2. Before any request, I am told who receives what I typed, what is not sent, and that my IP address is visible to them; I can
   decline and nothing is sent.
3. After typing a town or postcode I see the area as a cell with its approximate size before anything is saved.
4. What is saved is the cell and resolution only; the point the geocoder returned is not stored anywhere.
5. I can enter coordinates by hand and no request is made.
6. A latitude outside -90..90 or longitude outside -180..180 is rejected before conversion.
7. Forgetting the location removes it from the Home and from my paired devices.
8. The geocoding request appears in the sync activity list with its host and the text sent.
9. The same input always produces the same cell on desktop and Android.

## Consequences

- No Overture dependency, index or hosting is needed for the first slice; the cost moves to choosing and packaging an H3
  implementation.
- Homes lose a human-readable name for the place until the GERS step exists; the UI can only show "an area about 1 km
  across", unless a reverse-geocoded label is added, which would be a second outbound request needing its own disclosure.
- The first outbound request carrying user-typed text to a third party, so it needs a line in the privacy notes.
- Weather (ADR-024) and merchants (ADR-026) can use the Home's cell centre instead of asking again.
- Boundary-straddling is accepted and surfaced, not hidden.
