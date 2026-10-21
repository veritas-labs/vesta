# Implementation plan: places directory and nearby business list (ADR-039)

Written 2026-10-21 as the follow-on to [the area-picker plan](ADR-039-marketplace-location-plan.md) (its WP8b). The picker is built and
was walked by hand on desktop against the devnet (country, home locality, neighbours all worked on real data). Its last screen says
"Nearby merchants will appear here once business data is available." This plan builds the data and the list behind that sentence.
**Status: planned, nothing built.**

**Scope of this plan: the devnet only.** The data is uploaded to the local devnet, never to mainnet. The PII legal review
(ADR-039 decision 5) and the Overture `places` licence check therefore do **not** block it, but they **do** block any mainnet
upload and any shipped default address. The builder refuses a non-devnet network (WP-P1) so that is a code fact, not a promise.

## Goal

After choosing an area, the member sees the businesses in it: a count and download size before anything is fetched, then a list
sorted nearest first, with a name search and a category filter. Rows are Overture businesses (name, category, distance), not
registered merchants (ADR-026); claiming a listing or adding one to the household's own merchants list is out of scope here.

## What exists (verified 2026-10-21)

- `~/Development/scratch/gers-index/gb_places.parquet` (155 MB): GB places with only `id, pname, cat, confidence, st, lon, lat`.
  No phone, email, website, social or address columns, so the record is already PII-free by construction. `cat` is
  `taxonomy.primary` (1,797 values, flat); the hierarchy is **not** in the file.
- Locality names table (`VGN2`, 22,093 rows, spatially ordered by H3 res 6) and the cell index; both on the devnet and decoded by the
  Kotlin `LocalityPack`.
- Spike figures (ADR-039, third spike): 3,235,716 GB places, about 29.3 B per place, 95 MB zlib; 256 KiB chunks give about 2 chunks
  for a median unit and cost about 508 chunks per release. **Not measured since the locality-only change.**
- 49% of GB places lie inside no locality polygon (ADR-039 locality-only spike); decision below.

## Decisions taken in this plan (fold back into ADR-039 open decision 7 when done)

1. **A place belongs to its smallest containing locality** (ADR-039 decision 6), so nested localities never double-count.
2. **Unassigned places (49%) go to the nearest polygon locality, within a cap.** **Cap 5 km, measured by WP-P0**
   (85.7% of places placed, median 20 and p99 2,494 per locality; see ADR-039 "Places measurements"); beyond the cap the place is left out of v1 and counted in the builder's report. Reason: the area picker only
   offers localities, so a place with no locality can never be reached; nearest-with-cap keeps villages without a polygon useful
   without attaching remote farms to a town. The list labels these rows by distance only, never "in <locality>".
3. **Unit of fetch = a locality's places, split into spatial sub-units of at most 256 KiB** (ADR-039 chunk-size choice). A locality
   is a list of (chunk, offset, length) ranges, so one chunk holds several small localities and a big city spans several chunks.
4. **Packing follows names-table row order** (already spatial), so neighbouring localities usually share chunks and a home plus
   neighbours costs few fetches.
5. **Separate places root `VGP1`; the locality root `VGI2` is unchanged.** The places root names the locality release it was built
   against, the directory chunk, the category table and the data chunks. A different release is a different pack, same
   "needs choosing again" handling as the picker. Installed picker builds keep working.
6. **Categories: a table, plus a top-level group per place.** Per-place category is a varint into a category table (display name
   and group). Groups come from `taxonomy.hierarchy[0]` (about 10 to 20 values) so the filter is usable; the 1,797 primaries are
   not shown as a flat list. WP-P1 must re-extract `taxonomy.hierarchy` from Overture (the parquet has only `primary`).
7. **Fetch is always preceded by a size confirmation** when the area's total exceeds 1 MiB (count and size come from the directory,
   no data chunk is read for them). Below that it fetches straight away on opening the list. This also supplies the "merchant
   counts and download size" the ADR lists.
8. **Names and positions are published as they are in Overture** (devnet only). Contact fields are never extracted; the builder
   asserts the output schema has no such column and a test pins it.
9. **Disclosure copy changes before the list ships.** Fetching places chunks tells the serving node roughly which areas the member
   chose, which the picker's disclosure does not say. The disclosure and the unconfigured/unavailable states get a places paragraph
   (WP-P5). This is a product statement, so it needs the user's wording sign-off.

## Chunk formats (to be fixed by WP-P1 and recorded in the builder's docstrings, like `VGN2`)

- **Places data chunk** `VGD1`: zlib of a sequence of unit blobs. A unit = place count, then columns: ids (16 B each), lat/lon as
  zigzag-delta microdegrees in cell order, category varint, confidence byte, NUL-terminated names. Each unit is decodable on its own
  at a known (offset, length) inside the decompressed chunk, so compression is per chunk, not per unit. (WP-P0 measures whether
  per-unit zlib is worth the extra size.)
- **Directory chunk** `VGP2`: per names-table row, varint range count then (chunk index, offset, length, place count) tuples; chunk
  index points into the root's chunk list. Rows with no places cost one byte.
- **Category table chunk**: count, then per category the display name, group index; groups table first.
- **Places root** `VGP1`: magic, locality release, country, directory address, category address, chunk count, chunk addresses.
  Estimated about 500 chunk addresses x 32 B = 16 KB, so the root may itself need to be a list chunk; WP-P1 decides after P0.

## Work packages

Each is for one sub-agent in its own worktree, with its own commit, in this order. P0 gates P1; P2 and P3 can then run in
parallel; P4 needs P2; P5 needs P3 and P4.

### WP-P0. Measure (no repo code; results into the ADR)
On the scratch parquet with DuckDB, answer: (a) of 3.2M places, how many sit in a locality polygon and how many within 1, 2, 5, 10
and 25 km of one (pick the cap in decision 2); (b) the unit-size distribution per locality and per cap choice; (c) per-chunk vs
per-unit compression size; (d) chunk count and total size at 256 KiB; (e) how many places have a `taxonomy.hierarchy` and the
top-level group names; (f) the largest locality and how many chunks it spans. Output: a short table in the ADR's "As built" and the
chosen cap. **No upload.**

### WP-P1. Builder: `places` and `place-units` in `scripts/gers-index/gers_index.py`
New subcommands: `places-extract` (Overture S3 to a parquet with the allowed columns only, plus hierarchy), `places` (assign to
locality per decisions 1-2, encode `VGD1`/`VGP2`/categories, upload chunks and root, write `out/places_published.json`). Refuses to
run unless the devnet manifest is in use (no mainnet network flag, no real wallet). `verify-places` reads a random sample of
localities back over the network and checks every place against the source parquet (id, name, position within 1 microdegree,
category). Documents the formats in docstrings; README gets a "Places" section and a new "Last run" table. Tests: a small
fixture round-trip in a Python test, and the schema-has-no-contact-column assertion.

### WP-P2. Decoder and directory (`commonMain`, package `...location`)
`PlacesRoot`, `PlacesDirectory` (counts and byte sizes per locality row, chunk ranges for a set of rows), `PlaceUnit` decoder,
`CategoryTable`. Pure code over `ByteReader`/`Inflater`, no network. Tests with a `TinyPlaces` fixture builder mirroring `TinyPack`,
plus a `RealPlacesPackTest` (jvmTest, skipped unless `VESTA_PLACES_FIXTURE_DIR`) that decodes the real pack and checks counts
against `places_published.json`.

### WP-P3. Source and cache (`commonMain` interface, `jvmMain` implementation)
`PlacesPackSource` mirroring `LocalityPackSource`: root, directory and category table loaded and cached like the locality pack;
`fetchUnits(rows)` reads only the needed chunks (concurrency limited, nearest first), caches each after it decodes, reports bytes
and chunks fetched, and is cancellable. Reuses `DiskChunkCache` (separate directory `~/.vesta/places-cache`) and the existing
`ChunkVerifier` hook. Config via `VESTA_PLACES_ROOT` (and `vesta.placesRoot`) next to the locality settings; unconfigured
means the existing empty state. Tests with a fake client: partial failure, cancel mid-fetch, cached second fetch makes no calls,
nothing cached when a chunk fails to decode.

### WP-P4. Business list logic (`commonMain`)
`NearbyBusinesses`: given the saved area (home plus ticked localities), the directory and fetched units, produce counts, size
estimate, and a filtered, searched, distance-sorted list; folds names with the existing `SearchFolding`; category group filter;
stable paging (first 50, then more). Distance from the home locality's centroid, not from any device position. Pure function, unit
tested like `NearbyPlaces`.

### WP-P5. UI (`ui/screens`, `ui/flows`)
Extend the area flow after Saved: **Businesses** step. Parts: summary with counts and size (decision 7), a Download confirmation
when over 1 MiB, a progress state per chunk with Cancel, then the list (name, category, distance), search field, category group
chips, Show more, and honest states (no data for this area, partly loaded, unreachable with Try again, places not configured).
The Saved card's empty-state sentence is replaced by a "See businesses" action when places are configured. New strings under
`area_places_*`; disclosure paragraph from decision 9 after sign-off. Previews: the standing light/dark x phone/tablet matrix
grouped by device. View model tests (loading, size gate, cancel, retry, empty, search, filter) and flow Compose tests.

### WP-P6. Acceptance tests and docs
Given/When/Then in `docs/testing/acceptance-tests.md` for: size shown before any data chunk is fetched, declining the download
fetches nothing, list sorted nearest first, search and filter, cancel keeps what was already cached, forgetting the area also hides
the list, offline after one fetch. `MarketplaceBusinessesAcceptanceTest`. ADR-039 "As built" updated and open decision 7 closed.

### WP-P7. Later (not in this plan)
Sync-activity entry for places fetches (needs the picker's WP8 first); merchant counts inside the *picker's* neighbour list (the
directory already holds them, so this is small once P2 lands); other countries (needs the top-down builder); mainnet upload (legal
review, licence check, real wallet, cost sign-off); Android/iOS (embedded client has no chunk-by-address path yet); BLAKE3
verification of data chunks (the places pack makes the missing check matter more, since it is about 400 chunks).

## Verification

- Builder: `verify-places` passes on the devnet; counts in `places_published.json` reconcile with the parquet (placed + capped-out +
  dropped = total).
- Unit: P2, P3, P4 suites as listed; schema pin test; the existing location suites still pass.
- By hand (user, desktop): same two-antd setup as the picker (README), then Marketplace, Nearby merchants, See businesses, with the
  user's real home locality; check the size shown, the list order, search, a category filter, and that a second open works with
  the second antd stopped.

## Risks and open points

- **Devnet size and time.** About 95 MB over roughly 400 to 500 chunks; the picker's upload was 1.6 MB, so this is the first large
  upload. The devnet loses it on reboot. P1 must record the upload time and republish steps.
- **Large localities.** A city locality can have tens of thousands of places across several chunks; the size gate and
  nearest-first fetch order are the mitigation. P0 (f) tells us how bad.
- **Category quality.** Overture's primary category is uneven; a "group" filter may show odd members. Acceptable for a first list;
  note findings in the ADR.
- **Release drift.** Places root records the locality release it was built against; a mismatch with the saved area's release shows
  the existing "needs choosing again" state rather than wrong rows.
- **Names can identify sole traders.** Not a devnet blocker; it is the open legal question for mainnet.
- **Duplicates and closed businesses.** Overture confidence is carried but unused in v1; low-confidence rows are not hidden until
  we see real data. Decide after the by-hand run.
