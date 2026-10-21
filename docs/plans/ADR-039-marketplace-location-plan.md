# Implementation plan: Marketplace location and search area (ADR-039)

Written 2026-10-21 so a fresh session can build the Marketplace's location capture from [ADR-039](../adr/ADR-039-home-location-h3.md)
without re-deriving context. Read the ADR first (decisions 0-6, the locality-only spike section, open decisions 2, 3 and 7); this
plan says **how to build it in this codebase**, in what order, and how to verify it. **Status: WP1-WP7 built (2026-10-21); the by-hand desktop run is still owed.** As built: see the ADR's "As built" section for the differences
(country step lists only available countries, `VGN2` magic in the names payload, BLAKE3 not checked). The data builder exists: `scripts/gers-index/` (README is its runbook).

## Goal

The first time a member opens the Marketplace's nearby-merchants option on **desktop**, they agree to a short download, choose a
country (preselected from the device locale), search for their home **locality** by name, then tick which neighbouring
localities to include ("Also search nearby places"). Nothing in Home creation asks for a location, and no position is read from
the device. The only network request is the download of the location data from Autonomi, made after a disclosure screen. The
choice is stored and can be edited or forgotten. Android and iOS show "not available on this device yet".

## State of the repo (read 2026-10-21; verify before relying on it)

- **Marketplace:** `ui/screens/MarketplaceScreen.kt` has one row, Local Merchants (`onLocalMerchantsClick`). `App.kt` routes
  `HomeDestination.Marketplace` (~`:731`) and `HomeDestination.Merchants` (~`:564`, `:737`); `ui/flows/MerchantsFlow.kt` is the
  pattern for a multi-step flow (a private `Step` enum, plain state + callbacks).
- **Merchants (ADR-026):** a shared per-Home document, feature `merchants`, with `lat`/`lon` (millionths of a degree) and a
  free-typed `gersId` per merchant. It is the household's *own* list; it is not what this plan produces.
- **Adding a synced document:** `shared/src/jvmAndroidMain/.../automerge/VestaHomeSources.kt` builds the sources and lists the
  types in `documentTypes()` as `SpaceDocumentType(feature, module, scope, forHome, localChanges)`. `CalendarSources.kt` is the
  template for a type with a **private** document: one `SpaceDocumentStore` per feature, `Scope.Private`, id built with
  `spaceDocumentId(homeId, "private-calendar")`. `DocumentIdsTest` pins every id and feature list literally; a new type
  fails it until added. `VestaSyncModules` names the module shown in Sync activity (ADR-033).
- **Kits:** the registry and engine live in `uk.veritaslabs.kits` (ADR-038, `docs-kit`, `sync-kit`); Vesta supplies its types.
- **Nothing is bundled** (decision 1). Compose resources exist (`composeResources/files/`) but are not used for this data.
- **Desktop reaches Autonomi through the standalone `antd` REST daemon** (`jvmMain/.../autonomi/StandaloneAutonomiClient.jvm.kt`:
  port from `daemon.port`, default 8082). `AutonomiClient` exposes only `endpoint` and `connectionStatus()`; there is no chunk call.
  **Checked 2026-10-21:** `antd` 0.10.1-rc.3, then the updated **0.15.0**, started with `--network local --peers <devnet bootstrap>`
  (and their own `XDG_DATA_HOME` and `--rest-port`, so the user's normal daemon is untouched), read the published root chunk from
  the devnet with `GET /v1/chunks/{hex}`, which returns JSON `{"data": "<base64>"}` (same shape in both). **Pointers:** the
  installed 0.15.0 answers 404 to `GET /v1/pointers/keys` and to the pointer's `get`/`resolve`/`finality`, so its build has no
  pointer routes. The antd source in `~/Development/scratch/ant-sdk-src` (checkout of 2026-10-19) *does*: `GET /v1/pointers/{addr}`,
  `/resolve`, `/finality`, `/controller` (read; no key needed) and `POST /v1/pointers`, `/update`, `/transfer` (write, signed
  with keys the daemon loads from `--pointer-keys`). So pointer reads are expected in a later antd release; until then the root
  chunk's address is configured.
- **Android and iOS:** `UnsupportedAutonomiClient`; the Android embed has chunk get but no pointer API (ADR-039).
- **The Home registry is in the kits repo.** `~/Development/VeritasLabs/veritas-kits`, `spaces-kit` (`SpaceRegistryDocument.kt`):
  the per-user document `user:homes` (so it syncs to the user's own devices only, not Home members) holds one map per Home with
  `name`, `shape`, `createdAt`, `deleted`. Its functions are `@KitsInternalApi`, and the kit is published with
  `./gradlew publishToMavenLocal` and consumed by Vesta from there. Nothing in it can carry location.
- **The builder's chunks are zlib-compressed**, and there is no inflate in `commonMain`.
- **Data on hand (GB, release 2026-09-23.1):** published on the devnet (see `scripts/gers-index/README.md` to republish after a
  reboot); `~/Development/scratch/gers-index/out/published.json` has the addresses. Names table 466 KB (22,093 rows), adjacency
  119 KB. The names table has **no coordinates**. The devnet loses its data on reboot, but chunk addresses are hashes of
  unchanged content, so the root address stays the same after a republish.
- **Conventions (project memory, must follow):** every test method has `// Arrange` / `// Act` / `// Assert`; prefer
  `x?.let { }` to `if (x != null)`; every `@Preview` is light/dark x phone/tablet grouped by device; each feature has
  Given/When/Then acceptance tests in `docs/testing/acceptance-tests.md` *and* unit tests; commit only when asked, ending
  messages with the attribution lines.

## Decisions taken in this plan (fold back into the ADR when done)

1. **Nothing is bundled; desktop fetches from Autonomi.** A `LocalityPackSource` reads the root, names and adjacency chunks through
   `antd`'s `GET /v1/chunks/{addr}`. The root chunk lists the addresses of the other chunks (the builder already does this). For the
   test the source is the local devnet through a second `antd` (`--network local --peers ...`); on mainnet the same code reads the
   real network. The cell index (shards) is not fetched in this slice (shortcuts come later, WP9).
2. **The root is configured as an address; a pointer is preferred when antd can resolve one.** The source reads
   `VESTA_LOCALITY_POINTER` (a pointer address) or, failing that, `VESTA_LOCALITY_ROOT` (the root chunk's address), or the matching
   `vesta.locality*` system properties, and the antd REST port from `VESTA_ANTD_PORT` (else the standard `daemon.port`
   discovery). With a pointer set it calls `GET /v1/pointers/{addr}/resolve` and, if antd answers 404 (as the installed 0.15.0
   does), falls back to the root address, or reports "Location data is not configured on this device" if neither is set. Until a
   release with pointer routes, use the root address; the pointer path is written against the source's REST surface and
   cannot be tested until then.
3. **Fetched chunks are cached on disk by address.** Chunks are immutable and content-addressed, so a cached file never goes
   stale; the cache is a directory under the app's data directory. After the first fetch the flow works offline.
4. **Inflate on desktop only.** The chunks are zlib. A small `Inflater` interface lives in `commonMain`; the JVM implementation
   uses `java.util.zip` (in `jvmAndroidMain`, so Android gets it for free later); iOS gets a stub.
5. **Coordinates added to the names table.** "Nearest first" needs a centroid per locality: two integers in millionths of a degree
   (the unit ADR-026 uses), delta-coded in the spatial order. Builder change and a new format version (`VGN2`) in WP1.
6. **The choice is stored on the Home registry entry** (user decision), which is per user and syncs to the user's own devices only.
   The registry is in the kits repo and has no room for it, so **`spaces-kit` gains a generic string `attributes` map per Space**
   (kit stays Vesta-agnostic; Vesta writes `locality.country`, `locality.home`, `locality.release`, `locality.cell`,
   `locality.cellSource`, and `area.<localityId>` = `1` or `0`). Each key is its own Automerge field, so edits on two devices
   merge; unticking writes `0`, not a delete. This is a cross-repo change (WP4). ADR-034's versioning rules apply: an older app
   ignores unknown keys, and nothing is read that is absent.
7. **The Marketplace row exists before anything uses it** (user decision). Nothing reads the area yet: the merchant list needs the
   Overture `places` chunks, gated on the legal review (ADR-039 decision 5). The Marketplace row opens the flow, then an honest
   empty state. Merchant counts and the download-size footer are omitted until the places directory exists.
8. **Neighbour list rules** (ADR-039 decision 3, step 5) are a pure `NearbyPlaces` function so they are unit-tested without UI:
   nearest first, nested localities as their own rows labelled "inside X" or "contains Y", the home locality excluded from the
   choices (always on), at most ten shown until "show more".
9. **The disclosure screen is part of the flow** (ADR-039 decision 3, step 2): before any request it says who can see it (the
   Autonomi nodes serving the chunks, and the device's IP address), that the request names the country's data and not the user's
   locality, the size (about 0.6 MB), and offers Continue or Not now. Recording the request in Sync activity (ADR-033, ADR-039
   step 8) is a separate follow-up (WP8), because that log records sync transport events, not outbound fetches.

## Work packages

Each WP is a commit-sized slice with its tests. Everything up to WP6 can be built and verified without a UI run.

### WP0. Decisions already confirmed

Confirmed by the user on 2026-10-21: build the UI ahead of usage (decision 7); store on the Home registry (decision 6); bundle
nothing, desktop first, Android later (decisions 1-2). Still to settle during the build: the data-directory location for the chunk
cache (decision 3), and whether the kit change ships as a minor version of `spaces-kit` (WP4).

### WP1. Builder: coordinates (`scripts/gers-index/`)

- Add centroid latitude and longitude (microdegrees, delta-coded) to `encode_names`/`decode_names`; bump the names magic to
  `VGN2` and store it in the root so a reader can refuse an unknown version.
- Re-run `build` and `verify` against the devnet; record the new root address in `README.md` and `published.json`.
- No raw container and no `pack` command are needed (bundling dropped).
- Verification: `verify` round-trips the new column; row counts 22,093 and 61,355; the devnet root is readable through `antd`.

### WP2. Pack decoder and `LocalityDirectory` (`commonMain`, package `...location`)

- `Inflater` interface (decision 4) and `LocalityPack` parsing the three chunks into flat arrays (ids, subtype, parent index, name,
  centroid, adjacency) without a `Locality` object per row (22k rows).
- `LocalityDirectory` over a pack: `search(query)` (case- and diacritic-folded; prefix matches first, then word-prefix, then
  contains; only `locality` rows; capped), `chain(id)` (full parent chain, e.g. "Newport, Isle of Wight, England"),
  `neighbours(id)`, `contains(id)` and `insideOf(id)` (derived from the parent pointer when the parent is a locality),
  `centroid(id)`, `distanceMetres(a, b)` (haversine).
- `NearbyPlaces.rows(home, directory, shown)` implements decision 8.
- Tests (`commonTest`): a hand-built tiny pack covering ties, nested localities, islands, a name with a diacritic, two localities
  with the same name; a fake `Inflater`. A `jvmTest` that decodes the real chunks (from the devnet when it is running, else
  skipped with a clear message) and asserts 22,093 rows, 61,355 edges and the ADR's parent breakdown (11,741 county, 10,132
  locality, 58 region).

### WP3. Fetching: `LocalityPackSource` and the chunk call (`commonMain` interface, `jvmMain` implementation)

- Add `suspend fun chunkGet(addressHex: String): ByteArray?` to `AutonomiClient`; `StandaloneAutonomiClient` implements it with
  `GET /v1/chunks/{hex}` and base64-decodes `data`; the Android and iOS clients return null.
- Verify the address of what came back: `BLAKE3(content)` must equal the requested address (the client need not trust antd or
  the network). The repo has no BLAKE3 yet; if adding a dependency is unwelcome, compare lengths and decode errors and note the
  gap. Check before building.
- `AutonomiLocalityPackSource(client, rootAddress, cacheDir, inflater)`: reads the root (parse `VGI2`/`VGN2`), then the names and
  adjacency chunks, caching each by address; reports `Unconfigured | NotAvailableOnDevice | Unreachable | Ready`.
- `LocalityPackSource.countries()`: the root names its country, so GB only. Any other country is shown disabled, "Not available yet".
- Device region code for the country preselect (check for an existing locale helper; else `expect`/`actual`).
- Tests: a fake `AutonomiClient` returning the chunks (decoded from a small fixture built by the same code that builds the real
  ones); cache hit makes no second call; a corrupt chunk is rejected; unconfigured and unreachable states.

### WP4. Store the choice on the Home registry (cross-repo: `veritas-kits`, then Vesta)

- **kits (`spaces-kit`):** `RegistrySpace` gains `attributes: Map<String, String>`; `readSpaceRegistry` reads every string field of
  the entry other than the four reserved keys; new `putRegistryAttributes(doc, id, changes: Map<String, String?>)` sets or clears
  keys (a null value removes the key) and reports whether anything changed; a deleted Space stays deleted. `SpaceRegistry` exposes it
  and the sync path already carries the document, so no sync change is expected (verify with the existing registry sync tests). Update the
  `.api` dump, add kit tests (round trip, two devices set different keys and both survive, a tombstoned Space is not changed),
  bump the version and `publishToMavenLocal`.
- **Vesta:** a `HomeLocationRepository` in `commonMain` (`read(homeId)`, `setHome(...)`, `setArea(ids)`, `clear(homeId)`) backed
  by that function on the JVM and Android side (`jvmAndroidMain`), with a no-op iOS implementation. The `Home` database table is
  not changed: the choice is read from the registry document, not mirrored.
- Tests: the repository round trip; `clear` removes all `locality.*` and `area.*` keys; an older registry entry with no attributes
  reads as "no location"; the choice reaches a second paired device on the fake mesh and a Home member's device never receives it
  (the registry is per user; assert it).

### WP5. View model and screens (`commonMain`, `ui/screens`, `ui/flows`)

- `MarketplaceAreaViewModel`: state = `Disclosure | Country | HomeLocality | NearbyPlaces | Done`; holds the pack-source status, the
  query and results, the chosen home, the ticked set and the "shown" count; its only I/O is the pack source and the repository.
- Screens as plain state + callbacks:
  1. `AreaDisclosureScreen`: decision 9 copy; Continue / Not now; shown only when the data is not already cached. If the source is
     `Unconfigured` or `NotAvailableOnDevice`, this screen is replaced by the matching message.
  2. `AreaCountryScreen`: the countries, preselected from the device region; unsupported ones disabled.
  3. `AreaHomeLocalityScreen`: search box with results each showing the full chain; nothing shown for an empty query (no browsing
     22k rows); a "No match" state.
  4. `AreaNearbyScreen`: the home locality ticked and locked on top, then the rows from `NearbyPlaces` (nearest ten, search, "Show
     more", nested labels), prompt "Also search nearby places", a summary line with the number ticked.
  5. `AreaSummary` card for the Marketplace: "Searching in X and N nearby places", Edit, Forget.
- `MarketplaceAreaFlow` wires them like `MerchantsFlow`; Back never loses what was typed.
- Strings in `values/` (including "Your area is stored on your devices only"). Previews light/dark x phone/tablet per screen.
  Test tags in an internal object.
- Tests (`commonTest`, Compose UI): the flow with a fake pack source; the disclosure appears first and Not now sends nothing;
  the country preselect; typing shows chains; ticking, unticking, "Show more"; a nested row is labelled; Forget clears; Back keeps
  state; a stored id missing from the pack shows "Your area needs choosing again".

### WP6. Wire into the Marketplace (`App.kt`, desktop app)

- Add the second Marketplace row, **Nearby merchants**. With no stored area it opens `MarketplaceAreaFlow`; with one it shows the
  `AreaSummary` and the empty state ("Nearby merchants will appear here once business data is available"), with Edit and Forget.
  Local Merchants (ADR-026) is unchanged and stays the first row.
- `HomeDestination.MarketplaceArea` in the destination enum and the back handling (~`:555-564`).
- `desktopApp/.../main.kt` builds the pack source from the configuration in decision 2; `androidApp` passes an unavailable one.
- Create a Home and its tests are **not touched**; an acceptance test asserts it still asks for no location.
- Tests: the `MarketplaceScreen` test for the new row; an App-level test that no area sends the user to the flow and an area does not.

### WP7. Acceptance tests, docs and the ADR

- Given/When/Then entries in `docs/testing/acceptance-tests.md` for ADR-039 criteria 1-10 as reworded for the Marketplace, backed
  by `MarketplaceAreaAcceptanceTest` (jvmTest, fake mesh for the sync criterion, Compose UI for the flow).
- Add a short "As built" to ADR-039 and a one-line pointer from ADR-026 ("the Marketplace now also holds a search area; see ADR-039").
- Update the project memory entry for ADR-039.
- **By-hand desktop run** (the by-hand step the user asked for): start the devnet and republish (`scripts/gers-index/README.md`), start a
  second antd (`XDG_DATA_HOME=<scratch> ANTD_GRPC_PORT=0 antd --network local --peers <the manifest's bootstrap list> --rest-port
  18099`), run the desktop app with `VESTA_ANTD_PORT=18099 VESTA_LOCALITY_ROOT=<root from published.json>`, open the Marketplace
  of a Home, and walk the flow; then quit, restart with antd stopped, and confirm the cached data still works.

### WP8. Later: record the fetch in Sync activity

ADR-039 decision 3 step 8 wants the request listed with host and what was asked for. The log (ADR-033) records sync transport
events only; extend it or add a sibling "requests" list. Not in the first slice.

### WP8b. Later: places (gated)

Not in this plan. Needs the legal review (ADR-039 decision 5), the Overture `places` licence check, the places directory and chunk
format, the PII-free record, and the nesting and unassigned-places decisions in ADR-039 open decision 7. The area stored in
WP4 is the input to it; merchant counts and the download footer arrive with it.

### WP9. Later: shortcuts

Device location ("I'm at home now") and postcode, through the cell index. Needs: fetching the shards (0.90 MB for GB),
a Kotlin H3 implementation (ADR-039 open decision 1), the OS location permission with its disclosure, and the postcode table
(open decision 2). Both end in the same candidate list; neither is required for the main path.

### WP10. Later: pointers and Android

Desktop: when the installed antd has the pointer routes, test the pointer path from decision 2 and make it the default. Android:
resolve the pointer once the embed exposes one; bring the embedded client back for
Android (its size cost is in `docs/plans/android-app-size-reduction.md`). Only `LocalityPackSource` changes.

## Verification

- Builder: `scripts/gers-index` README steps; `verify` passes on the devnet.
- Unit: the `commonTest` suites above, plus `DocumentIdsTest`, `VestaHomeDocumentsTest` and `DeviceDataSyncServiceTest` (known to
  be flaky under load; re-run before blaming a change).
- UI: the Compose UI tests; then the by-hand desktop run in WP7. Check search stays responsive with the 22k-row pack (latency,
  no per-row allocation). Android is out of scope until pointers and the embed return.
- Privacy: a test that nothing is requested before the user agrees on the disclosure screen (the fake `AutonomiClient` records
  zero calls after Not now), that a postcode or typed query is never sent, and that Create a Home asks for no location.

## Risks and open points

- **A captured area with no consumer** (decision 7). Accepted by the user so the flow can be tested on a device; the empty state
  says so.
- **Cross-repo change.** WP4 edits `veritas-kits` (a different repo, published to the local Maven repository). It needs its own
  commit and version bump before Vesta can compile against it; do not start WP4's Vesta half before the kit is published.
- **Config-only root address.** Until the installed antd serves pointers, a rebuilt index with changed content has a new root address and the
  configured value must be updated; unchanged content keeps the same address.
- **A second antd on the dev machine.** The user's normal antd (default network, `~/.local/share/ant`) must not be touched; the test
  one uses its own `XDG_DATA_HOME` and port.
- **The pack is GB only.** A user elsewhere sees "Not available yet" and gets no area; say so in the UI. Other countries need the
  top-down builder (the res-9 fill of a large country exhausts memory) and a licence check.
- **About half of GB places lie in no locality polygon** (ADR-039 locality-only spike). A list built on this area would miss them;
  open decision 7 must settle nearest-locality assignment before the list is designed.
- **Nested localities double-count if a place is assigned to both a parent and a child.** The smallest-locality rule (ADR-039
  decision 6) is what prevents it; the places builder must follow it.
- **Overture release drift.** Locality ids can change between releases. The registry stores the release it used; when a newer
  pack is fetched, a stored id that no longer exists must show "Your area needs choosing again" rather than fail. Add that case to
  the WP5 tests.
- **Unmeasured:** decode time and memory of the 22k-row pack on a phone; local-language names and common names for search
  ("Cologne" for "Köln"); other countries' parent pointers.
