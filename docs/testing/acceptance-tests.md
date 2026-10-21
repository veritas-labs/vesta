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
7. **An invite link made by the old build opens on the new one.** *Given* the invite link literal captured before the extraction, *when* the new build
   decodes it, *then* it names the same Home, inviter and expiry (criterion 2; the literal is never regenerated).
8. **The kits are not listed as third parties.** *Given* the in-app open source licence list, *when* it is regenerated with `./gradlew generateLicenses`,
   *then* no entry has a name starting `uk.veritaslabs.kits`, and the kits' own dependencies (SQLDelight, Automerge, the androidx driver) are still listed.
   Manual: run the task and `git diff` the list; it should change only when a dependency changed.
9. **The kits build from git alone.** *Given* a fresh clone of veritas-kits, *when* `./gradlew build apiCheck publishToMavenLocal` runs, *then* it passes and
   Vesta's Gate V passes against the artifacts it published. Manual, at each release (veritas-kits README, "Release checklist").
10. **Old and new builds sync (manual two-version check).** *Given* one device on a build from before the extraction and one on the new build, in the same
    Home, *when* either edits the Pantry (and a Task), *then* the other receives the edit, and Sync activity on both lists the event under the same module.
    Manual because the automated tests run one version; not yet run (needs two devices, Gate D).
11. **A real user database opens.** *Given* a copy of a real `vesta.db` from before the extraction (not the fixture), *when* the new build opens the copy,
    *then* every Home, member, document and pending message is intact. Manual; not yet run.

## Morning digest (ADR-040)

1. **Today's tasks with no time are listed.** *Given* open tasks due today, one with no time and one at 09:00, one with no date, one due tomorrow, *when* the
   digest is built, *then* only the no-time task due today is in it. `MorningDigestTest.aTaskDueTodayWithNoTimeIsIncludedAndOneWithATimeIsNot`,
   `tasksDueOnOtherDaysAndTasksWithNoDateAreNotIncluded`.
2. **A done occurrence is left out; a repeating chore's next one is not.** `MorningDigestTest.aDoneOccurrenceIsLeftOutButTheNextOccurrenceOfARepeatingChoreStillCounts`.
3. **Only my tasks.** *Given* a task assigned to someone else, one to me, one to anyone and a private one, *then* all but the first are listed.
   `MorningDigestTest.aTaskAssignedToSomeoneElseIsLeftOutAndOneForMeOrAnyoneIsIncluded`.
4. **Read with no screen open.** *Given* Home and private tasks in the database, *when* the digest is read, *then* both lists are included with their Home's
   name, a Home I am not in adds nothing, and no profile gives nothing. `MorningDigestReaderTest`.
5. **Due from 07:00, once a day, at the next 07:00.** `MorningDigestTest.theDigestIsDueFromSevenAndOnlyOncePerDay`, `theNextDigestIsTodaysSevenIfAheadAndTomorrowsIfNot`,
   `theNextDigestFollowsTheDeviceOffset`.
6. **Manual, on a real Android device (not yet run).** *Given* a task due today with no time and the permission granted, *when* the device sleeps through
   07:00 and is then picked up, *then* one notification lists the task and the screen was not woken at 07:00; it does not appear a second time that day;
   after a reboot past 07:00 it appears once; a private task's title is not on the locked screen.
7. **A tap opens that Home's Tasks.** `TasksDeepLinkTest` (the request is held until its Home consumes it). Manual, on a real Android device (not yet run):
   *Given* the digest is showing, *when* I tap it, *then* Vesta opens in the Home of the first task listed, on its Tasks, whether the app was closed or already open.
8. **Desktop shows it once, after 07:00, while running.** `MorningDigestRunnerTest` (nothing before 07:00; once after, and the day remembered; an empty day is not
   retried; yesterday's does not stop today's). Manual, in the desktop app (not yet run): *Given* a task due today with no time, *when* Vesta is running at
   or after 07:00, *then* one tray notice lists it; clicking it raises the window on that Home's Tasks; it does not repeat that day or after a restart.

## Marketplace location and search area (ADR-039)

Criteria from [ADR-039](../adr/ADR-039-home-location-h3.md), as built for the Marketplace's Nearby merchants. The flow is driven through the real
screens over a fake `LocalityPackSource` in `MarketplaceAreaFlowTest` and through the view model in `MarketplaceAreaViewModelTest`; the network
criteria run the real `AutonomiLocalityPackSource` over a recording client; the sync criterion runs two real registries. Backing tests are in
`MarketplaceAreaAcceptanceTest` unless another class is named. Status of each is stated: **covered**, **partial** (what is missing is said), or
**not built yet**. Not covered by any automated test: the flow on a device, and a real Autonomi network.

1. **No request without a location. Covered.** *Given* a Home with no location and nothing downloaded, *when* I open Nearby merchants and choose Not now,
   *then* no request of any kind reaches the network and the Home reads as it did before.
   `MarketplaceAreaAcceptanceTest.withNoLocationSetAndNotNowNothingIsRequestedFromTheNetwork`;
   `RegistryHomeLocationRepositoryTest.aHomeWithNoLocationAndAnAbsentSpaceReadAsNull`;
   `MarketplaceAreaViewModelTest.notCachedShowsTheDisclosureAndLoadsNothingUntilTheUserAgrees`.
2. **Creating a Home asks nothing; I choose my country and search by name. Partial.** *Given* I am creating a Home, *then* no location, area, nearby, address
   or postcode wording appears; *given* I open the Marketplace for the first time, *then* my country is preselected from the device region and I search
   by typing a name.
   `MarketplaceNearbyMerchantsNavigationTest.creatingAHomeNeverMentionsLocation`;
   `MarketplaceAreaFlowTest.continueShowsTheCountryListWithTheRegionPreselected`, `typingShowsResultsWithTheirChainsAndNothingBeforeThat`.
   Missing: no test asserts that the flow never asks the device for a position; it has no such code path, which is checked by reading, not by a test.
3. **Results show their parent chain. Covered.** *Given* two places called Newport, *when* I search "newport", *then* each result shows its chain
   so they can be told apart. `MarketplaceAreaViewModelTest.searchResultsCarryTheirChainText`;
   `MarketplaceAreaFlowTest.typingShowsResultsWithTheirChainsAndNothingBeforeThat`.
4. **Told first, and I can decline. Partial.** *Given* the names table is not on the device, *when* I open Nearby merchants, *then* I am told before any
   request, and *when* I choose Not now, *then* nothing is sent; *when* I agree, *then* the only requests are reads of fixed chunk addresses.
   `MarketplaceAreaFlowTest.theDisclosureComesFirstAndNotNowLeavesWithoutLoading`; `MarketplaceAreaViewModelTest.decliningNeverLoads`,
   `notCachedShowsTheDisclosureAndLoadsNothingUntilTheUserAgrees`; `MarketplaceAreaAcceptanceTest.withNoLocationSetAndNotNowNothingIsRequestedFromTheNetwork`,
   `onlyChunkReadsByAddressAreMadeAfterAgreeingAndATypedQueryIsNeverSent`. Missing: no test reads the wording to check it says who can see the
   request and that my IP address is visible; there is no index shard to disclose yet (only the names table is fetched).
5. **Choice before saving. Partial.** *Given* I chose Newport, *then* I see it and its neighbours, Newport ticked and locked, neighbours unticked
   nearest first, at most ten until I ask for more, and nothing is saved until I confirm.
   `MarketplaceAreaViewModelTest.choosingAHomeShowsNearbyWithTheHomeCountedAndNeighboursUnticked`, `theHomeLocalityCannotBeToggled`,
   `onlyTheNearestTenAreShownUntilShowMore`, `confirmSavesTheHomeThenTheAreaAndShowsSaved`;
   `MarketplaceAreaFlowTest.choosingAHomeShowsTheNearbyListWithTheHomeLocked`, `showMoreAppearsWhenThereAreMoreRowsAndRevealsThem`.
   Not built yet: the merchant count per neighbour and the approximate download size (the list deliberately shows neither until the PII review).
6. **What is saved. Covered.** *Given* I confirm Newport with two neighbours, *then* the Home holds the country, the locality id, the release and
   the ticked localities, no cell, and no attribute is a position.
   `MarketplaceAreaAcceptanceTest.whatIsSavedIsTheCountryTheLocalityTheReleaseAndTheAreaAndNoPosition`;
   `RegistryHomeLocationRepositoryTest.aLocationRoundTrips`.
7. **A postcode is never sent. Not built yet (postcode shortcut).** There is no postcode input. What holds today is only that no typed query is sent: the
   only requests are chunk reads by fixed address and typing makes none.
   `MarketplaceAreaAcceptanceTest.onlyChunkReadsByAddressAreMadeAfterAgreeingAndATypedQueryIsNeverSent` (a postcode-shaped query is typed and nothing is sent).
8. **Latitude and longitude range. Not built yet.** It belongs to the later device-location shortcut; nothing takes a coordinate, so there is nothing to test.
9. **Forgetting removes the location from my devices. Covered.** *Given* a location set on my phone and seen on my tablet, *when* I forget it on the
   phone and the devices sync, *then* the tablet reads no location and the Home keeps nothing it did not have before.
   `MarketplaceAreaAcceptanceTest.forgettingTheLocationOnOneDeviceRemovesItFromMyPairedDevice` (two real registries);
   `RegistryHomeLocationRepositoryTest.clearRemovesTheLocationAndLeavesTheRest` and `MarketplaceAreaViewModelTest.forgetClearsTheRepositoryAndStartsAgainWithStateReset`;
   `MarketplaceAreaFlowTest.forgetAsksFirstThenReturnsToTheStart` (the confirmation). Not run on a device or a real mesh.
10. **Requests appear in sync activity. Not built yet.** The pack download is not recorded as a sync event, so there is no list entry with its host
    and what was asked for.
11. **Same point, same cell, on desktop and Android. Not built yet.** No H3 library is chosen and no cell is computed; `cell` stays empty.

## Marketplace businesses (ADR-039 places)

Criteria from [the places plan](../plans/ADR-039-places-plan.md) and [ADR-039](../adr/ADR-039-home-location-h3.md), as built for "See businesses" after the area is saved.
The screens are driven in Compose UI tests over fake sources (`AreaFakePlaces`, `AreaFakeSource`); the cache and network behaviour of the real places source is
proved separately in `AutonomiPlacesPackSourceTest` over a recording client. Backing tests are in `MarketplaceBusinessesAcceptanceTest` unless another class is named.
Status is **covered**, **partial** (what is missing is said) or **not built yet**. Not covered by any automated test: the screens on a real desktop window against
the devnet (a by-hand recipe is in the plan), Android and iOS, and a real mainnet.

1. **Size and counts are shown before any data chunk is fetched. Covered.** *Given* a saved area, *when* I open See businesses and the data is over 1 MiB, *then* I
   see the count and size inside my places and the count just outside them separately, and the size of both together, and no data chunk has been fetched.
   `sizeAndCountsAreShownBeforeAnyDataChunkIsFetched`; `MarketplaceAreaBusinessesViewModelTest.theSummaryReportsInsideAndNearbyCountsAndSizesWithoutFetching`.
2. **Declining the download fetches nothing. Covered.** *Given* "This will download about 2.5 MB. Download?", *when* I choose Not now, *then* nothing is fetched
   and I am back on the summary. `decliningTheDownloadFetchesNothing`; `MarketplaceAreaBusinessesViewModelTest.decliningTheSizeConfirmationFetchesNothing`.
3. **A small area fetches without asking. Covered.** *Given* an area under 1 MiB, *when* I open See businesses, *then* the list appears straight away with no
   confirmation. `aSmallAreaFetchesWithoutAsking`; `MarketplaceAreaBusinessesViewModelTest.underOneMiBDownloadsWithoutAsking`.
4. **The just-outside toggle changes the size. Covered.** *Given* the summary with the toggle off (inside places only), *when* I turn it on, *then* the
   size I am asked to confirm is the larger one, and only on confirming is it fetched, with the just-outside places included.
   `theJustOutsideToggleChangesTheSizeThatIsConfirmedAndFetched`; `MarketplaceAreaBusinessesViewModelTest.theToggleUpdatesTheChosenSizeAndCount`,
   `theToggleDefaultsOffWhenTheNearbyDownloadIsOverOneMiB`.
5. **The list is sorted nearest first. Covered.** *Given* the list, *then* rows run from nearest to farthest from my home locality's centre (not from the
   device), ties by name. `theListIsSortedNearestFirstAndNearbyRowsAreNeverLabelledIn` (order on screen);
   `NearbyBusinessesTest.sortsByDistanceThenNameThenId`.
6. **Nearby rows are never labelled "in". Covered.** *Given* a business just outside my places, *then* its row shows its category and distance ("Pub, 1.2 km
   away") and never says it is in a locality. `theListIsSortedNearestFirstAndNearbyRowsAreNeverLabelledIn`. Wording only: the rule that decides which
   places are "nearby" is the builder's (README, Places).
7. **Search and group filter. Covered.** *Given* the list, *when* I type a name and choose a category group, *then* only matching rows remain, an empty result
   says so, and All brings them back. `searchAndGroupFilterNarrowTheList`; `MarketplaceAreaBusinessesViewModelTest.searchNarrowsTheListAndNearbyRowsAreFlagged`,
   `aGroupFilterKeepsOnlyThatGroupAndAllClearsIt`, `theListPagesByFifty`.
8. **Cancel keeps what was cached and returns to the summary. Partial.** *Given* a fetch in progress, *when* I press Cancel, *then* the fetch stops, I am on
   the summary, and chunks already stored stay on the device. `cancellingKeepsWhatWasCachedAndReturnsToTheSummary` proves the stop and the return; the cache
   keeping its chunks is proved at the source (`AutonomiPlacesPackSourceTest.aFailingChunkGivesAPartialResultAndTheOtherChunkIsCached`), not through the screen.
9. **A partial failure shows a banner with Try again. Covered.** *Given* one chunk fails, *then* the list shows what loaded, with "Some data could not be
   loaded." and Try again, which fetches again. `aPartialFailureShowsTheBannerWithTryAgain`;
   `MarketplaceAreaBusinessesFlowTest.totalFailureShowsUnreachableWithTryAgain` (nothing loaded).
10. **Forgetting the area leaves the list and drops the results. Covered.** *Given* I have seen the list, *when* I forget the area, *then* the list is gone,
    the results are dropped and the location is removed. `forgettingTheAreaLeavesTheListAndDropsTheResults`;
    `MarketplaceAreaBusinessesViewModelTest.forgettingTheAreaLeavesTheStepAndDropsTheResults`. The downloaded chunks stay in the on-disk cache; forgetting
    does not clear it.
11. **A second open after one fetch makes no network calls. Partial.** *Given* I fetched an area once, *when* I open it again with the network gone, *then* the
    list comes from the cache. Proved at the source: `AutonomiPlacesPackSourceTest.aSecondFetchMakesNoClientCalls`, `aSecondLoadMakesNoClientCalls`,
    `loadsOfflineFromTheCache`. Missing: no test drives the screens over the real source twice; the fakes have no cache. Also by hand (plan, By-hand run).
12. **No places request before the disclosure is accepted. Covered.** *Given* nothing is on the device, *when* I open See businesses, *then* the disclosure
    (which mentions businesses) shows first and, on Not now, neither the names nor the places source is loaded or fetched.
    `noPlacesRequestIsMadeBeforeTheDisclosureIsAccepted`; `MarketplaceAreaBusinessesFlowTest.theDisclosureMentionsBusinessesAndNothingIsFetchedBeforeContinue`;
    `MarketplaceAreaBusinessesViewModelTest.nothingIsRequestedBeforeTheDisclosureIsAccepted`, `decliningTheDisclosureRequestsNothing`.
    The disclosure's businesses paragraph is a draft awaiting the user's sign-off.
13. **Places are verified against their address. Covered at the source.** Given a chunk whose bytes do not hash (BLAKE3) to its address,
    when the pack is loaded, then it is not used and not cached (`AutonomiLocalityPackSourceTest`, `Blake3Test`); the desktop
    factories use the real verifier.
14. **Fetches appear in Sync activity. Not built yet.** Same as criterion 10 of the section above.
