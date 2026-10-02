# ADR-016: Choosing and changing your display name

**Status:** Accepted (2026-10-02). Implemented for the name only. Builds on [[ADR-014]] (a person's Profile is their member
id and name in every Home) and [[ADR-013]] (the Profile syncs between their devices).

## Context

A Profile was created silently on first use, named after the device ("fedora", "Pixel 8"), and that name is what other
people see in a Home ([[ADR-014]]). CORE-EXPERIENCE says a Profile is required before creating or joining a Home, with a
display name of 2 to 40 characters, and that the user can change it later.

## Decision

**A name is chosen once, before anything else.** `Profile.named` is false while the name is the device placeholder. While
it is false the app shows a single "What should we call you?" screen (the device name suggested, a live `n/40` counter,
an error once the user tries to save a bad name) instead of the Home picker. It has no way back. Existing Profiles
upgrade as unnamed, so people already using the app are asked once.

**Renaming** is Settings, Your name, which opens the same screen with a back arrow. `ProfileRepository.rename` validates
(2 to 40 characters once trimmed), updates the Profile and the user's `HomeMember` rows, sets `named`, and tells a
`ProfileChangeListener`. The sync service responds by publishing the Profile and every Home's roster, so the new name
reaches the user's other devices and every Home's other members through the existing syncs.

**A chosen name beats a placeholder across devices.** The shared Profile ([[ADR-014]] slice 2) is the oldest entry; it now
prefers an entry whose name the user chose, then the oldest, then the lowest id. Without that, a name chosen on a new
device before pairing would be overwritten by the older device's placeholder. The document records `named` per entry and
never takes it back, and a device doesn't let an incoming placeholder undo a name chosen here before it has published.

## Consequences

- Other people see a real name from the first moment, not a hostname.
- Two devices that each choose a name before pairing keep the older one; the other's name is lost.
- Avatar and theme choices from the CORE-EXPERIENCE spec are not built; the name is the only required field.
- There is still one Profile per device (no switching), and a person's other devices learn the name by syncing, so an
  unpaired second device asks for a name of its own.
- The gate is only checked by compiling and by its parts being tested (`ProfileRepository`, the screen, Settings, the
  sync). A UI test of the whole gate was dropped: composing `App` under a real-thread Main dispatcher races in
  `LifecycleRegistry` and failed about half the time.

## References

- `ProfileRepository`, `ProfileNameRules`, `ProfileChangeListener`, `ProfileNameScreen`, `ProfileRegistryDocument`;
  migration `9.sqm`; `DeviceDataSyncServiceTest` (a name reaching another device and a Home's members).
