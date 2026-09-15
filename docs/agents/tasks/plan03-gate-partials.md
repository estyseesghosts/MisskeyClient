# Task State: Plan 03 Gate Partials Cleanup

**Plans:** `docs/decomposition_3/01.md`, `docs/decomposition_3/02.md`.

**Specification:** `docs/decomposition_3/progressreport.md` sections 3 and 5.

**Acceptance matrix:** `docs/agents/decomposition-01-02-acceptance-matrix.md`.

**Started:** 2026-09-15.

**This task is larger than one safe implementation slice.**

## Objective

Close the partial gaps from the 01/02 review. Keep completed extractions. Leave device, live-server, and signed-release checks as blocked. Do not start Plan 03 work in this task.

## Invariants

- Keep runtime generation, durable session revision, and registry generation separate.
- Keep protocol behavior in adapters.
- Keep shared domain models protocol-neutral.
- Keep account secrets, tokens, and sources out of presentation contracts.
- Capture ownership before launch. Check authority after each suspension.
- Reserve an operation slot synchronously. Release only the owning operation slot.
- Merge from current accepted state, not a captured whole-screen snapshot.
- Keep cursors opaque. Preserve adapter order.
- Keep Home and Photo Grid state independent.
- Keep drafts account-scoped and encrypted.
- Do not change a stored format without a migration in the same slice.
- Preserve unrelated worktree changes.
- Use Beeline in user-facing text. Keep the codename out of user-facing content.
- Do not commit without separate authorization.

## Accepted Decisions

- The acceptance matrix stays the status record until each partial slice passes.
- One completion slice owns one behavior.
- Documentation changes belong in the same slice as the behavior change.
- Plans 03 and 04 stay assigned to later work.
- A file extraction alone does not close a behavioral exit condition.
- No emulator is reachable. Instrumented locale and Room tests stay blocked verification.

## Completed Slices

| Slice | Scope | Exit | Status |
| --- | --- | --- | --- |
| P-01 | Remove duplicate `ownedPosts` input. Make `state.ownedPosts` authoritative. | Home rows come from the Home contract only. | implemented, test verified. Commit `0fbc7c4`. |
| P-02 | Narrow popup contract. Leaves use `PostPopupPresentation`. | Generic leaves never receive the service-backed owner, source, or scope. | implemented, test verified. Commit `108ca3a`. |
| P-03 | Make thread external apply non-emitting. | Externally applied projections never re-emit. No reliance on the coordinator re-entrancy guard. | implemented, test verified. Commit `3b65104`. |
| P-04 | Bind paging to the exact input cursor. Bump the collection epoch on stop. | A stale same-epoch page cannot merge or rewind the cursor. A stopped collection cannot publish. | implemented, test verified. Commit `4aa3618`. |
| P-05 | Guard thread reconcile and rollback by action family. | A stale server snapshot cannot overwrite newer local fields. A failed action cannot roll back a newer same-family projection. | implemented, test verified. Commit `ad5d403`. |
| P-06 | Inject the IO dispatcher into the Room DM store. Extract pure settings route gating with tests. | No hard-coded dispatcher in storage. Pending routes survive loading. Removed accounts remap to the safe parent, never to the active account. | implemented, test verified. |

P-01 verification: `HomeFeedTest` and `NavigationTest` pass. The `SearchScreen` `onReply` observer at `HomeFeedTest.kt:774` is a leaf callback test, not a shell seam. `PalustrisApp` carries no `onReply` parameter.

P-02 verification: `PostActionOwnerTest` and `PostProjectionCoordinatorTest` pass. `LocalPostActionOwner` provides `PostPopupPresentation`. `PostActionOwner` implements it.

P-03 verification: `PostThreadViewModelTest` (with new `externalProjectionDoesNotEmitToTheUpdateListener`), `PostProjectionCoordinatorTest`, and `PostProjectionTest` pass. Feed `updateExternalPost` was already non-emitting.

P-04 verification: `FeedViewModelRequestTest`, `SavedPostsViewModelTest`, and `FeedViewModelReactionTest` pass. Same-epoch page overlap stays serialized by the synchronous `loadingMore` reservation, so no extra operation token is required. The cursor check is defense in depth.

P-05 verification: `PostThreadViewModelTest` (with new `failedFavoriteKeepsNewerSameFamilyCountProjection` and `staleServerSnapshotPreservesNewerLocalFields`), `PostInteractionMutationOwnerTest`, and `PostInteractionExecutionAuthorityTest` pass. Cross-surface same-family concurrency stays serialized by the shared execution authority. The focused mutation owner already merges and rolls back by family.

P-06 verification: `SettingsRouteRestorationTest` (with new pending, present, removed, and non-account cases), `SettingsViewModelTest`, `SettingsDisplayTest`, `DirectMessageRepositoryTest`, and `DirectMessageViewModelTest` pass. The Room-backed removal test stays blocked verification without a device. Cache reads already run off the main thread in the repository and the ViewModel.

## Current Slice

P-07 — Reduce shell remnants and record locale/device limits.

## Files Involved For P-01

- `app/src/main/java/me/foxtails/palustris/ui/HomeFeed.kt`
- `app/src/test/java/me/foxtails/palustris/HomeFeedTest.kt`
- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/HomeContract.kt`

## Required Verification

Use focused tests first. Then run the full gate.

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.HomeFeedTest" --tests "me.foxtails.palustris.NavigationTest"
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:lintDebug
```

Close standard input. Set an explicit timeout for each Gradle call.

## Unresolved Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server behavior stays unverified.
- Signed-release behavior stays unverified.
- The Android 15 system-bar failure stays in `logs/BUGS.txt`.

## Last Safe Commit

`da7aa6f` "Keep cancellation cancellation in the emoji catalog load".
