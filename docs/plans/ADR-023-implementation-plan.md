# Implementation plan: blob storage and transfer, photos first (ADR-023)

Written 2026-10-08 so a fresh session can build [ADR-023](../adr/ADR-023-blob-storage-and-transfer.md) without
re-deriving context. Read the ADR first (reference shape, "Why not the hub", Decision 1-8, acceptance criteria 1-11); this
plan says **how to build it in this codebase**, in what order, and how to verify it. **Status (2026-10-08): WP0-WP10 are built and tested; see the ADR's "As built" section for the differences and what is not done.**

## Goal

A member adds a photo to a recipe. It is downscaled to WebP, stored once on the device, referenced from the Cookbook document
with a placeholder blur, and moved to every other device on the Home's roster by native x0x file transfer, **only while a holder and
a wanter are online together** (no store-and-forward, no hub involvement). A device that hasn't got the bytes shows the blur.

## State of the repo (read 2026-10-08)

- **Cookbook media today** are `RecipeMedia(uri, kind, caption)` (`ui/screens/CookbookViewModel.kt:23`), written into the recipe's
  `media` list by `automerge/CookbookDocument.kt` (keys `uri`, `kind`, `caption`; a save rewrites the whole recipe). No picker, no
  image loader, no thumbnails; the detail screen shows labelled tiles.
- **x0x client:** `X0xClient` / `AbstractX0xClient` have **no file API** (only `sendDirect`, stores, groups). Event streams are
  `directEvents` (`/direct/events`), `presenceEvents`, and `gossipEvents` (`/events`, which **only forwards `type == "message"`**
  frames, `AbstractX0xClient.kt:55`). `X0xEventHub` wraps direct and presence streams in reconnecting flows.
- **x0xd facts (read from source, to be confirmed by Spike 1):** `file:offer` and `file:complete` are broadcast on **`/events`**,
  not `/direct/events`, as `{type, data}` frames (`file:complete` carries `sha256` and the final `path`). `/files/accept/{id}`
  is the only way to start a transfer. Finished files land in the daemon's flat `transfers_dir/<filename>`.
- **Sync wiring:** `datasync/DeviceDataSyncService.kt` runs everything: it collects `directMessages` and routes them to `handle`,
  keeps `peersFor(documentId)` (own paired devices + the Home roster's devices), reacts to `cookbookSources.localChanges`, and has a
  30 s `round()` plus presence-triggered `syncWith`. `SyncableDocument` (`generate`/`receive`) is `internal`. It is built in
  `desktopApp/.../main.kt:22` and `androidApp/.../MainActivity.kt:32`.
- **Retries:** `PendingMessage.sq` (migration `10.sqm`) holds direct messages resent each round for ADR-017; not suited to file transfers.
- **Mobile data:** `settings/AppSettingsRepository.useMobileData()` + `X0xNetworkPolicy.useMobileData`. **The policy is enforced
  by stopping the embedded daemon** (`EmbeddedX0xClient.android.kt:139-218`) while on mobile data and disallowed, so nothing can
  flow; the reconciler needs no separate gate, only a way to *report* "waiting for Wi-Fi".
- **Database:** async SQLDelight; migrations `1.sqm`-`16.sqm`, so the next is **`17.sqm`**. Every `.sq` table is mirrored by a migration.
- **Fake mesh:** `jvmTest/.../devicesync/FakeX0xNetwork.kt` (nodes, `offline`, `comeOnline`, DMs, groups, group stores). **No files.**
- **Real-daemon tests:** `jvmTest/.../datasync/DaemonHarness.kt` (`Daemon`, `Phone`), opt-in, used by `HubDaemonIntegrationTest`.
- **Dependencies:** Automerge (JVM/Android only), Compose Multiplatform (Skia on desktop), no image library, no EXIF library. Desktop is
  the main dev target; Android must compile; iOS only has to still compile (no Automerge there, so all blob code lives in `jvmAndroidMain`
  like the other document code, apart from small `commonMain` interfaces the UI needs).
- **Conventions (project memory, must follow):** every test has `// Arrange` / `// Act` / `// Assert`; prefer `x?.let { }` to
  `if (x != null)`; every `@Preview` is light/dark × phone/tablet grouped by device; every feature has Given/When/Then acceptance
  tests (`docs/testing/acceptance-tests.md`) *and* unit tests; commit only when asked and end commits with the attribution line.

## Decisions taken in this plan (fold back into the ADR when done)

1. **Blob code lives in `jvmAndroidMain`**, one class per job, with small interfaces in `commonMain` for what screens need
   (`PhotoPicker`, `BlobImage`). Same split as the Cookbook.
2. **Receiving uses `file:complete`, not polling.** The accept gate runs on `file:offer`; the move-into-store step runs on `file:complete`
   (it carries the path and the daemon's own sha256). Our re-hash of the moved file is still the authority.
3. **Send by `path`, not `data_b64`**, to avoid a second spooled copy. Whether the daemon can read the path is Spike 1; the fallback is `data_b64`.
4. **Filename is `<hash>.webp`.** The daemon's `transfers_dir` is flat, so a hash name cannot collide, and the receiver can read the
   wanted hash straight from the offer.
5. **The want-request is its own small JSON direct message** (`{"vesta":"blob-want","homeId","hash"}`), recognised by a `kind` field the same way
   agent and device-sync JSON already share the stream; it is **not** a `SyncEnvelope`. It is not queued in `PendingMessage`; the
   reconciler repeats it each round with its own backoff, so a vanished message costs one round.
6. **Placeholder algorithm: ThumbHash unless Spike 3 says otherwise.** The reference field is named `blur` and treated as an
   opaque string plus a one-letter scheme prefix (`t:`/`b:`), so switching later does not break documents.
7. **Cookbook compatibility.** A media entry gains `hash,size,mime,w,h,blur` next to `uri`; an entry with a `hash` is a blob, one with only a
   `uri` is a legacy link. Old devices ignore the new keys and still show a tile.
8. **Which "device" serves a want:** any roster device of a Home whose document references the hash, plus the user's own paired devices
   (`peersFor(documentId)` already gives exactly this set; reuse it).

## Work packages

Order matters: WP0 is the gate, and everything after it can be built and tested on the fake mesh before any real daemon is involved.
Each WP is a commit-sized slice with its tests.

### WP0. Spikes (before any production code)

Run as throwaway opt-in tests under `jvmTest/.../blobs/` using `Daemon`/`Phone` from `DaemonHarness`. Record findings in a new
"Spike findings" section of the ADR, as ADR-020 did.

1. **Native transfer end to end** (two daemons): `POST /files/send` with `path` and with `data_b64`, observe `/events` frames
   (`file:offer`, `file:complete`) and whether `/events` delivers them to our parser; `accept`; check the landed file, its name,
   `transfers_dir` contents afterwards; what an offer to a stopped daemon does and how it surfaces (error, `Failed` state); whether
   `path` works when the sender daemon is another user's (desktop standalone x0xd). On an Android emulator, whether `file:offer`
   arrives with the app backgrounded.
2. **Three-device want flow:** A adds, B was offline, C never saw A online. Confirm B gets it from C after C holds it, and measure
   convergence.
3. **Encoder and placeholder:** on Android and desktop, bytes per photo at long edge 1600 / quality 70, 75 and 80 for ~10 real photos
   (different scenes, a portrait phone shot, a dark one); thumbnail-hash length, decode time and how each looks. Decides quality,
   ThumbHash vs BlurHash, and the input size cap.
4. **Transfer hygiene:** do `.part` and final files stay in `transfers_dir` after the app moves the file? Is there an API to delete
   a finished transfer? Disk growth over many photos.

**Exit:** the ADR's four spikes have written findings and the numbers (quality, max edge, input cap, placeholder length) are chosen.
If Spike 1 shows the embedded daemon can't surface `file:offer` or accept reliably, **stop and revisit the ADR**.

### WP1. File transfer in the x0x client

- `X0xClient` / `AbstractX0xClient`: `sendFile(agentId, filename, size, sha256, path | bytes): String` (returns the transfer id),
  `listFileTransfers()`, `getFileTransfer(id)`, `acceptFile(id)`, `rejectFile(id)`, and `fileEvents(onEvent, onClose): X0xEventStream` reading `/events`
  frames whose `type` starts with `file:` into `X0xFileEvent.Offer(transferId, filename, size, sender)` / `.Complete(transferId, filename, sha256, path)` /
  `.Failed(...)`. Add the model types to `X0xModels.kt`. Keep `gossipEvents` as it is.
- `X0xEventHub.fileEvents`: a reconnecting flow like `directMessages`.
- All three platform clients inherit from `AbstractX0xClient`; check the iOS actual still compiles.
- **Fake mesh:** extend `FakeX0xNetwork` with files: a send to an online node emits an offer event on that node; `acceptFile` completes
  the transfer and emits a complete event with a path in a per-node temp dir; a send to an offline node fails like x0xd does (as found in Spike 1);
  nodes have a transfers dir. Mirror the real states (`Pending`, `InProgress`, `Complete`, `Failed`).
- **Tests:** `AbstractX0xClientFilesTest` against a stub HTTP layer (request bodies, parsing of the frames, `type` filtering); the fake's own test.

### WP2. Blob store and want list

- `blobs/BlobStore.kt` (`jvmAndroidMain`): `has(hash)`, `file(hash)`, `put(bytes): BlobInfo`, `importFile(path, expectedHash, expectedSize): Result` (stream-hash
  the file, reject on mismatch, write to a temp name in the store dir, `rename` into `blobs/<aa>/<hash>`, then delete the source),
  `delete(hash)`. The root dir is injected (Android `filesDir/blobs`, desktop the app data dir), like the database path.
- **Schema (`Blob.sq` + `17.sqm`):** `Blob(hash PK, size, mime, lastReferencedAt)` and `BlobWant(hash PK, homeId, size, firstWantedAt, lastAskedAt, attempts)`.
  Both written in the migration and the `.sq` (kept in step, as `PendingMessage.sq` notes).
- `blobs/BlobRepository.kt`: wraps the store and tables; `want(hash, homeId, size)`, `markHave(hash)`, `wants(): List<BlobWant>`, `touch(hash)`, grace-period deletion
  helpers (WP9).
- **Tests:** unit tests for `BlobStore` (hash mismatch is discarded and leaves no partial file; a crash between temp write and rename leaves nothing visible;
  idempotent put) and `BlobRepository`; `VestaDatabaseTest` gets the migration.

### WP3. Placeholder hash (pure Kotlin, `commonMain`)

- `ui/blur/` or `blobs/Placeholder.kt`: `encode(pixels: IntArray, w, h): String` and `decode(hash): Pixels(w, h, argb)` for the algorithm WP0 chose, with
  the one-letter scheme prefix (decision 6).
- **Tests:** known vectors from the reference implementation, round-trip colour tolerance, malformed input never throws (returns null).
- Display needs an `ImageBitmap` from pixels: see WP5.

### WP4. Photo ingest

- `commonMain` interface `PhotoEncoder`: `encode(source: PhotoSource): EncodedPhoto?` where `EncodedPhoto(bytes, w, h, blur)`. `PhotoSource` is a byte array or a platform path/URI.
- **Android actual:** `BitmapFactory` with `inSampleSize` bounds first (never decode a 48 MP photo at full size), `ExifInterface` for orientation,
  `Bitmap.compress(WEBP_LOSSY, q)`, then a tiny scaled copy for the placeholder. **Desktop actual:** Skia (`org.jetbrains.skia.Image`, `EncodedImageFormat.WEBP`)
  if Spike 3 confirms it; otherwise a WebP encoder dependency. Metadata is dropped simply by re-encoding from pixels; add a test that proves EXIF GPS is gone.
- Limits from Spike 3: reject inputs over the cap and anything that fails to decode, with a user-facing message.
- `BlobIngest` (`jvmAndroidMain`): `suspend fun addPhoto(source): BlobReference?` = encode → `BlobStore.put` → touch → returns `{hash,size,mime,kind,w,h,blur}`.
- **Tests:** a JVM test with a small generated image (size cap respected, WebP magic bytes, hash stable for the same input, a rotated EXIF source comes out upright,
  GPS gone). Android encoder under `androidHostTest`/device tests only if cheap.

### WP5. Document model and UI

- **`RecipeMedia`** gains `hash: String?`, `size`, `mime`, `w`, `h`, `blur`. `CookbookDocument.kt` writes and reads the new keys (decision 7); a `uri`-only entry is unchanged.
  Add `blobReferences(): Set<BlobReference>` to the Cookbook document and a hook so `DeviceDataSyncService` can ask any Home document which hashes it references
  (`SyncableDocument` is `internal`; add an `internal fun blobReferences()` with a default empty set so Pantry and shopping list are untouched).
- **Picker:** `commonMain` `expect`/interface `PhotoPicker` (Android: `ActivityResultContracts.PickVisualMedia`; desktop: a file dialog). The recipe edit screen's media section
  gets an "Add photo" button replacing the free-text URI for new entries; remove-photo works as today. Keep legacy link entries editable.
- **Display:** `BlobImage(ref, store)` composable: file present → decode → show; missing → the blur at `w`×`h` with a small "waiting for photo" cue (a spinner or a muted label, not an error).
  Needs two small `expect`/`actual`s: `decodeImage(bytes): ImageBitmap?` (Android `BitmapFactory`, desktop Skia) and `imageBitmapFromArgb(w, h, argb)`.
  Downsample on decode for lists.
- **Previews:** every new composable gets the light/dark × phone/tablet matrix grouped by device (project convention); include the placeholder state.
- **Tests:** document round-trip (with and without `hash`), `blobReferences`, a Compose test that the missing-bytes tile shows the cue and the present-bytes tile doesn't.

### WP6. Reconciler and want-serving (`blobs/BlobTransferService.kt`)

One service, like `AgentCommandService`, started from `DeviceDataSyncService.run()` (or beside it, sharing the `X0xClient`).

- **Reference scan:** on every Home document change (`cookbookSources.localChanges`, and after `receive` applies a peer's change) and at app start,
  collect `blobReferences()` per Home, `BlobRepository.want()` the ones with no local file, `touch` the ones present.
- **Push on ingest:** after `addPhoto` and the document save, `sendFile` to every online device in `peersFor(Home's cookbook doc)`; the transfer id is kept only for logging.
  Offers to an offline peer are not retried here: that peer will `blob-want` (below).
- **Pull:** each round, on presence events and on foreground, for each want whose backoff has elapsed, send `blob-want` to the Home's reachable peers. Backoff per want: 30 s doubling to 10 min,
  reset on a presence event for a Home peer. Stop asking when the want is satisfied.
- **Serve:** on an incoming `blob-want` from a sender that is on that Home's roster (or a paired device) and for a hash the Home's documents still reference, if the blob is in the store, `sendFile` back.
  Otherwise ignore silently. Serve at most N concurrent transfers and de-duplicate repeated asks within a window.
- **Accept gate (decision 2):** on `file:offer`, accept only if (sender is on a roster/paired device) **and** (the filename is `<hash>.webp` for a hash on the want list) **and** (the size matches the want). Any other offer is rejected
  at once (it costs nothing and frees the sender's record; the ADR's timeout wording becomes "reject immediately").
- **Finish:** on `file:complete`, `BlobStore.importFile(path, hash, size)`; success deletes the want, marks present and tells the UI; a mismatch deletes the file and keeps the want.
- **Mobile data:** read `X0xNetworkPolicy.useMobileData` and the existing metered check to expose `waitingForWifi: Flow<Boolean>`. The daemon being stopped already prevents transfers.
- **Resumability:** all state is in `BlobWant`/`Blob` and the files; a killed app restarts the reconciler with the same want list. A half-received transfer is the daemon's `.part`; the
  next offer replaces it (hygiene per Spike 4).
- **Tests (fake mesh, WP1's file support):** author pushes to an online member; a device offline at ingest later gets it from another holder via `blob-want`; an unsolicited offer and
  one for an unwanted hash are rejected; a hash mismatch leaves the want; a non-member's `blob-want` is ignored; repeated asks are de-duplicated.
- **Real-daemon test:** `BlobTransferDaemonIntegrationTest` (opt-in) for the three-device flow from Spike 2.

### WP7. Surfacing state

- Cookbook list and detail show per-photo state (present / waiting / waiting for Wi-Fi) through `BlobImage`; the status card for x0x can show a count of wanted blobs (optional, last).
- Settings text for "Use mobile data" mentions photos.

### WP8. Wiring and platform specifics

- Construct the service in `main.kt` and `MainActivity.kt` and give it the blob directory (desktop app data dir; Android `filesDir`). Expose it to `App.kt` through the same `internal` hooks the Cookbook uses
  so `BlobImage` and the picker work in tests.
- Android: no new permissions (photo picker needs none); confirm minSdk 26 supports the picker backport. Desktop: file dialog.
- Dependency additions (WP0 decides): a WebP encoder for desktop only if Skia can't; nothing for Android.

### WP9. Garbage collection

- A periodic job (in `BlobTransferService`'s round) deletes a blob whose last reference was seen more than 7 days ago and that is on no document and no want; resets if a reference reappears.
  Orphan files with no `Blob` row (a crash between rename and insert) are adopted or removed at startup.
- **Tests:** within the grace period nothing is deleted; after it, the file and row go; re-reference cancels.

### WP10. Acceptance tests, docs, ADR

- `acceptance/CookbookPhotosAcceptanceTest.kt` (Compose UI test, real stack, fake mesh and fake picker) with one Given/When/Then test per ADR criterion that is user-visible (1, 2, 3, 4, 7, 8, 10), and unit or service tests
  cover the rest (5, 6, 9, 11). Add the criteria to `docs/testing/acceptance-tests.md`'s list of features if it keeps one.
- Update ADR-023: status Accepted/Implemented, Spike findings, any decision deviations above; update ADR-022's "Media pipeline" not-done items.
- Real-app run (not just tests): two desktop instances (or desktop + Android emulator), add a photo, watch it arrive, kill and restart mid-transfer.
- Add the user-facing note to `docs/product/` describing what photo syncing can and can't do (no store-and-forward).

## Risks and how the plan handles them

| Risk | Handling |
|---|---|
| Embedded Android daemon doesn't surface `file:offer` in the background, or can't be accepted reliably | Spike 1 is a gate; if bad, revisit the ADR before building |
| `path` unreadable by a standalone x0xd | Fall back to `data_b64` (one spooled copy on the sender; measured in Spike 1) |
| x0xd `transfers_dir` grows | Spike 4 finds the cleanup call; if none, WP6 deletes what it can and the ADR documents the cost |
| Desktop WebP encoding needs a native dependency | Spike 3 checks Skia first; the dependency is desktop-only |
| A malicious member sends big or false files | Accept gate: roster sender, wanted hash, size must match the reference; hash re-verified; cap on the reference's `size` |
| Photos never arrive for members who never overlap with a holder | Accepted and documented (ADR "Honest limit"); the blur and caption still arrive |
| Document grows with blur strings | ~30 chars per photo, negligible against Automerge history |

## Suggested slices to commit

1. WP0 findings in the ADR. 2. WP1 (client + fake). 3. WP2 + WP3 (store, table, placeholder). 4. WP4 (ingest). 5. WP5 (model + UI). 6. WP6 (+ real-daemon test). 7. WP7-WP9. 8. WP10.
WP2/WP3/WP4 are independent of WP1 and can be done in parallel after WP0.
