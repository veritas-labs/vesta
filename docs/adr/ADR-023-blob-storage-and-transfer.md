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
