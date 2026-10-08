# ADR-023: Storing and transferring blobs (photos first)

**Status:** Proposed (2026-10-08). Nothing is built. Spikes 1-4 below must run before the transfer design is final.
Closes the gap [[ADR-022]] left ("blob storage doesn't exist in the sync layer yet"). Builds on [[ADR-005]], [[ADR-012]],
[[ADR-013]] and [[ADR-017]]. Relates to [[ADR-020]]: its hub does **not** carry blobs (see "Why not the hub").

## Context

Documents (Automerge, [[ADR-005]]) sync between devices, and through the hub's group-store mailbox ([[ADR-020]]).
Documents are the wrong place for megabytes, and the first user is the Cookbook's photos ([[ADR-022]], whose media are
references only). Pantry item photos and others are likely to follow; **video must be possible later without redoing this**.

Requirements, from the decisions of 2026-10-08:

- **Photos now, video later.** The model must not assume "small".
- **Eager.** As soon as a device sees a reference to a blob it does not hold, it tries to get it. It does not wait to be viewed.
- **Native x0x file transfer** rather than a hand-rolled protocol.
- **Optimise hard for size**: WebP, downscaled.
- A placeholder for a blob that hasn't arrived must come **with the document**, not as a second transfer.

### What x0x gives us (read from x0x source, `src/files`, `src/server/routes/files.rs`, `src/kv`)

**Native file transfer (`/files/*`)**
- **Point to point.** `POST /files/send` pushes one file to one named agent. There is no "fetch hash X from whoever has it".
- **Offer, then accept.** The sender sends an *offer* by DM. The receiver's daemon records it as `Pending` and emits an SSE
  `file:offer`. Nothing flows until something calls `POST /files/accept/{id}`. The only automatic refusal is for blocked contacts
  and sizes over 1 GiB. There is no auto-accept setting.
- **Transfer.** 32 KiB chunks, each acknowledged after the receiver persists it, with SHA-256 checked at the end.
- **Landing place.** The file lands in the receiving daemon's `transfers_dir` as `<filename>`: a flat namespace.
- **Source.** The sender gives either `data_b64` (the daemon spools a copy) or a local `path` the daemon reads.
- **One shot.** If the offer can't be delivered the transfer is marked `Failed` and not retried. Retrying is the caller's job, as in [[ADR-017]].

### Why not the hub (considered and rejected 2026-10-08)

The hub ([[ADR-020]]) is an app-agnostic x0xd that cannot call `accept`, so native transfer can't reach it. The only app-agnostic
bulk path through it is a group store, and that does not work for photos on phones:

- A store has **no remote read**: to read an entry a phone must join the store, and gossip then replicates the whole store to it.
- There is **no leave** (routes are create, join, keys, get, put, delete-key), and in a group store a delete propagates to everyone,
  so a phone can't shed a replica.
- A store keeps its entries in an **in-memory map** (snapshot-persisted). A phone replicating a photo store would hold every photo
  in RAM in its embedded daemon, and on disk twice (the replica and our own blob store).

A Vesta-specific blob service beside the hub would work but breaks ADR-020's "the hub knows nothing about Vesta" rule, and
upstream x0x features that would fix this (auto-accept for trusted contacts, hash-addressed fetch) are not expected soon.
So **originals do not get store-and-forward**. Revisit if x0x gains those features.

## Decision

### 1. A blob is content-addressed bytes, referenced from a document

Documents hold a reference, never the bytes. This replaces the Cookbook's `uri`:

```
{ "hash": <sha256 hex of the bytes>, "size": Int, "mime": "image/webp", "kind": PHOTO | VIDEO,
  "w": Int, "h": Int, "blur": <placeholder hash string>, "caption": Str }
```

- The **hash is the identity**. Two devices adding the same bytes make the same reference, so nothing conflicts and a
  transfer is idempotent. A received blob is **always re-hashed** and discarded if it does not match the reference.
- `blur` is a ~30-character **placeholder hash** (BlurHash, or ThumbHash if Spike 3 prefers it) computed at ingest. It is part of the
  document, so it reaches every member exactly when the reference does, even if the bytes never do. With `w` and `h` it lets a
  tile render at the right shape and colour immediately.
- `mime` and `kind` leave room for video later: a video is another blob whose `blur` comes from its poster frame. Storage and
  transfer never look at `kind`.
- Existing `uri` media (a link or device path) stay readable and show as today. They are never transferred.

### 2. Local storage: one content-addressed store per device

- Files in the app's data directory, `blobs/<aa>/<sha256>`, written to a temp name and renamed so a partial file is never read as complete.
- A small SQLite table (ADR-007's database) records each blob's size, mime and last-referenced time, and the *want list* (hashes referenced but missing).
- **One store per device, shared by all Homes on it**, so the same photo in two Homes is stored once. See "Scope and authorisation".

### 3. Ingest: downscale and re-encode once, at the source

- Decode, apply EXIF orientation, **strip all metadata (GPS included)**, resize so the long edge is at most ~1600 px, and encode
  **WebP (lossy)** at a quality chosen by measurement (starting point 70-75).
- Compute the placeholder hash from the downscaled image.
- Inputs over a sane limit (proposed 30 MB) or that fail to decode are refused with a message.
- The encoder is per platform: Android `Bitmap.compress(WEBP_LOSSY)`; desktop most likely Skia (`EncodedImageFormat.WEBP`,
  already in Compose Desktop), since `ImageIO` has no WebP writer. Confirm in Spike 3.
- Only the encoded result is kept or sent. The original is never stored.

### 4. Transfer: native x0x file transfer, eager, app-gated

The unit is "a hash a Home document references that this device doesn't have". A **reconciler** runs after every document
change, on app open, on a peer's presence transition and each sync round. It adds missing hashes to the want list and tries to satisfy them.

- **Push on ingest.** The author calls `files/send` to each online member device of the Home (and its own paired devices), with
  `filename = <hash>.webp` and the blob's `path` (so the daemon doesn't spool a second copy).
- **Pull by request.** A device that wants a hash sends a `blob-want {hash}` DM to the reachable Home members and its own
  paired devices. **Any** peer that holds the blob replies with `files/send`, so a device that was offline when the photo was
  added gets it from whichever holder it next meets, not only the author. Requests are repeated with the [[ADR-017]] backoff.
- **Receiving is app-driven.** Vesta listens for `file:offer` and calls `accept` **only** when the sender is a roster/member
  agent *and* the offer's `sha256` is on the want list *and* its size matches the reference. Every other offer is left pending
  and rejected after a timeout. This is the in-app gate x0x's missing auto-accept needs, as [[ADR-009]] did for commands.
- **Finish.** x0x's `transfers_dir` is flat and daemon-owned, so the app moves the finished file into its own store immediately,
  after re-hashing. The `<hash>` filename avoids collisions. Failed offers are retried by Vesta.
- **Nothing available.** The hash stays on the want list and the UI shows a tile with the placeholder blur, `w`×`h` shape and a
  "waiting for photo" cue. No error, no blocking.

**Honest limit.** There is no store-and-forward: a photo reaches a device only while some device holding it and the wanting device are
online together. The document, caption and blur reach everyone as before (including through the hub), but two members who are
never online with a holder see a blur until they are. This is the same overlap limit documents had before [[ADR-020]]. It is
accepted for now and revisited if x0x gains hub-friendly transfer.

### 5. Mobile data and ordering

- Eager, as required. The existing "use mobile data" setting applies: with it off, the reconciler neither requests nor accepts
  blobs on mobile data, and the want list is shown as "waiting for Wi-Fi".
- Order: newest-referenced first, so a recently added recipe fills in before old ones.
- Video, when added, reuses every part of this. Expected changes: a per-kind size ceiling and probably "Wi-Fi only" above a size,
  which is why `kind` and `size` are in the reference from the start.

### 6. Scope and authorisation: per device locally, per Home on the wire

- **Storage dedup is per device**, shared across Homes (section 2). A user's paired devices ([[ADR-012]]) are also valid `blob-want` sources.
- **Transfer is bounded by the Home.** A photo in a Home's document must reach the *other members*, who belong to different device
  sets, so the unit of authorisation is the Home's roster, not the device set. A device only requests or serves a hash because a
  Home document it can read references it, and only to or from agents on that Home's roster.
- A blob used by two Homes is served independently to each. Hashes are never cross-linked, so a hash seen in one Home tells another nothing.

### 7. Privacy and at-rest

- In transit: x0x DMs and file transfer are end-to-end encrypted.
- At rest on a device: plaintext in the app's private directory, as the SQLite database is today. The hub holds no blobs.
- Metadata is stripped at ingest, which removes the main accidental-disclosure risk (location in photos).
- The placeholder hash is derived from the image and is visible to anyone who can read the document, like the caption.

### 8. Local garbage collection

A blob is deletable locally when no document on this device references it. Because a concurrent edit or undo can re-reference it,
deletion waits a grace period (proposed 7 days) and is skipped if a reference reappears.

## Spikes to run first

1. **Native transfer end to end between two embedded daemons**: offer → app-gated accept → file in `transfers_dir` → moved and verified,
   including what an offer to an offline peer does and how it surfaces, whether `path` works when the daemon runs as a different
   user (desktop standalone x0xd), and whether an Android daemon sees `file:offer` while the app is backgrounded.
2. **Want/serve flow with three devices**: author, a second device that was offline, a third that never saw the author online,
   to confirm "any holder serves" works and the backoff converges.
3. **Encoder and placeholder size/quality** on Android and desktop: bytes per photo at long edge 1600 / q70-75; BlurHash vs ThumbHash
   on a few real photos (length, look, decode cost), so the numbers above are measured, not guessed.
4. **Daemon disk hygiene**: confirm the `.part`/final files don't accumulate in `transfers_dir` once the app has moved them, and what to
   call to clear them.

### Spike 1 findings (2026-10-08, x0xd 0.46.0, two standalone desktop daemons `spike-a`/`spike-b`, same user, raw HTTP)

Not yet covered: a daemon of another user, and an Android emulator with the app backgrounded (both still open).

- **Offer:** `POST /files/send` returns `{ok, transfer_id}` at once. The receiver gets `file:offer` on **`/events`** as `{type, data:{filename, sender, size, transfer_id}}`
  (no sha256 in the frame; it is in `GET /files/transfers/{id}`). Nothing moves until `POST /files/accept/{id}`; the receiver stays `Pending`.
  The contact needs to be trusted on both sides for the test to run (`POST /contacts` with `trust_level: trusted`); `agents/connect` returned `NotFound` but the send worked anyway.
- **Accept:** transfer runs in 32 KiB chunks. 200 kB took ~25 s on loopback (first chunk after ~4 s, slow ramp), so UI must treat transfers as slow and background.
- **Complete:** `file:complete` on `/events` carries `{filename, path, sha256, transfer_id}`. **The landed file is `transfers/<first 8 of transfer_id>_<filename>`**,
  not `<filename>`. This contradicts plan decision 4: take the file from `path` in the frame and never assume the name; the hash we want is still readable from the offer filename.
- **`path` vs `data_b64`:** both work. With `path` the daemon reads the source in place (`source_path` is the original, nothing copied). With `data_b64` the daemon writes
  `transfers/<id>.send` on the sender and **never deletes it** (a second copy per photo). Plan decision 3 (send by `path`) is confirmed. The daemon does not check `path` or `size` at send time (a nonsense path/size still returns `ok`).
- **Integrity:** the receiver checks the sha256 given at send time against what arrived. A wrong claimed sha gave receiver `Failed` with
  `SHA-256 mismatch: expected ... got ...` and **no file left behind**; the **sender still showed `Complete`** and **no `file:failed` frame appeared on `/events`**. So the receiver can only learn of a failure by polling `/files/transfers`,
  and a sender cannot know delivery succeeded. Our own re-hash stays the authority.
- **Reject:** `POST /files/reject/{id}` gives `Rejected` / `"rejected by user"` on both sides; no file.
- **Offer to a stopped daemon:** send returns `ok`, sender stays `Pending` with no error. After the receiver restarted (45 s and again 60 s later) **the offer was never delivered**,
  the sender stayed `Pending`, and the receiver's `/files/transfers` was **empty (transfers do not survive a daemon restart, even completed ones)**.
  Consequence: a Pending send is not a promise. The want/serve loop must treat any transfer that is not `Complete` within a timeout as lost and ask again; the want list, not the daemon, is the source of truth.
- **Hygiene (early answer to Spike 4):** completed files stay in `transfers/` until the app moves them; failed ones leave nothing. No delete endpoint for a finished transfer was seen (only `send/accept/reject/transfers`), so `data_b64` sends will leak `.send` files; use `path`.

**Plan impact:** WP1 must (a) read the file from `file:complete.path`, (b) poll `GET /files/transfers/{id}` for `Failed`, (c) retry on timeout instead of trusting `Pending`, (d) send by `path` only.
Exit rule check: the embedded daemon surfacing `file:offer`/accept is **not** disproved on desktop; the Android-backgrounded question still gates the ADR.

### Spike 2 findings (2026-10-08, three standalone desktop daemons `spike-a`/`b`/`c`, same user, raw HTTP)

Run: B stopped; A sent a 150 kB file to C (offer to accepted 7 s, complete 10 s); A stopped; B started; B sent C a `blob-want` direct message;
C sent the file to B by `path` from its own `transfers/`; B accepted. B's re-hash matched the wanted hash.

- **"Any holder serves" works**: B got the bytes from C with A (the author) down. The want DM was delivered (`trust_decision: accept`, `verified: true`) and C could serve with no
  involvement from A. The serving daemon sends from `transfers/<id8>_<hash>.webp` in place; no copy is needed (the app's own blob store is the better source in production).
- **Latency:** serve to offer on B 2 s, accept to complete 2 s. The want DM itself was not timed precisely (my grep for `blob-want` could not see inside the base64 payload), only bounded
  at under 40 s. Not a worry for a 30 s reconciler round, but the real reconciler test should time it.
- **Correction to Spike 1:** the receiver's `transfers/` **files survive a daemon restart** (B still held the Spike 1 files); only the in-memory transfer *list* is lost. A restarted device therefore
  still has files it never moved, so the receiver should sweep `transfers/` for unclaimed hash-named files at start-up, not only rely on `file:complete`.
- **Mutual trust:** the serving device must trust the asker (and vice versa for the offer to be shown). In the app this is already true for Home roster devices and paired devices
  (the `peersFor(documentId)` set), but a want from an untrusted agent must be ignored.
- **Not tested**: a third device that has genuinely *never* seen the author online (C here was sent the file by A directly, then served B), the backoff/convergence loop (it is app code, WP7),
  and many simultaneous askers. Those belong in the fake-mesh tests and the real-daemon test of the reconciler.

### Spike 3 findings (2026-10-08, partial: desktop only, no real phone photos available)

**Limits of this run.** The machine holds no phone photos. The corpus was 11 images: 8 generated illustrations/screenshots (1.0-1.8 MP), a printer sample photo (1780x2728), one
already-downscaled portrait from a chat app (862x1280), and a darkened (x0.15) copy of the sample as the "dark" case. No Android run (no device/emulator used). Treat the byte counts as indicative for
*photographic* content only through the two photo-like files; generated art compresses differently. **Re-run on ~10 real phone photos before freezing the numbers.**

- **Skia WebP on desktop works.** `org.jetbrains.skia.Image.encodeToData(EncodedImageFormat.WEBP, q)` (skiko 0.144.6) returned data, 44-91 ms per image, and matches libwebp via Pillow within ~2% on size.
  So no WebP encoder dependency is needed on desktop (the ADR's open question at line 81). Needs `kotlinx-coroutines` on the classpath, which the app already has.
- **Bytes at long edge 1600 (no upscaling):** mean over 11 images q70 **138 KB**, q75 **147 KB**, q80 **180 KB**. Per image q75: 232, 203, 175, 156, 210, 213, 136, 78, 157 (the sample photo), 26 (chat-app portrait), 29 (dark).
  JPEG q85 of the same was 2-3x larger (mean ~340 KB). q80 costs ~22% more than q75 for q70-to-q75's ~6%; the knee is at 75.
- **Placeholder:** BlurHash 4x3 is always **28 chars** and decodes in ~0.1 ms; ThumbHash was **25-28 bytes** (~34-38 base64 chars), encodes in ~11-17 ms (pure-Python, will be far less on JVM) and decodes in ~2-3 ms.
  ThumbHash carries the aspect ratio itself and has lower mean error on 8 of 11 images (e.g. 25.5 vs 34.5 on the portrait, 37.0 vs 50.2 on the sample photo; BlurHash won only on the flat illustration and dark images). Both are fine to store in a document.
- **Provisional decisions (need the real-photo re-run to confirm):** WebP lossy **q75**, long edge **1600**, no upscaling, **ThumbHash** (`t:` prefix, base64), expected blob size 30-250 KB, so the document-side `blur` is ~40 chars.
- **Input cap:** not measured. Decoding is the risk, not the file: a 48 MP source decoded fully is ~190 MB of RGBA. Plan: decode with a sample-size bound on Android (`inSampleSize`) and cap input at ~50 MP / 25 MB, to be checked on a device.
- **Still to do:** Android `Bitmap.compress(WEBP_LOSSY)` sizes and time on a mid-range phone/emulator, EXIF orientation of a real portrait shot, and the 10 real photos.

### Spike 4 findings (2026-10-08, x0xd 0.46.0, `spike-a`/`spike-b`, desktop, same user)

- **Files in `transfers/`:** while receiving, the daemon writes `<full transfer_id>.part` (growing; 196 MB seen mid-way on a 400 MB send). On completion it is renamed to
  `<id8>_<filename>` (Spike 1). On a SHA mismatch the `.part` is removed (Spike 1: nothing left). On the sender, `path` sends leave nothing; `data_b64` sends leave `<id>.send` forever.
- **The daemon never deletes a finished file.** Moving the file away (our `importFile` step) is harmless: the transfer stays `Complete` with the now-dangling `output_path`, no error, no events.
  So the app's move is the cleanup for good transfers, as the plan assumes.
- **No API to delete a transfer or its record:** `DELETE /files/transfers/{id}` is 405 and every other probe (`/files/delete/{id}`, `/files/clear`, POST variants) returned no body; the CLI has only
  `send-file`, `receive-file`, `transfers`, `transfer-status`, `accept-file`, `reject-file`. Nothing in the binary mentions retention, TTL or cleanup. Transfer *records* are in memory only and vanish on restart.
- **Interrupted receives leave orphans that nothing cleans up.** Killing the receiver (kill -9) mid-transfer left the `.part` (199 MB), and it was still there after the daemon restarted, which listed no transfers.
  There is no resume: the sender stayed `InProgress` indefinitely (no error), and a retry makes a new transfer id and a new `.part`. A clean `x0x stop` was not tested.
- **Disk growth:** with moves, the steady state is zero (blob store holds one copy). Without them every received photo would sit in `transfers/` for ever. The risk is orphans: phone killed mid-photo (Android does this routinely), each costing up to a photo's size
  and never reclaimed, plus `.send` files if `data_b64` is ever used.

**Plan impact (WP1/WP2/WP7):**
1. Always send by `path`; never `data_b64`.
2. On `file:complete` verify, move into the blob store, then delete whatever remains. Never leave the daemon's copy.
3. **Add a `transfers/` janitor** run at app start and each reconciler round: delete `*.part` files whose transfer id is not in `GET /files/transfers` and whose mtime is older than a short bound (e.g. 15 min),
   and adopt-or-delete finished `<id8>_<hash>.webp` files not yet claimed (Spike 2: those survive restarts). The directory is the daemon's `transfers_dir` (flat, in its data dir), which the app must be told on both platforms.
4. A sender cannot tell a lost transfer from a slow one; it should not hold state on a sent transfer beyond the want-list's own retry/backoff.
5. Open: clean-shutdown behaviour of a `.part`, and whether an Android embedded daemon uses an app-private `transfers_dir` the janitor can reach (check in WP1).

## Consequences

- Cookbook media change from `uri` to a blob reference with an inline placeholder. Existing `uri` entries stay valid.
- The hub is unchanged and carries no photos. Documents (including the placeholder) still go through it.
- A second "who may I trust" path (file offers) reuses the Home roster and the want list, so an unsolicited offer is never accepted.
- Phones carry a want list and a reconciler that must tolerate being killed at any point: every step is resumable and
  hash-verified, nothing is "half-applied".
- No blob bytes live in any x0x store, so nothing is held in a daemon's memory or duplicated on disk.
- Video will need real work later (decode/encode, streaming playback, size policy) but not a new storage or transfer model.

## Acceptance criteria

1. I can add a photo to a recipe from the device; the app stores it as WebP no larger than ~1600 px on the long edge with no location metadata.
2. The photo appears on a paired device or another member's device that is online at the same time, without any manual step.
3. A member's device that was **offline** when the photo was added gets it, once it is online with any device that holds it
   (not only the author).
4. Before the photo arrives, the recipe shows a blurred placeholder of the right shape; the placeholder came with the recipe document.
5. A blob whose bytes don't match the reference's hash is discarded and the hash stays wanted.
6. An unsolicited file offer, one from a non-member, or one whose hash isn't wanted is never accepted.
7. With "use mobile data" off, blobs are not fetched on mobile data and the UI says they are waiting.
8. Two recipes (or two Homes) with the same photo store it once on the device.
9. Deleting the last reference to a photo eventually removes it from the device, but not within the grace period.
10. A reference whose bytes haven't arrived never crashes or blocks the screen.
11. Interrupting a transfer (kill the app) and restarting resumes and completes without a corrupt blob.

## Not in this ADR

- Store-and-forward of blob bytes through the hub (needs x0x auto-accept / hash fetch, or a Vesta-aware hub service; both rejected for now).
- Video capture, encoding, playback and its size policy.
- Sealed blobs for an untrusted hub ([[ADR-020]] step 3).
- Non-image attachments (PDF receipts and the like): the storage and transfer model fits them, the ingest step doesn't.
