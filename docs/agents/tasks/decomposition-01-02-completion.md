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
| C-05 | Step 6 | Establish a composer editor owner. Move editor fields out of `PalustrisApp`. | `PalustrisApp` requests composer transitions. It does not implement editor state. | implemented, test verified. Commit recorded in the next documentation commit. |

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

## Remaining Slices

| Slice | Report step | Scope | Exit | Status |
| --- | --- | --- | --- | --- |
| C-06 | Step 7 | Make draft and publish completion version-aware. Separate the contract from storage. Bind to an account owner. | No late callback clears newer text, starts an obsolete publish, or recreates removed data. | pending |
| C-07 | Step 8 | Stabilize post-action ownership and projection. Use typed families. Retire the coordinator with its entry. | Every surface receives the accepted action result once. Retired popups have no authority. | pending |
| C-08 | Step 9 | Complete Home paging demand. Include filter identity and the request epoch. Count accepted pages. | Home reaches older visible content without unbounded automatic requests. | pending |
| C-09 | Step 10 | Finish notification request and launch ownership. Add request identity. Return explicit launch acceptance. | Rejected pages change no state. An undelivered launch is not acknowledged. | pending |
| C-10 | Step 11 | Complete settings validity and recovery. Bind commands to lifecycle-valid targets. Add recovery. | A settings command cannot change another account or restore deleted state. | pending |
| C-11 | Step 12 | Repair locale event direction. Separate startup reconciliation from later commands. | The latest accepted user choice controls resources and survives restart. | pending |
| C-12 | Step 13 | Reduce shell assembly and finish test isolation. Extract a navigation state holder where shared. | `PalustrisApp` owns navigation and placement. Feature changes stay local. | pending |
| C-13 | Step 14 | Run cancellation and integration verification. Review every touched suspending path. | Cancellation remains cancellation. All required tests pass. | pending |
| C-14 | Step 15 | Publish the final ownership documentation. Classify every document. | Maintained documentation matches source. | pending |

Split a slice when it spans independent behavior. Keep one verification method for each slice.

## Current Slice

**C-06 — Make draft and publish completion version-aware.**

Not started. Work from `progressreport.md` section 3 step 7. C-01 through C-05 are committed.

## Files Involved For C-06

- `app/src/main/java/me/foxtails/palustris/ui/composer/ComposerOwner.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/DraftsContract.kt`
- `app/src/main/java/me/foxtails/palustris/data/auth/DraftStore.kt`
- Production draft construction in `di/`
- `app/src/main/java/me/foxtails/palustris/ui/AccountManager.kt`
- `app/src/main/java/me/foxtails/palustris/ui/FeedHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/test/java/me/foxtails/palustris/DraftActionsTest.kt`
- Proposed composer publication tests

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

Process-recreation restoration of the saveable editor snapshot is source verified only. No
instrumented recreation test ran.

The proposed `DirectMessageStoreInstrumentedTest.kt` from C-03 is not written. No device is
reachable. The Room store deletion and late-write behavior stays device unverified.

## Unresolved Blockers

- No emulator or device is reachable in the agent shell. Connected instrumentation stays unverified.
- Live-server behavior stays unverified.
- Signed-release behavior stays unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
- `ru-RU` and `in-ID` localization stays blocked without code changes.

## Last Safe Commit

`cb6d024` "Own direct message editor text in the feature".

C-01 is committed at `6b8752b`. C-02 is committed at `ffc9c3f`. C-03 is committed at `bfbd7ed`.
C-04 is committed at `cb6d024`. C-05 is committed before C-06 starts. C-06 is the next slice.
