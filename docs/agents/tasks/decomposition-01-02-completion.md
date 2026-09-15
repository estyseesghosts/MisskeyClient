# Task State: Decomposition 01 And 02 Completion

**Plans:** `docs/decomposition_3/01.md`, `docs/decomposition_3/02.md`.

**Specification:** `docs/decomposition_3/progressreport.md` (sections 3 and 5).

**Acceptance matrix:** `docs/agents/decomposition-01-02-acceptance-matrix.md`.

**Started:** 2026-09-14.

**This task is larger than one safe implementation slice.**

## Objective

Meet the Plan 01 and Plan 02 exit conditions. Close the ten gaps in `progressreport.md`
section 1. Keep the completed extractions and repairs. Do not recreate the old argument lists.

## Invariants

- Keep runtime generation, durable session revision, and registry generation separate.
- Keep protocol behavior in adapters.
- Keep shared domain models protocol-neutral.
- Keep account secrets, tokens, and sources out of presentation contracts.
- Capture ownership before launch. Check authority after every suspension.
- Reserve an operation slot synchronously. Release only the owning operation's slot.
- Merge from current accepted state, not a captured whole-screen snapshot.
- Keep cursors opaque. Preserve adapter order. Do not infer chronology from opaque IDs.
- Keep Home and Photo Grid state independent.
- Keep drafts account-scoped and encrypted.
- Do not change a stored format without a migration in the same slice.
- Preserve unrelated worktree changes.
- Use Beeline in user-facing text. Keep the codename out of user-facing content.
- The shell consumes one accepted connected context. It never joins separate session flows.
- A connected context carries no token to presentation. The source stays internal to hosts.

## Accepted Decisions

- The progress report is the completion specification.
- The acceptance matrix is the single status record for the exit conditions.
- One completion slice owns one behavior.
- Documentation changes belong in the same slice as the behavior that changes.
- Plans 03 and 04 stay assigned to later work. They require a rebase before implementation.
- A file extraction alone does not close a behavioral exit condition.
- `NotificationSyncController.register` returns the sync token that owns the registered source.
  Registration is synchronous. A delayed registration is not part of the current boundary.

## Completed Slices

| Slice | Report step | Scope | Exit | Status |
| --- | --- | --- | --- | --- |
| C-01 | Step 2 | Make connected identity coherent. Resolve one accepted context. Remove the `sourceFactory.create` fallback. | Every source-backed feature receives values from one accepted connected lifetime. | implemented, test verified. Commit `6b8752b`. |
| C-02 | Step 3 | Repair ViewModel lifetime and route re-entry. Add an explicit connected-entry store and lifecycle owner. | Retired owners cannot publish. Re-entered features never receive stopped owners. | implemented, test verified. Commit `ffc9c3f`. |
| C-03 | Step 4 | Complete DM durable write authority. Route `markRead` through `commitIfCurrent`. Keep network outside locks. | A retired session cannot mutate current DM storage. Removed rows stay deleted. | implemented, test verified. Commit `bfbd7ed`. |
| C-04 | Step 5 | Give DM recovery text a feature owner. Add an editor revision. Clear only on accepted success. | A send failure cannot erase recoverable text. | implemented, test verified. Commit `cb6d024`. |
| C-05 | Step 6 | Establish a composer editor owner. Move editor fields out of `PalustrisApp`. | `PalustrisApp` requests composer transitions. It does not implement editor state. | implemented, test verified. Commit `bd2d1b6`. |
| C-06a | Step 7, part 1 | Make publish completion version-aware. Reserve the submission before the async save. Reject obsolete save callbacks. Clear and delete only the submitted version. | No late save clears newer text or starts an obsolete publish. | implemented, test verified. Commit `84006c1`. |
| C-06b | Step 7, part 2 | Move the draft storage owner into the data layer. Bind draft operations to the account. Add load request identity. Report load and delete failures. | The presentation contract carries no storage. Draft failures are reported. | implemented, test verified. Commit `c1288da`. |
| C-06c | Step 7, part 3 | Coordinate account removal with pending draft writes. | A remove cannot leave recreated draft data. | implemented, test verified. Commit `4454bae`. |
| C-07 | Step 8 | Stabilize post-action ownership and projection. Use typed families. Retire the coordinator with its entry. | Every surface receives the accepted action result once. Retired popups have no authority. | implemented, test verified. Commit `0027b60`. |
| C-08 | Step 9 | Complete Home paging demand. Include filter identity and the request epoch. Count accepted pages. | Home reaches older visible content without unbounded automatic requests. | implemented, test verified. Commit `a011a06`. |
| C-09 | Step 10 | Finish notification request and launch ownership. Add request identity. Return explicit launch acceptance. | Rejected pages change no state. An undelivered launch is not acknowledged. | implemented, test verified. Commit `c6ab9b1`. |
| C-10 | Step 11 | Complete settings validity and recovery. Bind commands to lifecycle-valid targets. Add recovery. | A settings command cannot change another account or restore deleted account state. | implemented, test verified. Commit `731b74b`. |
| C-11 | Step 12 | Repair locale event direction. Separate startup reconciliation from later commands. | The latest accepted user choice controls resources and survives restart. | implemented, test verified. Commit `43f8aa0`. |

C-01 changed `AccountManager`, `NotificationSyncController`, `ConnectedApp`,
`ConnectedSessionHost`, `MainActivity`, `SessionViewModelTest`, and added
`ui/session/ConnectedSessionContext.kt` and `ConnectedSessionContextTest.kt`.

C-02 added `ui/session/ConnectedEntryStore.kt` and `ConnectedEntryStoreTest.kt`. It changed
`ConnectedApp`, `ConnectedSessionHost`, the feature hosts, `SettingsOverlayHost`, and
`NotificationSettingsViewModel`.

C-03 changed `DirectMessageWriteAuthority`, `DirectMessageRepository`, `DirectMessageViewModel`,
`DirectMessagesHost`, `ConnectedSessionContext`, `ConnectedSessionHost`, and `AccountManager`. It
changed `DirectMessageRepositoryTest`, `DirectMessageSourceTest`, and `DirectMessageViewModelTest`.
`DirectMessageWriteAuthority` now activates, revokes, deletes, and commits under one per-account
lock. The account lifecycle issues the writer generation. A repository captures that generation. It
does not issue one. `markRead` routes its local write through `commitIfCurrent`. Network requests
stay outside every lock.

C-04 moved the direct-message composer text into `DirectMessageViewModel`. The state now carries
`editorText` and `editorRevision`. `DirectMessageUiState` owns them. Each editor change and each
selection change advances the revision. A selection change resets the text. `DirectMessagesContract`
adds `updateEditor(text)` and changes `send(text)` to `send()`. `DirectMessageConversationScreen` is
stateless. It reads `state.editorText` and calls `onEditorTextChange`. The Send button no longer
clears the text. A send captures the text and revision. It clears the editor only after accepted
success when the revision is unchanged. A failed send keeps the text.

C-05 added `ui/composer/ComposerEditorState.kt`, `ui/composer/ComposerOwner.kt`, and
`ui/composer/ComposerHost.kt`. The owner holds text, warning, audience, draft identity, the dirty
snapshot, the drafts list, reply and quote restoration, draft value construction, save, delete, and
publish. It publishes `ComposerNavigation` requests. The shell applies a request by placing the
composer overlay. `PalustrisApp` no longer holds editor fields. It renders the editor from the owner
and keeps overlay placement and back precedence. The editor state uses a saveable snapshot. A session
replacement clears restored reply and quote targets.

C-06a gave `ComposerOwner` an editor revision and a reserved submission. Each editor mutation
advances the revision. `publish` captures the request, account, draft identity, revision, and
session revision, then reserves the submission before the asynchronous draft save starts. A second
publish is rejected while a submission is reserved. An obsolete save callback cannot publish. A
successful publish clears and deletes only the submitted version. The publish control disables while
a submission is in flight.

C-06b added `data/auth/DraftActions.kt`. The draft storage owner left `ui/shell/DraftsContract.kt`.
The presentation contract carries no storage type. `DraftActions.create` holds the legacy preferences
lookup in the data layer. `DraftActions` binds to one account. `ui/composer/DraftsContractAdapter.kt`
adapts it to the presentation contract. `ComposerOwner.refreshDrafts` uses a load epoch so a stale
load result cannot replace a newer one. Load and delete failures report an explicit message.

C-06c added `data/auth/DraftWriteAuthority.kt`. The authority mirrors `DirectMessageWriteAuthority`
with one lock per account, monotonic generations, `activate`, `invalidate`, `invalidateAndDelete`,
and `commitIfCurrent`. `AccountManager.connect` activates the draft generation before it publishes
the context. `updateAccount` carries the generation into the rebuilt context. `removeAccount`
revokes writers and deletes rows in one serialized boundary. `DraftActions` routes save and delete
through `commitIfCurrent`. A revoked writer writes nothing and reports no success. `ConnectedSessionHost`
builds the draft owner from `connectedContext.draftGeneration` and the injected authority.

C-07 added `PostActionFamily` and `OperationToken` to `PostInteractionExecutionAuthority`. Family
slots are typed. Acquisition returns the owning token or null when busy. Only that token releases
the slot. `PostInteractionMutationOwner`, `ProfileViewModel`, and `PostThreadViewModel` use the
token API. `PostProjectionCoordinator` retires with its connected entry, rejects forwards and
registrations after retirement, and rejects repeated deliveries of one accepted publication by its
created-post identity. `PostActionOwner` retires with its connected entry and rejects later opens,
mutations, and reports. `ConnectedSessionHost` builds the popup owner from the stable connected
identity, reads the profile refresh callback without recreating the owner, and registers both
retirements with the entry store. The narrow popup interface extraction stays deferred to C-12.

C-08 published the feed request epoch through `FeedState` and `HomeFeedUiState`. `FeedViewModel`
advances the epoch on refresh and timeline replacement, including failed timeline changes. Paging
keeps the epoch. `HomePagingDemand` tracks filter identity and the epoch beside the row count, so
a filter or epoch change at the same count still resets the budget. `reset` advances a demand
generation so evaluation reruns even when rows are unchanged. `onPageAccepted` counts only
accepted pages. The demand blocks while sign-in is required.

C-09 gave `NotificationsViewModel` a request epoch. Refresh and paging capture the query identity
before launch, reserve their slots synchronously, and reject stale completions so rejected pages
change no state. Paging returns at the reserved slot instead of cancelling the page in flight. A
refresh supersedes paging display. `NotificationLaunchHost` always calls the latest route callback
and acknowledges a launch only when the receiving shell accepts its route. `ConnectedApp` accepts
only with an accepted connected context, so an undelivered launch stays pending. A missing account
routes to the recoverable unavailable state. A failed switch resolves through the accounts update,
which reruns the effect into the same unavailable state.

C-10 bound post commands to the validated account set the shell publishes. A command for a
removed account reports an explicit unavailable error at call time. A command queued before the
removal writes nothing and reports nothing. Failed commands are retained for explicit retry.
Dismissing the error hides the message but keeps the retry until the next command. Command errors
are typed, so the shell resolves the user-visible message from resources. Account settings models
wait for the restored account index instead of constructing eagerly. The settings error card
gains a retry action.

C-11 added `ui/localization/AppLocaleOwner.kt`. The owner applies first-upgrade precedence once
through `reconcilePlatformSelection`, then follows observed movement: a moved repository exports
the in-app choice to the platform, and a moved platform imports the external choice into the
repository, including an external clear to System default. A pending import repeats until the
repository applies it, and a stale platform read after an export re-asserts the repository.
`MainActivity` serializes each decision with its side effect under one mutex, prefers the
platform value for the base context on Android 13 and later, recreates only when an applied
import leaves a stale base context, and reports a failed import through repository state without
a recreation loop. The Language settings route already survives recreation through
`SettingsRouteSaver`.

## Remaining Slices

| Slice | Report step | Scope | Exit | Status |
| --- | --- | --- | --- | --- |
| C-12 | Step 13 | Reduce shell assembly and finish test isolation. Extract a navigation state holder where shared. | `PalustrisApp` owns navigation and placement. Feature changes stay local. | pending |
| C-13 | Step 14 | Run cancellation and integration verification. Review every touched suspending path. | Cancellation remains cancellation. All required tests pass. | pending |
| C-14 | Step 15 | Publish the final ownership documentation. Classify every document. | Maintained documentation matches source. | pending |
| C-15 | Cleanup (no report step) | Remove dead scaffolding left by earlier extraction waves. | No caller remains. Focused Compose suites, `test assembleRelease`, and `:app:lintDebug` pass. | pending |

Split a slice when it spans independent behavior. Keep one verification method for each slice.

C-15 is a behavior-neutral cleanup. It is not part of the Plan 01 or Plan 02 exit conditions. It
removes dead scaffolding that earlier extraction waves left behind. The symbol list and the
replacement for each symbol are in `docs/agents/app-shell-ownership.md`. Do not combine it with a
behavior change.

## Current Slice

**C-12 — Reduce shell assembly and finish test isolation.**

Not started. Work from `progressreport.md` section 3 step 13. C-01 through C-11 are committed.
`PalustrisApp` owns navigation and placement but still holds shell assembly that belongs with
feature owners, and small feature scenarios still construct the full shell.

## Files Involved For C-12

- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/AppShellState.kt`
- `app/src/main/java/me/foxtails/palustris/ui/AppLocalPageContent.kt`
- `app/src/main/java/me/foxtails/palustris/ui/AppNotificationsDestinationContent.kt`
- Compact and wide detail presentation files
- `app/src/main/java/me/foxtails/palustris/ui/DetailActionPolicy.kt`
- `AppShellFixtures.kt`
- Existing Home, navigation, reply, and wide-layout tests
- Proposed feature-specific test fixtures

## Required Verification

Use focused tests first. Then run the full gate.

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*ConnectedSessionContextTest" --tests "*SessionViewModelTest"
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

Close standard input. Set an explicit timeout for each Gradle call.

C-01 verification result: focused tests passed. `test assembleRelease` passed.
`:app:lintDebug` passed. Commit `6b8752b`.

C-02 verification result: `ConnectedEntryStoreTest` and `ConnectedSessionContextTest` passed.
`test assembleRelease` passed. `:app:lintDebug` passed when run alone.

C-03 verification result: `DirectMessageRepositoryTest`, `DirectMessageViewModelTest`,
`DirectMessageSourceTest`, `DirectMessageScreenTest`, `SessionViewModelTest`, and
`ConnectedSessionContextTest` passed. `test assembleRelease` passed. `:app:lintDebug` passed when
run alone.

C-04 verification result: `DirectMessageViewModelTest`, `DirectMessageScreenTest`,
`DirectMessageSourceTest`, and `DirectMessageRepositoryTest` passed. `test assembleRelease` passed.
`:app:lintDebug` passed when run alone.

C-05 verification result: `ComposerOwnerTest`, `ReplyComposerTest`, and `NavigationTest` passed.
`test assembleRelease` passed. `:app:lintDebug` passed when run alone.

Test these cases for C-05:

- Reply opens for the effective action target and records the reply target. Covered by
  `ComposerOwnerTest.replyOpensForTheEffectiveActionTarget`.
- A foreign-account post cannot open a reply. Covered by `replyRejectsAForeignAccountPost`.
- A dirty editor blocks a reply request. Covered by `replyRejectsWhileTheEditorIsDirty`.
- Quote records the quote target. Covered by `quoteSetsTheQuoteTarget`.
- Draft restoration sets text, warning, and audience without dirty changes. Covered by
  `draftRestoresTextWarningAndAudienceWithoutDirtyChanges`.
- Save clears the dirty baseline only after success. Covered by
  `saveClearsTheDirtyBaselineOnlyAfterSuccess`.
- A failed save keeps the text for recovery. Covered by `failedSaveKeepsTheTextForRecovery`.
- An unavailable audience is rejected before publish. Covered by
  `publishRejectsAnUnavailableAudienceWithoutPublishing`.
- Publish saves, then publishes, then clears on acceptance. Covered by
  `publishSavesThenPublishesAndClearsOnAcceptance`.
- A session replacement clears restored targets. Covered by `sessionReplacementClearsRestoredTargets`.
- The composed reply flow still opens the composer. Covered by
  `ReplyComposerTest.replyOpensComposerForTheEffectiveActionTarget`.

C-06a verification result: `ComposerOwnerTest` passed with 13 cases. `test assembleRelease` passed.
`:app:lintDebug` passed when run alone.

Test these cases for C-06a:

- A second publish is rejected while a save is pending. Covered by
  `ComposerOwnerTest.duplicatePublishIsRejectedWhileASaveIsPending`.
- An obsolete save callback cannot publish after a session replacement. Covered by
  `obsoleteSaveCallbackCannotPublishAfterSessionReplacement`.
- Newer edits typed during publication survive acceptance. Covered by
  `newerEditsDuringPublishSurviveAcceptance`.

C-06b verification result: `DraftActionsTest`, `ComposerOwnerTest`, `ReplyComposerTest`, and
`NavigationTest` passed. `test assembleRelease` passed with 841 tests. `:app:lintDebug` passed when
run alone.

Test these cases for C-06b:

- Load migrates legacy preferences, then lists. Covered by
  `DraftActionsTest.loadMigratesThenLists`.
- A load failure reports an explicit message. Covered by `loadFailureReportsError`.
- A load cancellation reports nothing. Covered by `loadCancellationReportsNothing`.
- A delete failure still completes and reports an explicit message. Covered by
  `deleteFailureStillCompletesAndReports`.
- A delete cancellation reports nothing. Covered by `deleteCancellationReportsNothing`.
- Drafts survive activity recreation and delete through the bound account. Covered by
  `NavigationTest.draftsSurviveActivityRecreationAndCanBeDeleted`.
- Closing the composer autosaves through the bound account. Covered by
  `NavigationTest.closingComposerAutosavesUnsavedText`.

The load and delete failure messages surface through the composer error field. The drafts page has
no separate error surface. No device test ran.

Process-recreation restoration of the saveable editor snapshot is source verified only. No
instrumented recreation test ran.

The proposed `DirectMessageStoreInstrumentedTest.kt` from C-03 is not written. No device is
reachable. The Room store deletion and late-write behavior stays device unverified.

C-06c verification result: `DraftActionsTest`, `SessionViewModelTest`, `ComposerOwnerTest`, and
`ConnectedSessionContextTest` passed. `test assembleRelease` passed. `:app:lintDebug` passed when
run alone.

Test these cases for C-06c:

- A save after invalidation writes nothing and reports nothing. Covered by
  `DraftActionsTest.saveAfterInvalidationWritesNothingAndReportsNothing`.
- A delete after invalidation deletes nothing and reports nothing. Covered by
  `deleteAfterInvalidationDeletesNothingAndReportsNothing`.
- An in-flight save cannot recreate rows deleted by removal. Covered by
  `inFlightSaveCannotRecreateRowsDeletedByRemoval`.
- A pending save cannot recreate the removed draft while a second account keeps its drafts.
  Covered by `SessionViewModelTest.removeAccountCannotLeaveRecreatedDraftFromPendingSave`.

No device test ran. Live-server and signed-release behavior stay unverified.

C-07 verification result: `PostInteractionExecutionAuthorityTest`, `PostProjectionCoordinatorTest`,
`PostActionOwnerTest`, `PostInteractionMutationOwnerTest`, `PostThreadViewModelTest`, and
`ProfileScreenTest` passed. `test assembleRelease` passed. `:app:lintDebug` passed when run alone.

Test these cases for C-07:

- A busy family slot rejects the second caller. Covered by
  `PostInteractionExecutionAuthorityTest.busyFamilyRejectsTheSecondCaller`.
- Other families proceed while one family is owned. Covered by `otherFamiliesProceedWhileOneFamilyIsOwned`.
- Release frees the slot, a foreign release keeps it, and a repeated release is a no-op. Covered by
  `releaseFreesTheSlotForTheNextCaller`, `foreignReleaseKeepsTheOwnedSlot`, and `repeatedReleaseIsANoOp`.
- A repeated publication delivery reaches sinks once. Covered by
  `PostProjectionCoordinatorTest.duplicatePublicationIsDeliveredOnce`.
- A retired coordinator delivers nothing and accepts no sinks. Covered by
  `retiredCoordinatorDeliversNothingAndAcceptsNoSinks`.
- Retirement dismisses the open popup and rejects later opens, mutations, and reports. Covered by
  `PostActionOwnerTest.retireDismissesTheOpenPopup`, `retiredOwnerRejectsOpen`, and
  `retiredOwnerRejectsMutationsAndReports`.

No device test ran. Live-server and signed-release behavior stay unverified.

C-08 verification result: `HomePagingDemandTest`, `FeedViewModelRequestTest`, `HomeFeedTest`, and
`NavigationTest` passed. `test assembleRelease` passed. `:app:lintDebug` passed when run alone.

Test these cases for C-08:

- A filter change at the same row count resets the budget. Covered by
  `HomePagingDemandTest.filterChangeAtTheSameCountResetsTheBudget`.
- A request epoch change resets the budget. Covered by `requestEpochChangeResetsTheBudget`.
- A reset advances the generation for reevaluation. Covered by
  `resetAdvancesTheGenerationForReevaluation`.
- The sign-in gate blocks automatic paging. Covered by `signInGateBlocksAutomaticPaging`.
- Refresh advances the epoch and paging keeps it. Covered by
  `FeedViewModelRequestTest.refreshAdvancesTheRequestEpochAndPagingKeepsIt`.
- A failed timeline change carries the new epoch. Covered by
  `failedTimelineChangeCarriesTheNewRequestEpoch`.

No device test ran. Live-server and signed-release behavior stay unverified.

C-09 verification result: `NotificationsViewModelTest`, `NotificationLaunchHostTest`,
`NotificationLaunchRouterTest`, and `NotificationRouteResolverTest` passed.
`test assembleRelease` passed. `:app:lintDebug` passed when run alone. The full gate hit the
known intermittent `MediaViewerScreenTest.selectedAttachmentsRemainOnFullQualityAfterSwipingBack`
timeout once. Its focused rerun passed, and a full `test assembleRelease` rerun passed.

Test these cases for C-09:

- An old page failure after replacement changes nothing. Covered by
  `NotificationsViewModelTest.oldPageFailureAfterReplacementChangesNothing`.
- A queued paging call returns at the reserved slot. Covered by
  `pagingSlotReservationRejectsOverlap`.
- A delivered launch is acknowledged when accepted. Covered by
  `NotificationLaunchHostTest.deliveredLaunchIsAcknowledgedWhenAccepted`.
- An undelivered launch stays pending. Covered by `undeliveredLaunchIsNotAcknowledged`.
- A missing account routes unavailable and clears. Covered by
  `missingAccountRoutesUnavailableAndClears`.
- A launch for another account switches and stays pending. Covered by
  `launchForAnotherAccountSwitchesAndStaysPending`.

No device test ran. Live-server and signed-release behavior stay unverified.

C-10 verification result: `SettingsViewModelTest`, `SettingsRouteRestorationTest`,
`SettingsDisplayTest`, `ModerationViewModelTest`, and `LocalizationResourceTest` passed.
`test assembleRelease` passed. `:app:lintDebug` passed when run alone. The full gate failed
once with test-JVM Main-dispatcher init pollution in `NotificationsViewModelTest`, which this
slice does not touch. A full `test assembleRelease` rerun passed with no source change.

Test these cases for C-10:

- A post command for a removed account writes nothing and reports unavailable. Covered by
  `SettingsViewModelTest.postCommandForRemovedAccountWritesNothingAndReportsUnavailable`.
- A queued post command after removal writes nothing and reports nothing. Covered by
  `queuedPostCommandAfterRemovalWritesNothingAndReportsNothing`.
- A failed command retries after recovery. Covered by `failedCommandRetriesAfterRecovery`.
- Dismiss keeps retry until the next command. Covered by
  `dismissKeepsRetryUntilTheNextCommand`.

No device test ran. Live-server and signed-release behavior stay unverified.

C-11 verification result: `AppLocaleOwnerTest` (12 cases) and `AppLocaleControllerTest`
passed. `test assembleRelease` passed. `:app:lintDebug` passed when run alone.

Test these cases for C-11:

- First upgrade imports an explicit platform locale. Covered by
  `AppLocaleOwnerTest.firstUpgradeImportsAnExplicitPlatformLocale`.
- An applied startup import converges quietly, and a repeated check before the applied
  import repeats it. Covered by `appliedStartupImportConvergesQuietly` and
  `repeatedCheckBeforeAnAppliedImportRepeatsTheImport`.
- An in-app selection exports over a differing platform. Covered by
  `inAppSelectionExportsOverADifferingPlatform`.
- An in-app selection of System default clears the platform. Covered by
  `inAppSelectionToSystemDefaultClearsThePlatform`.
- An external selection after convergence imports. Covered by
  `externalSelectionAfterConvergenceImports`.
- An external clear imports System default. Covered by `externalClearingImportsSystemDefault`
  and `repeatedCheckBeforeAnAppliedExternalImportRepeatsTheImport`.
- A stale platform read after export re-asserts the repository. Covered by
  `stalePlatformReadAfterExportDoesNotUndoTheUserChoice`.
- A repository move during an external check keeps the user choice. Covered by
  `repositoryMoveDuringExternalCheckKeepsTheUserChoice`.
- A newer external selection supersedes a pending import. Covered by
  `newerExternalSelectionSupersedesAPendingImport`.

No device test ran. API 29 and API 33+ locale instrumentation stays unverified.
Live-server and signed-release behavior stay unverified.

## Unresolved Blockers

- No emulator or device is reachable in the agent shell. Connected instrumentation stays unverified.
- Live-server behavior stays unverified.
- Signed-release behavior stays unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
- `ru-RU` and `in-ID` localization is unblocked by slice L-01. Russian and Indonesian are
  selectable with confident navigation subsets. Full catalog translation stays open.

## Last Safe Commit

`43f8aa0` "Repair locale event direction".

C-01 is committed at `6b8752b`. C-02 is committed at `ffc9c3f`. C-03 is committed at `bfbd7ed`.
C-04 is committed at `cb6d024`. C-05 is committed at `bd2d1b6`. C-06a is committed at `84006c1`.
C-06b is committed at `c1288da`. C-06c is committed at `4454bae`. C-07 is committed at `0027b60`.
C-08 is committed at `a011a06`. C-09 is committed at `c6ab9b1`. C-10 is committed at `731b74b`.
C-11 is committed at `43f8aa0`. C-12 is the next slice.
