# ADR-037: Space vocabulary in the kits

**Status:** Accepted (decided unattended by the team lead on the user's standing decisions: separate repos, Space, services only),
2026-10-16. Plan: [kits-extraction-implementation-plan.md](../plans/kits-extraction-implementation-plan.md) (WP11, Phase C).
Related: [ADR-036](ADR-036-kits-database-composition.md), [ADR-035](ADR-035-document-type-registry.md).

## Context

The kits are meant to serve apps other than Vesta, so their API cannot say "Home". But existing installs hold persisted data, and
peers on older builds exchange wire messages, that spell "home" in tables, columns, document ids, message kinds, JSON fields and
invite links. Renaming any of those breaks installs or sync.

## Decision

1. **"Space" replaces "Home" in kit Kotlin APIs.** `Home*` becomes `Space*`, `homeId` becomes `spaceId`, `homeName` becomes
   `spaceName`, `shape` becomes `kind` (an opaque `String` the app interprets). Examples: `HomeDocumentType` ->
   `SpaceDocumentType`, `HomeDocumentRegistry` -> `SpaceDocumentRegistry`, `HomeDocumentStore` -> `SpaceDocumentStore`,
   `HomeRepository` -> `SpaceRepository`, `HomeRosters` -> `SpaceRosters`, `HomeRegistry` -> `SpaceRegistry`,
   `HomeJoinService` -> `SpaceJoinService`, `HomeJoinMessage` -> `SpaceJoinMessage`, `HomeGroups` -> `SpaceGroups`,
   `HomeHubs` -> `SpaceHubs`, `HomeHub` -> `SpaceHub`, `HomeChangeListener` -> `SpaceChangeListener`, `InviteLink.homeId` ->
   `spaceId`.
2. **Every persisted and wire name stays byte-identical** (inventory below). Where a renamed Kotlin property is serialised it
   carries an explicit `@SerialName("homeId")` and the like; raw lookups such as `stringField(payload, "homeId")` keep the literal.
3. **Vesta keeps "home"** in its own code and UI (screens, view models, `PantrySources.forHome`, `AgentCommandService`, Compose
   resources). The edge is a thin adapter where a Vesta class calls a kit class with a `homeId` value.
4. **Just-in-time, per kit, before its extraction** (blobs WP17, docs WP19, spaces WP21, sync WP23), not as one repository-wide
   rename first. Each diff stays reviewable; x0x-kit has no Home vocabulary and can go first; every rename still lands before the
   kit's first publication, which is the real constraint. The tool is a checked-in script (`scripts/rename-space.sh`, a
   word-boundary `sed` over an explicit file list); compiling finds what it missed; `@SerialName` additions are by hand.
5. **Compatibility is tested before renaming** (WP10): golden wire strings, golden document ids, a committed version-20 database
   fixture opened through the production path (`ExistingInstallStillOpensTest`), and a `sqlite_master` table and column guard.

## Names that must not change

| Name today | Where | Treatment |
|---|---|---|
| Tables `Home`, `HomeMember`, `HomeGroup` | `.sq` | persisted; generated names (`homeQueries`, rows) are `internal` in `store-kit` |
| Column `homeId` (`AutomergeDocument`, `SyncEvent`, `InviteCode`, `AssistantGrant`, `AssistantActivity`, `MailboxWrite`, `MailboxSequence`, `MailboxRead`, `MailboxSnapshot`, `BlobWant`, `JoinRequest`, `PendingJoin`, `PendingMessage`, `HomeGroup`, `HomeMember`) | `.sq` | persisted, unchanged |
| `Home.shape`, `PendingJoin.homeName`, indexes `AutomergeDocument_homeId`, `SyncEvent_homeId_lastAt` | `.sq` | persisted, unchanged |
| Migrations `1.sqm`-`19.sqm`, snapshots `1.db`-`20.db`, `user_version` 20 | `sqldelight/` | immutable |
| Document ids `"$homeId:$feature"`; features `pantry`, `shopping-list`, `cookbook`, `merchants`, `mealplan`, `calendar`, `private-calendar`, `tasks`, `private-tasks`, `roster` | `AutomergeDocument.documentId`, `SyncEnvelope`, `MailboxWrite` | persisted and wire; kit `spaceDocumentId(spaceId, feature)` yields the same string |
| User document ids `user:homes`, `user:profile` | `UserDocument`, `SyncEnvelope` | persisted and wire; constant renamed `SpaceRegistryDocumentId`, value `"user:homes"` with a comment saying why |
| Roster and registry document keys (`member:`, `device:`, `devicename:`, `hub`, `hub:`, `gone:`, `invite:`, `usedBy:`, `name`, `avatar`, `role`, `joinedAt`, `removed`, `group`, `createdBy`, `expiresAt`, `maxUses`, `revoked`; registry `name`, `shape`, `createdAt`, `deleted`) | `HomeRosterDocument.kt`, `HomeRegistryDocument.kt` | persisted and wire (inside Automerge), unchanged |
| Message kinds `homejoin.invite`, `.accepted`, `.left`, `.removed`, `.ack`, `.request`, `.denied`; `homegroup.invite`, `homegroup.invite.request`; discriminator `kind` | `HomeJoinMessage.kt`, `HomeGroupMessage.kt` | wire and persisted (`PendingMessage.payload`), unchanged |
| JSON fields `homeId`, `homeName`, `shape`, `createdAt`, `inviterMemberId`, `inviterName`, `secret`, `memberId`, `name`, `agentId`, `of`, `reason`, `groupId`, `invite`; `blob.want {homeId, hash}` | the messages, `BlobMessage` | wire; `@SerialName` keeps the literal |
| `PendingMessage.kind` values `left`, `removed` | `HomeJoinMessage` | persisted, unchanged |
| Invite link `vesta://invite/<base64url("homeId:secret:expiresAt:agents:homeName")>` (positional, limit 5) | `InviteLink.kt` | wire; scheme from config, order and separator unchanged, Kotlin fields renamed |
| Pairing link `vesta://pair/<token>` | `PairingLink.kt` | wire, unchanged |
| x0x group names `Vesta Home`, `DeviceSync`; group store name `mailbox` | `HomeGroups.kt`, `DeviceSyncService.kt` | wire; become config defaults (WP3) |
| Sync activity module ids `pantry`, `shopping`, `cookbook`, `mealplan`, `calendar`, `tasks`, `merchants`, `members`, `mailbox`, and event kinds and detail codes | `SyncEvent` | persisted, unchanged (Vesta ones move to `VestaSyncModules`, WP6) |
| Agent protocol `urn:vesta:homes`, `urn:vesta:home:<id>`, `urn:vesta:document:<homeId>...`, `@context`, reply strings, capability names `ListHomes`, `PantryAdd` in `AssistantGrant.capability`, policy names `Ask`/`Auto`, `skills/vesta` | agent files | wire and persisted; Vesta-owned, never moves to a kit |
| `AppSetting` keys; `vesta.db`, `~/.vesta/`, `.vesta/blobs`, `X0X_DATA_DIR`, `VESTA_AGENT_ALLOWLIST`, iCal `@vesta` and `PRODID` | various | Vesta-owned, unchanged |
| JNI symbols `Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` | `ffi/x0x-ffi/src/lib.rs` | not a Home name but the same hazard; changed with the package in WP13, guarded by a test |
| Vesta UI strings and Compose resource keys that say "Home" | `composeResources`, screens | unchanged |

## Consequences

- Kit APIs read neutrally; existing installs and older peers keep working with no migration.
- Kit code has two vocabularies at the boundary (Kotlin `spaceId`, persisted `homeId`); the `@SerialName` annotations and this
  table are the record of why, and the WP10 tests fail if a literal changes.
- Vesta maintains a thin Home-to-Space adapter layer. A future rename of the persisted names would need its own migration ADR.
