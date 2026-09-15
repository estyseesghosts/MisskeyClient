# Task State: Cancellation Audit And Shell Continuation

**Plans:** `docs/decomposition_3/02.md` slice 02-L, `docs/decomposition_3/01.md`
skipped items (`FeedHost` extraction, 01-H test construction).

**Started:** 2026-09-15.

**This task is larger than one safe implementation slice.**

## Objective

Close the 02-L integration gate (cancellation stays cancellation, essential local
cleanup stays reliable, teardown is idempotent) and the deferred Plan 01 items
(`FeedHost` extraction, removal of production no-argument app construction).

## Source survey (2026-09-15, current source verified)

- `failAuth` rethrows `CancellationException`. `AccountManager` catch sites are
  compliant. No change.
- `FeedViewModel`, `DirectMessageViewModel`, `NotificationsViewModel`,
  `ModerationViewModel`, `SavedPostsViewModel`, `PostInteractionMutationOwner`,
  `SearchController`, `EmojiCatalogViewModel`, `PostThreadViewModel`,
  `ProfileViewModel`, `PhotoGridController`, `PostActionOwner`,
  `NotificationSettingsViewModel.save`, `SettingsViewModel`,
  `FileAppPreferencesRepository`, `PostPreferencesRepository`,
  `FileNotificationStore`, `SessionStore`, `NotificationWorkers`,
  `NotificationSynchronizer`, `NotificationSyncOrchestrator`,
  `UnifiedPushMessageHandler` already rethrow cancellation. No change.
- True violators: `UnifiedPushRegistrationManager.reconcile`
  (`catch (error: Exception)` records cancellation as failure),
  `UnifiedPushRegistrationManager.disable` (remote-removal catch swallows
  cancellation), `UnifiedPushConnector.runConnector` (wraps cancellation as
  `PushConnectorFailure`), `NotificationSettingsViewModel.retryRegistration`
  and `refreshDistributors` (`runCatching` maps cancellation to error state),
  `ConnectedSessionHost` draft callbacks (three `runCatching` sites).
- `NotificationsViewModel` has active jobs but no `stop()`. Its host never
  disposes it. `DirectMessagesHost` wires `stop()`; `NotificationsHost` does not.
- `NotificationSettingsViewModel.save` is request-epoch plus token gated.
  `SettingsViewModel` is app-scoped with target-explicit commands. Neither owns
  session-bound jobs. No `stop()` required; rationale recorded in 02-L2.
- `ModerationViewModel.stop()` exists since `8dd093e`. The `logs/BUGS.txt`
  `260914-122320` claim is stale on that point. Correct it in 02-L2.
- `ConnectedApp` defaults are unused (only `MainActivity` calls it, with all
  arguments). `PalustrisApp()` no-arg use: three test files plus one preview.

## Slices

| Slice | Scope | Exit | Status |
| --- | --- | --- | --- |
| 02-L1 | Push and notification-settings cancellation audit | Cancellation rethrows in reconcile, disable (after local cleanup), connector, retry, distributors. New tests pass. | completed |
| 02-L2 | Draft ownership and notification teardown | `DraftActions` owner with cancellation guards, fixture deduplicated. `NotificationsViewModel.stop()` wired. Stale bug entry corrected. | pending |
| 01-FeedHost | Extract feed owner from `ConnectedSessionHost` | `FeedHost` owns model, contracts, sink. Session host keeps source, coordinator, composer assembly. Existing suites pass. | pending |
| 01-H | Remove production no-arg app construction | `PalustrisApp` and `ConnectedApp` take required arguments. Tests use an explicit test helper. Preview passes explicit contracts. | pending |

## Verification

Focused suites per slice, then `gradlew.bat test assembleRelease` and
`gradlew.bat :app:lintDebug`. Connected instrumentation stays unverified: no
emulator or device is reachable (`adb` absent). A concurrent shell shares this
worktree; rerun Gradle on file-lock failures and never stage foreign changes.

## Last safe commit

`b759a8c` "Extend Japanese strings from reference applications".
