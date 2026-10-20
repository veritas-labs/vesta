# ADR-039: Grounding a Home to a place (country and division, optional H3 cell)

**Status:** Proposed (2026-10-19). Not accepted and not implemented. The H3 **library choice is deliberately left open**
(see "Open decisions"). Supersedes the "where does the location come from" question left open in ADR-024 (weather widget).

## Context

New features need a Home tied to a real-world place. The question that started this was "what is the GERS division of this
Home?", so Vesta can join the Overture/Colo world (inspections, division x0x groups, places). Overture's public data gives
divisions as polygons; the only input Vesta has is a place the user names, and the user may not be at home when they set the Home up. Two requirements shape everything:

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
world index, and Overture's behaviour across releases. (A UK-wide index was measured in the second spike, below.)

### Second spike: a UK-wide index stored on Autonomi (2026-10-19, desktop + local devnet; scratch code, not in the repo)

Overture release `2026-09-23.1`, the 24,171 GB `division_area` rows with `class = land`, DuckDB 1.5.6 with its H3
extension. For each res-9 cell the most specific division containing the cell centre was kept (microhood, neighborhood,
macrohood, locality, county, region, country, in that order, smaller area first), and consecutive cells with the same
division were merged into runs.

| Measure | Result |
|---|---|
| Cells / runs | 2.65M cells, 318k runs |
| Whole UK, one file | **0.69 MB** (zlib; varint deltas over base-7 H3 ordinals, 16-byte UUID table per shard) |
| Sharded by res-2 H3 prefix | 13 shards, largest 184 KB, plus a 491 B root chunk listing shard addresses |
| Exact vs point-in-polygon (25.7k random UK land points) | **97.1%**; 0.2% have no cell |
| Wrong answers with a differing cell in the ring around them | about 93% (730 of 788), so they can be flagged |
| Same lookup read back through `ant chunk get` (400 points) | 97.8% exact; 99.8% identical to the in-memory table (the rest are absent cells inside a run, which return a division) |
| Network calls per lookup, devnet | cold: 3 (pointer resolve, root, one shard), 84 KB, about 1 s; warm: 1 |

The 97.1% matches the first spike's 97.0%, on a far larger and more varied area.

**What the index stores.** Only the cell to **GERS division id** mapping, one id per cell: the most specific division, with
no ancestors, names or subtype. The id is `division_area.division_id`, which is the `id` of the row in Overture's `division`
type and is the GERS id of the division. It is **not** `division_area.id`, the id of the area row. All 16,272 ids in the
index were found in the `division` table. Water is excluded (`class = land`), so a point at sea returns no cell.

**Names and hierarchy are a separate table**, built from the `division` type: the 16,272 indexed divisions plus their
ancestors (16,451 in all, none outside GB). Columns: 16-byte id, subtype (7 values), parent as an index into the same table,
primary name. It is **0.37 MB** zlib (0.50 MB raw): ids 0.26 MB (incompressible), names 0.08 MB, parents 0.02 MB, subtypes
1.4 KB. Together with the 0.69 MB of shards that is **about 1.06 MB** for ids, names and ancestry. The shards and the table
both hold the 16 ids; having shards refer to table positions instead would save about 0.26 MB of that (**estimated, not
built**), for about 0.8 MB. Only each division's primary name is kept, no translations.

**A silent failure to avoid.** DuckDB's `h3_polygon_wkt_to_cells` returns **zero cells for every MULTIPOLYGON** with no
error. The first UK run dropped all 235 of them (the country, every region, Highland, most counties) and looked
plausible; the unresolved points clustered in Scotland and gave it away. Split with `ST_Dump` first. The first spike's
1,260 Kent and east London polygons were not re-checked for this.

**Autonomi facts verified** (`ant` 0.3.11, a local 25-node devnet with an Anvil EVM chain, not Arbitrum Sepolia):

- `ant chunk put/get` store a raw chunk of **1 byte to exactly 4 MiB (4,194,304 bytes)**; one byte more is rejected. A raw
  chunk needs no data map and has no 3-chunk minimum (unlike `ant file`).
- A chunk's address is the **BLAKE3 hash of its content**, so a copy fetched from any mirror can be verified by the client.
- Pointers (`ant pointer create/update/get/resolve/finality`) are owner-signed and mutable; `update` raised the counter and
  changed the target. A fresh pointer reports `open (its owner can move it)` until transferred.
- Mainnet storage quotes (`ant file cost`) are per chunk, about 0.05 ANT, almost independent of size (a 2.9 KB and a 28.6 MB
  file cost 0.149 and 0.465 ANT for 3 and 11 chunks), so a few larger shards are cheaper than many small ones. Not tested
  with a funded mainnet wallet; devnet prices are not mainnet prices.
- **Android.** The published `com.autonomi:ant-android:0.0.8-rc.1` AAR exposes `Client.chunkGet`, `chunkPut` and
  `chunkExists`, and `connectDefault` needs no wallet, so reading a chunk is possible in process. It has **no pointer
  API**; the pointer operations exist only in the `ant` CLI and the `antd` daemon. The AAR is 38 MB with 27-40 MB native
  libraries per ABI (arm64-v8a, armeabi-v7a, x86_64), the same cost that led to its removal from the app
  (`docs/plans/android-app-size-reduction.md`, WP4). **Nothing was run on Android**: only the exposed API was inspected. Pointer support is expected in a later release (the
maintainers' plan is unconfirmed); that would close the API gap but not the library size.

**Not measured in the second spike:** mainnet latency and cost, an Android run, a world index, names in other languages,
shards that refer to table positions (estimated only), and whether the index could be cut below res 9 (the first spike
measured 91.3% at res 8/9).

### Third spike: other countries, businesses, and the user flow (2026-10-20, desktop; scratch code, not in the repo)

Same Overture release (`2026-09-23.1`). Numbers marked *estimate* were not built or encoded.

**Divisions in other countries** (res 9 unless noted; shard encoding as above):

| Country | Land areas | Divisions in index | Runs | Size |
|---|---|---|---|---|
| GB | 24,171 | 16,272 | 318k | 0.69 MB |
| DE | 40,394 | 30,249 | 832k | 1.59 MB (more neighbourhood-level detail than GB) |
| AU, res 7 only | 16,668 | 13,831 | 190k | about 0.48 MB *estimate* (1.35 B per run + 16 B per division) |

The run-based estimate was calibrated on GB, where it reproduced 318,253 runs against 318,254 and 0.69 MB. FR and AU at
res 9 did not finish. Size follows boundary detail, not land area. Overture has **1,078,697 land areas in 272 countries**;
extrapolating from GB and DE gives roughly **60-100 MB for the world, 0.1-3 MB per country** (*estimate*, two data
points, Latin-script names assumed). That argues for **one pack per country**, fetched only for the user's country.

**The builder must work top-down.** Filling every res-9 cell works for GB (2.65M cells) but not for a large country:
Australia is tens of millions of cells and the attempt reached 22 GB of memory before it was stopped; the FR run died too.
Fill at a coarse resolution and subdivide only the cells a boundary crosses, so cost follows the output size, not the area.

**Businesses (Overture `places`).** 81,455,423 places worldwide, 3,235,716 in GB (by the first address's country).
The GB records were encoded as: 16-byte id, position as deltas in cell order, category, confidence, primary name; sorted by
res-9 cell, each column compressed with zlib.

| Column | MB (GB, zlib) |
|---|---|
| Ids (16 B each, incompressible) | 51.8 |
| Names | 26.6 |
| Positions | 9.7 |
| Category (1,797 distinct) | 4.4 |
| Confidence | 2.6 |
| **Total** | **94.9, or 29.3 B per place** |

The world would be about 2.4 GB (*estimate*). Ids are over half of it, so a business index cannot be bundled with the app.
In this release `categories` has become `basic_category` and `taxonomy`; the index used `taxonomy.primary`. Only 0.17% of
GB places fall outside every division cell; a ring-3 fallback leaves 6.

**Buildings are out of scope** (decision 2026-10-20). There are 2.53 billion worldwide and about 29.6M in GB; ids alone
would be 16 bytes each, about 40 GB for the world and over 9,000 chunks per release. Addresses (474M rows) are not
pursued either.

**The user flow, sized.** A user in the UK picks a home division from their position, then browses its businesses.

1. The device converts the position to a res-9 cell (microseconds, no network).
2. Candidate divisions: pointer, root chunk and the one res-2 shard for the cell (3 calls cold, 84 KB measured on the
   devnet), then the leaf division and its parents. Divisions found in the cells around the point are offered as
   neighbours, flagged when the cell sits near a boundary. Names come from the 0.37 MB table, fetched once and cached.
3. The user picks a division. Only its GERS id and the Overture release are stored.
4. Businesses: each place is assigned to its leaf division, and divisions are cut top-down so each unit holds at most
   25,000 places (2,785 units in GB: median 35 places, p90 about 5,000, largest about 45,000, or 1.3 MB). Units are packed
   into chunks in spatial order, and a small places directory (estimated at about 40 KB; **not built**) maps a division to
   its chunk. The device fetches that chunk; neighbours usually share it. The 45 GB divisions above the cut (counties and
   larger) are shown as too large to browse and need search or category filters instead.

**Chunk size is the main tuning choice**, because Autonomi returns whole chunks. Simulated with 600 random place positions
standing in for users (this over-represents towns), at 29.4 B per place:

| Chunk target | Chunks, GB places | Median fetch, home division | p90 | Median, home plus adjacent (about 1 km) | Cost per release (*estimate*, 0.05 ANT per chunk) |
|---|---|---|---|---|---|
| 64 KiB | 1,721 | 248 KB (about 5 chunks) | 639 KB | 317 KB | about 86 ANT |
| **256 KiB** | **508** | **258 KB (about 2 chunks)** | **639 KB** | **366 KB** | **about 25 ANT** |
| 1 MiB | 111 | 920 KB | 1,039 KB | 986 KB | about 6 ANT |
| 4 MiB | 24 | 4,107 KB | 4,187 KB | 4,123 KB | about 1 ANT |

Going below 256 KiB does not help, because the median user's own division is already about 250 KB. 4 MiB downloads about
sixteen times what is needed. **256-512 KiB is the working choice.**

Totals for one GB release: about 96 MB of storage (0.69 MB of shards, 0.37 MB of names, 95 MB of places), about 524 chunks
at 256 KiB. A first-time user transfers about 0.7 MB (root and shard about 10 KB, names 0.37 MB, directory about 40 KB,
places chunk about 260 KB); later lookups cost one chunk per new division, and about 1 MB is cached on the device. Client
compute is expected to be milliseconds (**not measured on a phone**). The node serving a places chunk can see which chunk
was requested, and so roughly which division the user chose; packing several divisions into one chunk blurs this a little.

**Not measured in the third spike:** FR and AU at res 9, any world figure, the places directory and the real chunk format,
mainnet cost, decoding on a phone, and a name or category search index for businesses.

### Neighbours without the cell index (2026-10-20, desktop; scratch code, not in the repo)

The names table holds only containment (id, subtype, parent, name), so it cannot say which divisions are *next to* each
other; siblings (same parent) are not neighbours. A precomputed adjacency list added to the table would. Measured on all
24,171 GB land areas: two divisions are neighbours when their polygons of the **same subtype** touch or overlap.

- 73,827 edges; mean 6.1 neighbours, median 6, p99 11; 1,117 divisions (islands) have none.
- **147 KB zlib in total, 6.1 B per division**, when the table is ordered spatially (by the H3 cell of each division's
  centroid) and each list is varint deltas of table indices. In UUID order the same lists are 306 KB (12.6 B per division),
  because neighbours land far apart in the table. The names table should therefore be spatially ordered.
- 11 divisions have over 100 "neighbours" (maximum 3,041). They are Northern Ireland districts tagged `locality` that
  *contain* ordinary localities, so containment is being counted. The builder must use touches only (or ignore pairs whose
  overlap area is non-trivial); the true figure is somewhat lower.
- The table has 16,451 divisions, not 24,171, so its list is smaller still; 0.15 MB is an upper bound for GB.

Not measured: DE or any other country, neighbours across subtypes (a neighbourhood next to a locality), and near-but-not-
touching neighbours (a tolerance of about 100 m). The polygon join took about 6 minutes on this machine for GB.

**Locality only (decision 6).** Re-measured for GB with localities that have a polygon:

- Overture holds **50,102 localities in GB but only 21,931 have an area**. The other 28,171 are point-only: no polygon, so
  they cannot be assigned from a cell, have no neighbours, and cannot own businesses by position. They are left out. (Including
  them would make the names table 1.1 MB, of which 0.8 MB is incompressible ids.)
- **Names table: 22,093 rows** (21,931 localities, 157 counties, 4 regions, 1 country), spatially ordered: ids 353 KB, parent
  5 KB, names 105 KB, **464 KB zlib in total**. This is larger than the 0.37 MB table above because that held only the
  16,272 leaf-most divisions plus ancestors, and many localities were hidden under neighbourhoods.
- **Adjacency**, counting a pair as neighbours only if the smaller polygon is at most 20% inside the larger (10,257 pairs
  were nested and dropped; the result barely moves between 5% and 50%): 61,355 edges, mean 5.6, median 6, p99 11,
  **maximum 113**, 921 islands with none; **119 KB zlib**. Names table plus adjacency is about **0.58 MB** for GB.
- Not re-run for locality only: the cell-to-division index (the 0.69 MB figure ranks every subtype), and the places units.

## Decision

### 1. A Home's location is a country and a chosen division, with an optional coarse cell

Setting up a Home must not assume the user is at home, so it cannot start from a GPS fix. What is stored is:

- `country` (ISO 3166-1 alpha-2);
- `divisionId`, the GERS id of the division the user picked, and the Overture release the id came from (ids can change
  between releases);
- optionally a **cell** (default **resolution 8**, about 0.5 km edge) with a `source` tag: `device` (a fix taken at home),
  `postcode`, or `division` (the division's centroid, coarser, resolution 7). A cell says only that the point is somewhere
  inside it, so it is a real coarsening. Anything needing a coordinate (weather, distance) uses the cell centre, accurate to
  about the cell size. The exact point a user typed or the device reported is never stored.

### 2. The user finds their division by name; the cell index is only a shortcut

The cell-to-division index (second spike) is needed only when the entry starts from a point (device location, postcode).
The main path does not use it:

1. **Country.** Defaulted from the device locale; the user can change it.
2. **Fetch that country's names table** (id, subtype, parent, name; 0.37 MB for GB, measured; DE about 0.7 MB, *estimate*),
   from the bundled copy or from Autonomi (open decision 3). No coordinates are involved.
3. **Search or drill down** to a division (region, county, locality, then neighbourhood where it exists). Results show the
   parent chain ("Newport, Isle of Wight, England") because names repeat. **Only `locality` divisions are selectable**
   (decision 6); counties and regions appear only as the parent chain.
4. **Neighbouring divisions** come from an adjacency list shipped with the table (see "Neighbours without the cell index";
   about 0.12 MB for GB, locality only), not from the cell index. Divisions that merely share a parent are not neighbours.
5. **Confirm.** The chosen division, its parent chain, and its neighbours are shown; nothing is saved until the user
   confirms.

Open issues for this path, all *unmeasured*: Overture's primary name is usually the local-language name (searching "Cologne"
for "Köln" needs common names, which enlarge the table); localities that Overture holds only as points (no polygon) cannot be selected; and the table size for countries bigger than DE.

Optional shortcuts, each producing the same candidate list (leaf division, its parents, and flagged neighbours) through the
cell index: **device location** ("I'm at home now", an OS permission, no desktop equivalent), and **postcode** where a
postcode-to-point table is shipped (UK first; open decision 2). A postcode centroid is rarely as accurate as a res-9 cell,
and a postcode can straddle divisions, so postcode entry always shows the candidates and asks. A map pin is last (it needs
tiles and so the network). The postcode never leaves the device.

### 3. The flow

1. **Entry.** Home setup and Home settings, "Location". Optional; nothing prompts for it after setup and nothing runs in the
   background.
2. **Country, then name search** (decision 2). If the names table is bundled, no request is made; if it is fetched from
   Autonomi or a mirror, a disclosure screen first states who can see the request (the node or host serving it, and the
   device's IP address) and that the request names a country pack, not the user's division.
3. **Shortcuts** (device location, postcode) are secondary buttons on the same screen, each with its own disclosure
   (the OS permission prompt; for a postcode, that it stays on the device).
4. **Show and confirm.** The division is drawn on its parent chain, with its neighbours listed; if a cell was derived, it is
   drawn as a hexagon labelled with its rough size. Nothing is saved until the user confirms.
5. **Saved.** `country`, `divisionId`, the release, and optionally the cell with its `source`. A point is never saved.
6. **Forget.** One button clears the location from the Home and every device.
7. **Audit.** Any request made for the location (names table, index shard) is recorded in the sync activity surface
   (ADR-033): host, date, and what was asked for.

After step 5 no further request is made unless the user edits the location or opens the business list.

### 4. Where it lives

On the Home, synced to the user's paired devices like the Home's name and shape (ADR-013's registry). Whether it is also
shown to other Home members (a Home is a shared household, and a 0.5 km cell is coarse) is an open decision, because the
member-facing copy depends on which document carries the Home's shared fields.

### 5. Business data stored on Autonomi excludes personal information

Autonomi storage is permanent and public, so anything published cannot be taken back or corrected. Business records
published there therefore carry **no phone numbers, email addresses or other personal information**. The record
measured in the third spike already holds none of the contact fields: id, primary name, position, category and
confidence only; no phones, emails, websites, social links or free-text addresses. Names and positions can still identify
a sole trader, so whether even those may be published is **open pending a legal review** (not yet done); until it is
done, nothing from `places` is uploaded. The Overture licence terms for each theme also need checking before any
redistribution.

### 6. Only localities matter

Vesta connects consumers to local traders, so the only division subtype the app selects, indexes or lists businesses for is
`locality` (towns, villages and cities). Microhood, neighbourhood, macrohood and county divisions are not offered to the user;
counties and regions are kept only so a locality can show its parent chain. This supersedes the earlier "leaf-most of any
subtype" ranking for everything built after this decision (the figures in the second and third spikes used it, and have not
been re-run). Localities nest in Overture (about 10,000 pairs of overlapping localities in GB, for example the Northern
Ireland districts over their towns), so a point still takes the **smallest** locality containing it, and the adjacency list
must not count a contained locality as a neighbour.

## Open decisions

1. **H3 library.** Untested on Android; the candidates and their known limits:
   - **`com.uber:h3`** (JNI). Verified on desktop. The jar carries Android natives for **arm and arm64 only, no x86_64**, so
     an x86_64 emulator image cannot load it; the loader extracts the `.so` from jar resources at runtime, which d8 drops, so
     the build must package it explicitly (resource or `jniLibs`). No iOS native, and Vesta has an `iosMain` source set.
   - **A small pure-Kotlin implementation** of just point to cell, cell to centre, parent and neighbours. Runs everywhere,
     no native packaging, but is code we own and must verify against the reference library on a large set of points.
   - `io.github.luneo7:h3` is a repackage of Uber's library under another group id, not a different implementation.
2. **Postcode tables.** Postcode entry needs a postcode-to-point table per country, shipped with the app (a hosted geocoder
   would send the text to a third party). Precision varies a lot: a full UK postcode is about 15 addresses, a US ZIP or German
   PLZ is several km across, Canada's free data is only the first three characters, and some countries have no postcodes.
   Sources and licences (ONS/OS open data for the UK, GeoNames postal codes for many countries) are from memory and **must be
   checked**. Name search (decision 2) does not depend on this, so it can be deferred, UK first. A hosted geocoder
   (Open-Meteo's, shared with the weather widget) remains a fallback needing its own disclosure; not tested here.
3. **GERS step: how.** Options: (a) a precomputed cell-to-division index; (b) reading Overture directly (about 4 MB for
   candidates, 41-95 MB for the exact answer; desktop only until a Parquet reader is shown to run on Android). The second
   spike measured (a) for the UK: 0.69 MB of ids plus 0.37 MB of names and ancestry (about 1.06 MB), so it is small enough to **bundle with the app** (fully offline, updates
   with app releases), and also small enough to serve from any content-addressed store. Where it lives is still open:
   - **Bundled in the app.** No network request at all, so nothing to disclose for this step. About 1.06 MB for the UK; a
     world index is not measured.
   - **Autonomi raw chunks** (root chunk, shards of up to 4 MiB, a pointer to the current root). Lookup is 1 call warm and
     3 cold on a devnet, and the network sees one opaque chunk address for an area of about 1,700 km2 (res-4) or larger
     (res-2 in the spike). Costs about 0.05 ANT per chunk per release, unverified on mainnet; chunks are permanent and
     unchanged shards dedupe across releases. **Blocked on Android:** the embedded client has chunk get but no pointer API
     and a 38 MB library per ABI, so on Android the root address would have to ship with the app (or arrive another way),
     and the library would have to come back in a dynamic feature module or similar. Desktop can use `antd`.
   - **A hosted static file or HTTP mirror** of the same chunks. Because a chunk's address is its BLAKE3 hash, the client can
     verify every byte, so the mirror need not be trusted; the host would see a coarse request and the device's IP address.
   Keep the format transport-agnostic (`get(hash) -> bytes`) so all three can read it. The first GERS slice can bundle the
   UK index and add Autonomi later.
4. **Region.** UK only or global. The UK is about 1.06 MB with names; a world index is extrapolated, not measured, and would need the
   root-and-shards layout rather than one file.
5. **Visibility to members** (see 4 above).
6. **Default resolution.** 8 is the working default. 9 is closer to the earlier "1 km rounding"; coarser reveals less.
7. **Businesses.** Whether to offer a business list at all, and from where. Blocked on the legal review in section 5 and the
   Overture licence check. If it goes ahead: chunk target (256-512 KiB working choice), who pays for the near-full
   re-upload each release (place data changes constantly, so tiles do not dedupe the way divisions do), and a name or
   category search index, which is not designed.

## Acceptance criteria

1. With no location set, no network request is ever made for it, and the Home works as before.
2. Setting up a Home away from home works: I choose my country (defaulted from the device locale) and search for my town by
   name, with no position taken from the device.
3. Search results show each division's parent chain, so two places with the same name can be told apart.
4. Before any request for the names table or an index shard, I am told who can see it and that my IP address is visible; I can
   decline and nothing is sent.
5. I see the chosen division and its neighbouring divisions before anything is saved.
6. What is saved is the country, the division id, the Overture release, and optionally a cell with its source; no
   position is stored anywhere.
7. A postcode I type is never sent over the network.
8. A latitude outside -90..90 or longitude outside -180..180 is rejected before conversion.
9. Forgetting the location removes it from the Home and from my paired devices.
10. Each request made for the location appears in the sync activity list with its host and what was asked for.
11. The same point always produces the same cell on desktop and Android.

## Consequences

- Setup no longer needs a position, so it works away from home. The first slice needs the per-country names table and
  adjacency list built and shipped (UK, locality only: about 0.6 MB together, measured), plus an H3 implementation if cells are stored.
- Homes get a human-readable place (the chosen division and its parents) with no reverse geocoding.
- A name search against a bundled table sends nothing; a fetched table or shard is an outbound request needing a line in the
  privacy notes.
- Weather (ADR-024) and merchants (ADR-026) can use the cell centre, or the division centroid when there is no cell.
- The names table must be ordered spatially (it halves the adjacency list) and the builder must count touching, not
  containing, divisions as neighbours.
- Boundary-straddling is accepted and surfaced, not hidden. About 97% of lookups are exact at res 9 and most of the rest
  can be flagged because a neighbouring cell disagrees; an exact answer would need a further tier of simplified polygons.
- The index builder must split MULTIPOLYGONs before filling, and should check that no large division comes back empty.
- The GERS step is cheaper than first thought (a UK index with names is about 1 MB), which weakens the case for a network lookup
  unless the index must update between app releases.
- Divisions scale as one pack per country (0.1-3 MB each, world about 60-100 MB, an estimate); the builder has to work
  top-down because filling every res-9 cell of a large country exhausts memory.
- A business list is feasible for the UK (about 260 KB per lookup, about 95 MB stored per release) but is gated on the PII
  and licence questions, and on funding a near-full upload each release. Buildings are not pursued.
