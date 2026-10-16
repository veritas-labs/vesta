# Acceptance tests

Every feature ships with **acceptance tests** as well as unit tests. The acceptance tests say what "feature complete"
means: when they all pass, a user can do what the feature promises. Unit tests say the parts work; acceptance tests
say the parts add up to the feature.

## The rule

A feature is not done until:

1. its **acceptance criteria** are written down (in the feature's ADR, plan or design doc, or if it has none, in the
   acceptance test class's KDoc), as plain sentences from the user's point of view;
2. **every criterion has at least one acceptance test**; and
3. the acceptance tests pass alongside the unit tests (`./gradlew :shared:jvmTest`).

Start from the user's own example of why they want the feature, and turn it into scenarios before writing code.

## What an acceptance test is

- **One scenario per test**, named as a sentence about user-visible behaviour
  (`filteringToTheShopImStandingInShowsOnlyItemsPlannedForIt`), not after a function.
- **Given / When / Then.** Each test carries `// Arrange — Given …`, `// Act — When …` and `// Assert — Then …`
  comments (this satisfies the project's Arrange/Act/Assert rule too). Write the Given/When/Then as a sentence of
  the scenario, not a restatement of the code.
- **Driven like a user:** tap, type and read what's on screen, using the screens' test tags and visible text.
  Don't call view-model or document methods to make the thing under test happen, and don't assert on internals.
  Seeding the *Given* through the real source (`source.save(...)`) is fine.
- **The real stack, faked only at the edge:** the real Automerge-backed source, view model, screens and
  navigation between them. Fake only what is outside the app (the x0x mesh, the camera, the clock).
- **Persistence and sync where the feature has them:** at least one scenario reloads from the saved bytes (or syncs
  to a second device on the fake mesh) and checks the feature still holds.

## Where they live

`shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/acceptance/<Feature>AcceptanceTest.kt`, one class per feature, with
a KDoc naming the feature and listing its acceptance criteria. They run on the JVM with Compose's `runComposeUiTest`;
set `Dispatchers.setMain(Dispatchers.Default)` in `@BeforeTest` when a view model is involved (see
`DevicePairingFlowTest`).

The class has a small harness composable that wires the screens the way `App.kt` does. Reuse `App.kt`'s `internal`
mappers rather than copying them, so the test follows the app.

## Example

`ShoppingListTagsAcceptanceTest` (shopping-list tags) is the reference.

## Sync activity (ADR-033)

Criteria from [ADR-033](../adr/ADR-033-sync-activity.md). The screen criteria are driven through the real view model and screen over a fake
`SyncActivityRepository` in `SyncActivityAcceptanceTest`; the criteria that live in the sync service are backed by the service tests named below,
which run on the fake mesh and an in-memory database. Not covered by an automated test: a real x0x daemon producing the same events, and the
screen on a device.

1. **Up to date.** *Given* two other devices, one online and synced a minute ago and one offline, *when* I open Sync activity, *then* the card says
   "Up to date" naming the device, and both devices are listed with the offline one marked Offline.
   `SyncActivityAcceptanceTest.withTwoOtherDevicesOneSyncedAMinuteAgoTheScreenSaysUpToDateAndListsBoth`.
2. **Waiting for others.** *Given* nobody is online and an event from an hour ago, *when* I open Sync activity, *then* the card says "Waiting for
   others" and the earlier event is still in the log.
   `SyncActivityAcceptanceTest.withNobodyOnlineTheScreenSaysWaitingForOthersAndEarlierEventsRemain`.
3. **Only you.** *Given* a Home only on this device, *when* I open Sync activity and tap the Invite row, *then* the card says "Only you", there is
   no Devices list, and the invite flow is asked for.
   `SyncActivityAcceptanceTest.theOnlyMemberOfAHomeSeesOnlyYouAndAnInviteRow`.
4. **A received update is content-free and folds.** *Given* a Tasks update from another device, *when* I open Sync activity, *then* the log reads
   "Tasks updated by {device}" with its time and no task content, and repeats inside five minutes raise a count and not new rows.
   `SyncActivityAcceptanceTest.aReceivedTasksUpdateReadsAsTasksUpdatedByTheDeviceWithItsTimeAndNoContent` (the screen);
   `DeviceDataSyncServiceTest.aTasksEditIsLoggedAsSentOnTheEditorAndReceivedOnTheOtherMember` and `anUnchangedRoundIsOnlyAHandshakeAndAddsNoReceivedEvent`
   (recording on the fake mesh); `SyncEventRecorderTest` (collapsing; the table has no content columns).
5. **A failed send is a problem.** *Given* a send to a device that failed, *when* I choose the Problems filter, *then* only "Couldn't reach {device}"
   with "Will keep trying" is listed.
   `SyncActivityAcceptanceTest.aFailedSendAppearsUnderProblemsAndTheProblemsFilterShowsOnlyIt` (the screen);
   `DeviceDataSyncServiceTest.anOfflinePeerIsLoggedAsOneUnreachableRowThatCountsEachFailedRound` and `SyncActivityServiceTest.theFiltersKeepTheirKindsOnly`.
6. **Changes waiting.** *Given* a local change no peer or hub has taken, *when* I open Sync activity, *then* the card says "Changes waiting" until one
   takes it.
   `SyncActivityAcceptanceTest.changesWaitingShowsUntilAPeerOrHubTakesThem` (the screen); `ChangesWaitingTest` and
   `SyncActivityServiceTest.theVerdictIsChangesWaitingWhenTheyAreAwayAndNothingIsInTheMailbox` (how it is worked out).
7. **Last synced survives a restart.** *Given* two devices that have synced, *when* the app restarts on the same database, *then* the device still
   shows when it last synced and not "never".
   `DeviceDataSyncServiceTest.theTimeAPeerLastSyncedSurvivesAFreshServiceOnTheSameDatabase`; `SyncEventRecorderTest` (last synced is read from memory
   first and from the table by a new recorder).
8. **Seven days, and leaving removes the log.** *Given* rows older than seven days, *when* the next round starts, *then* they are gone; *when* I leave
   the Home, *then* its rows are gone too.
   `SyncEventRecorderTest` (prune removes rows older than seven days and keeps newer ones; forgetting a home removes its rows and buffered events);
   the "last 7 days" note on the screen is in `SyncActivityAcceptanceTest.aReceivedTasksUpdateReadsAsTasksUpdatedByTheDeviceWithItsTimeAndNoContent`.
9. **Sync now.** *Given* an up to date Home, *when* I tap Sync now, *then* one round is asked for and progress shows until it ends; *given* the
   daemon is down, *then* the card says sync isn't running and the button is disabled.
   `SyncActivityAcceptanceTest.syncNowRunsOneRoundAndShowsProgressUntilItEnds` and
   `withTheDaemonDownTheCardSaysSyncIsNotRunningAndSyncNowIsDisabled`; `SyncActivityServiceTest.syncNowStartsAnotherRound`.
10. **Recording never breaks syncing.** *Given* a recorder that fails every time, *when* a change is made, *then* it still reaches the other device.
    `DeviceDataSyncServiceTest.aRecorderThatFailsEveryTimeLeavesTheSyncUnchanged`; `SyncEventRecorderTest` (a closed database does not make recording fail).

## Pantry categories migration (ADR-010 amendment, ADR-034 WP3)

1. **My own categories survive the move to the new defaults.** *Given* a Pantry stored with the first default set plus a category I added, *when* the
   app opens it, *then* I see the new supermarket-aisle categories and my own, and the old defaults the new set dropped (Meat, Dairy) are gone.
   `PantryDefaultCategoriesAcceptanceTest.aPantryStoredWithTheOldCategoriesShowsTheNewDefaultsAndKeepsMyOwn`;
   `PantryCategoriesMigrationTest.migrationKeepsWhatTheHouseholdAddedAndRemovesOnlyDroppedDefaults`.
2. **A category added after the move is kept.** *Given* a Pantry that has been moved once and then given a category of mine, *when* the app reopens it,
   *then* my category is still there beside the defaults.
   `PantryDefaultCategoriesAcceptanceTest.aCategoryIAddAfterTheResetSurvivesReopeningThePantry`.
3. **Two devices that move before they sync lose nothing.** *Given* two devices with the same old Pantry, *when* one adds a category and both open
   the Pantry (so both move to the new defaults) and then sync, in either order, *then* both show the same categories, including the one added.
   `PantryCategoriesMigrationTest.aCustomCategoryAddedBeforeBothDevicesMigrateSurvivesTheMerge` and
   `theMergeIsTheSameWhicheverWayRoundAndHoldsTheCurrentDefaults`.
4. **Three devices converge.** *Given* three devices that each add something and then move, *when* they all sync in any order, *then* they agree and
   every addition is present.
   `PantryCategoriesMigrationTest.threeReplicasThatEachAddSomethingAndMigrateConvergeWithEverythingKept`.
5. **Opening again changes nothing.** *Given* a Pantry already moved, *when* the app opens it again, *then* nothing is written; *given* one that
   another device moved first, *when* this device opens it, *then* that device's later additions survive.
   `PantryCategoriesMigrationTest.migrationIsIdempotentAndASecondRunWritesNothing`, `aVersionTwoDocumentIsNotTouched`,
   `aCategoryAddedAfterOneDeviceMigratedSurvivesTheOtherDevicesMigration`, `migrationDoesNotReplaceTheCategoriesMap`.

### Input this version can't read (ADR-034, WP4)

Unknown input is still ignored, but now shows in Sync activity. Not covered by an automated test: a real daemon, and the screen on a device.

11. **A message with an unknown kind is noted.** *Given* a paired device sends a device-sync or Home-group message whose `kind` this version doesn't
    know, *when* I open Sync activity and choose Problems, *then* "{device} sent a message this version doesn't know" is listed with the kind and a count
    when it repeats, and nothing from the message's content is stored.
    `UnsupportedInputActivityTest.anUnknownDeviceSyncKindFromAPairedDeviceIsNotedOnItsHomes`, `anUnknownHomeGroupKindIsNotedOnlyOnTheHomeItNames` and
    `aRetryingPeerFoldsIntoOneRowAndNoPayloadContentIsStored`; `UnrecognisedKindTest`; the screen: `SyncActivityScreenTest.anUnsupportedVersionEntryNamesTheDeviceAndWhatItSent`.
12. **Other traffic is not a problem.** *Given* an agent command, another family's message, a known kind or text that isn't a message arrives, or
    a message from an unverified sender or a stranger, *when* I open Sync activity, *then* nothing is added.
    `UnsupportedInputActivityTest.messagesThatAreNotOfAKnownFamilyOrAreKnownAreNotNoted` and `anUnverifiedMessageOrOneFromAStrangerIsNotNoted`;
    `UnrecognisedKindTest.foreignOrMalformedPayloadsAreNeverUnrecognisedKinds`.
13. **A newer document or mailbox entry is noted and kept.** *Given* a device sends a sync message for a document type this version lacks, or leaves a
    mailbox entry or snapshot from a newer version, *when* I open Sync activity, *then* "{device} sent changes this version can't read" is listed with
    the document type or version; *when* Vesta is updated, *then* the mailbox entry is applied, because it was never deleted or passed over.
    `UnsupportedInputActivityTest.aSyncMessageForADocumentTypeThisVersionLacksIsNotedOnItsHome` and `aDocumentIdOfNoHomeOfOursOrFromAStrangerIsNotNoted`;
    `MailboxTest.anEntryFromANewerVersionIsNotedKeptAndAppliedOnceAReaderUnderstandsIt`, `entriesAfterANewerOneAreAppliedOnceAndNotAgainEachRoundWhileTheNotesFold`,
    `aDamagedEntryIsSkippedWithoutBeingNotedAsANewerVersion` and `aSnapshotFromANewerVersionIsNoted`.
14. **The card says a newer Vesta is needed.** *Given* changes this version can't read arrived in the last week, *when* I open Sync activity, *then* the
    card says "Some changes need a newer Vesta" (after a sync problem, before Up to date).
    `SyncVerdictTest.changesFromANewerVersionOutrankUpToDate` and `aProblemOutranksChangesFromANewerVersion`;
    `UnsupportedInputActivityTest.theUnsupportedVersionMakesTheVerdictSayANewerVersionIsNeededAndShowsUnderProblems`;
    the screen: `SyncActivityScreenTest.changesFromANewerVersionSaySoOnTheCard`.
15. **A mailbox entry for a document this version lacks is kept.** *Given* a device leaves a mailbox entry for a document this version can't open (a
    newer document type, or a Home not here yet), *when* this device reads the mailbox, *then* it is noted in Sync activity with the document type and the
    saved place stays before it; *when* Vesta is updated (or the Home arrives), *then* the entry is applied.
    `MailboxTest.anEntryForADocumentThisDeviceCannotOpenIsNotedKeptAndAppliedOnceItCan`.

## Document type registry (this plan)

Criteria for docs/plans/document-registry-and-source-collapse-plan.md, WP9. A throwaway `probe` document type, defined only in the test, is
registered through `HomeDocumentType` and handed to the engine with the app's other document types; the engine, mailbox, acknowledgement, Sync activity and
Changes waiting are not edited for it. All in `DocumentTypeRegistrationAcceptanceTest`.

1. **A new type syncs directly.** *Given* a Home on two devices with the `probe` type registered on both, *when* one device edits its probe document,
   *then* the other has the edit through a direct sync message.
   `aDocumentTypeTheEngineNeverHeardOfSyncsDirectlyBetweenTwoDevicesOfAHome`.
2. **A new type catches up through the mailbox.** *Given* a member was away, *when* the other edits and leaves before the first is back, *then* the
   member has the edit through the Home's mailbox.
   `aMemberWhoWasAwayCatchesUpOnTheNewDocumentTypeThroughTheMailbox`.
3. **A private type stays private.** *Given* a `Private` probe type, *when* I edit it, *then* it reaches my other paired device, and never another
   member's device nor the mailbox.
   `aPrivateDocumentTypeReachesTheUsersOtherDeviceButNeverAnotherMemberOrTheMailbox`.
4. **Sync activity and Changes waiting cover it.** *Given* my other device is away, *when* I edit a private probe document, *then* the Home shows
   changes waiting until the device is back; *when* it syncs, *then* Sync activity lists the sent and received events under the probe's module.
   `syncActivityShowsTheNewTypesEventsUnderItsModuleAndChangesWaitingFollowsItsUnacknowledgedEdit`.

## Kits extraction

Criteria for docs/plans/kits-extraction-implementation-plan.md, WP10. The extraction changes no behaviour, so these are compatibility checks: they were
captured from the code as it was before any rename, and the literals and the database file are never regenerated. All in
`shared/src/jvmTest/.../compat/` (the ids also in `DocumentIdsTest`).

1. **Wire messages keep their exact text.** *Given* a join, group, device-sync or blob message with fixed values, *when* it is encoded, *then* the JSON
   is the literal captured before the extraction (kind names such as `homejoin.invite` and fields such as `homeId`); *when* the literal is decoded,
   *then* it gives back the same message. `GoldenWireStringsTest` (one test per message family).
2. **Links keep their exact text.** *Given* an invite link and a pairing link with fixed values, *when* they are encoded, *then* the strings are the
   captured literals (`vesta://invite/...`, `vesta://pair/...`); *when* the literals are decoded, *then* the same links come back.
   `GoldenWireStringsTest.theInviteLinkEncodesToItsLiteralStringAndTheLiteralDecodesBack`, `thePairingLinkEncodesToItsLiteralStringWithAndWithoutADeviceName`.
3. **The sync envelope keeps its bytes.** *Given* one envelope for `home-1:pantry`, *when* it is encoded, *then* it is the length byte, the id, then the message;
   *when* those bytes are decoded, *then* the id and message come back. `GoldenWireStringsTest.theSyncEnvelopeIsTheLengthOfTheIdThenTheIdThenTheMessage`.
4. **Document ids and their order do not change.** *Given* a Home, *when* its documents are listed, *then* they are `<homeId>:roster`, `pantry`,
   `shopping-list`, `cookbook`, `merchants`, `mealplan`, `calendar`, `tasks`, `private-calendar`, `private-tasks` in that order, and the user documents are
   `user:homes` and `user:profile`. `DocumentIdsTest`.
5. **The schema keeps every name.** *Given* a new database, *when* its tables, columns, indexes and `user_version` are listed, *then* they equal the
   literals captured before the extraction (version 20). `SchemaGuardTest`.
6. **An existing install still opens.** *Given* a copy of a version-20 database written by the code before the extraction, with a row in every table, one Home
   with all ten documents, a roster with members, a device, a hub, an invite and a removed member, a pending `homejoin.left` message and a mailbox row, *when*
   it is opened through the app's open path, *then* it opens at version 20 and the Home, every document, the roster, the pending message and the mailbox
   row are all read back. `ExistingInstallStillOpensTest`. After a test run, `git diff --exit-code shared/src/jvmTest/resources/compat/vesta-v20.db` shows
   the fixture was not regenerated.
