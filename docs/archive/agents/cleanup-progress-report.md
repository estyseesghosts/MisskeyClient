# Cleanup Progress Report

**Status:** historical. Superseded by
[Decomposition 01 and 02 completion](../agents/tasks/decomposition-01-02-completion.md) and the
[acceptance matrix](decomposition-01-02-acceptance-matrix.md).

This report describes the commit range `63b01e8..e34e0f0`. Its "remaining debt" list is now
assigned to the completion task. Do not cite its completion claims as current.

**Owner:** app-shell and feature-state maintainers.

**Last reviewed:** 2026-09-14.

**Source baseline:** `e34e0f0`.

**Range reviewed:** `63b01e8..e34e0f0`, 100 commits, `2026-09-13` to `2026-09-14`.

**Plans reviewed:** `docs/decomposition_3/01.md` and `docs/decomposition_3/02.md`, both planned at baseline `ad8914b`.

**Evidence:** source verified for cited paths. Tests were inspected, not executed in this report. Device behavior remains unverified. Live-server behavior remains unverified.

## Scope

This report tracks cleanup over the last 100 commits. It measures shell decomposition and lifecycle repair. It uses source code and tests as authority. It does not treat plans as proof of behavior.

The range contains two phases. The older phase covers `63b01e8..ad8914b`. The newer phase covers `ad8914b..e34e0f0`.

## Older Phase

The older phase changed 121 files. It added 6161 lines and removed 1123 lines.

The phase extracted focused owners. It extracted the media transition layer. It extracted profile header presentation. It extracted profile large presentation. It extracted the profile timeline pager. It extracted push message handling.

The phase consolidated behavior. It consolidated pill actions and moderation contracts. It extracted `PostInteractionMutationOwner`. It replaced the share sheet with an action card. It removed obsolete setup and share code.

The phase preserved product identity. It applied the Beeline name to labels and resources. It repaired launcher resources. It prepared release `v0.2.1`.

The phase added locale resources. It added Spanish, Portuguese, and Chinese catalogs. This step created the locale selection debt that Plan 02 now owns.

## Newer Phase

The newer phase changed 75 files. It added 6017 lines and removed 1385 lines. It implements Plan 01 and starts Plan 02.

### Shell Characterization And Contracts

Commit `ef8e8e6` characterizes the shell. It adds `AppShellFixtures.kt` and `ShellCharacterizationTest.kt`.

The following commits introduce narrow contracts in `app/src/main/java/me/foxtails/palustris/ui/shell/`:

* `7a81eb7`, `afc77ef`, `eabbeb2`, `04d31db`, `57a1753`, `3816bd8`, `5dfa74b`, `d77e58c`, `56fb1f0`, `b8037af`, `32313eb`.

`PalustrisApp` in `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` now accepts 19 parameters. The prior boundary held 98 parameters. Each feature now passes one contract. The shell no longer accepts `actionSource`, `draftStore`, or duplicate `ownedPosts`.

### Host Extraction

Commit `0de4585` moves post-action construction behind a host. Commit `2379b44` moves draft persistence behind a host.

Commit `1ce9842` centralizes fan-out in `ui/shell/PostProjectionCoordinator.kt`. It adds `PostProjectionCoordinatorTest.kt`.

Commit `1f852c1` extracts `ui/session/ConnectedSessionHost.kt`. `ConnectedApp` in `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt` now holds 160 lines. The diff removes 482 lines from the root. The root now owns startup, sign-in, and connected selection only.

Commit `7232978` extracts `ui/settings/SettingsOverlayHost.kt` and `ui/notifications/NotificationLaunchHost.kt`.

Commit `2dca95e` removes four pass-through wrappers. It deletes `AppHomeDestinationContent`, `AppSearchDestinationContent`, `AppPhotoGridDestinationContent`, and `AppProfileDestinationContent`. It removes 287 lines of forwarding code.

Commit `921d7f8` moves profile editor state into the profile host. Commit `af793a8` adds `ui/DetailActionPolicy.kt` and `DetailActionPolicyTest.kt`. Commit `fb9d8bf` splits the session host into `ProfileHost`, `ThreadHost`, `NotificationsHost`, `NotificationSettingsHost`, `DirectMessagesHost`, and `EmojiHost`.

### Lifecycle Repair

Commit `518c0c5` repairs feed request ownership in `app/src/main/java/me/foxtails/palustris/ui/FeedViewModel.kt`. It adds `FeedViewModelRequestTest.kt` with 284 lines.

Commit `e12fbd3` repairs direct-message visible ownership in `app/src/main/java/me/foxtails/palustris/ui/directmessages/DirectMessageViewModel.kt`. It adds `DirectMessageViewModelTest.kt` with 377 lines.

Commit `2a4fa44` repairs durable writes. It changes `app/src/main/java/me/foxtails/palustris/data/directmessages/DirectMessageRepository.kt`. It adds `app/src/main/java/me/foxtails/palustris/data/directmessages/DirectMessageWriteAuthority.kt`. It adds `DirectMessageRepositoryTest.kt` with 242 lines.

Task state for Plan 01 reports all requested items implemented at `fb9d8bf`. Task state for Plan 02 reports slices `02-A` through `02-C` completed at `2a4fa44`.

## Ownership Map

Current owners are stable for the migrated boundary:

* `ConnectedApp` owns root composition.
* `ConnectedSessionHost` owns coherent session lifetime and shared owners.
* Focused hosts own their ViewModel, state, actions, and projection registration.
* `PostProjectionCoordinator` owns normalized fan-out.
* `DetailActionPolicy` owns origin-based action selection.

`PalustrisApp` owns navigation, adaptive layout, and surface placement. It does not own repositories, sources, draft storage, or request jobs by design. The file remains large at 1404 lines and still holds composer fields and overlay state.

## Remaining Debt

This section is a snapshot at `e34e0f0`. The completion task now owns this list. Two items below are
closed by later commits.

Known gaps also remain:

* `NotificationsViewModel`, `DirectMessageViewModel`, `NotificationSettingsViewModel`, and `ModerationViewModel` expose no `stop()`. **Closed.** Direct-message and moderation teardown exist. Notification teardown and the settings rationale are recorded in `logs/BUGS.txt` entry `260915-033000`.
* Feed, composer, saved collections, and the post-action owner share the feed model in the session host. **Partly closed.** `FeedHost` and `SavedCollectionsHost` are extracted. The composer and post-action owner remain. See completion slices C-05 and C-07.
* Account removal does not delete account-scoped drafts. **Closed.** `AccountManager.kt:320` calls `draftStore.deleteAll`.
* The production no-argument app construction for tests remains. **Closed.** `PalustrisApp` and `ConnectedApp` take required arguments. Tests use `AppShellFixtures.app`.

## Affected Tests

Preserved and extended suites include:

* `ShellCharacterizationTest`, `HomeFeedTest`, `NavigationTest`, `SignInScreenTest`, `ReplyComposerTest`, `SearchPanelRestorationTest`, `WideNavigationTest`.
* `FeedViewModelRequestTest`, `DirectMessageViewModelTest`, `DirectMessageRepositoryTest`.
* `PostProjectionCoordinatorTest`, `DetailActionPolicyTest`, `ProfileViewModelTest`.
* `SessionViewModelTest`, `NotificationLaunchRouterTest`.

## Verification Limits

* Unit verification is JVM and Robolectric only.
* `logs/DONE.txt` records `test assembleRelease` and `:app:lintDebug` passes per slice. This report did not re-execute those suites.
* Mocked source tests do not prove live-server behavior.
* Compose tests do not prove physical-device rendering.
* Release assembly does not prove signed publication.
* The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
* Live-server, physical-device, and signed-release checks remain unverified.

## Assessment

Cleanup direction is correct. The shell boundary is narrower. Feature ownership is explicit. Feed and direct-message repairs add real guards with regression tests.

The work is incomplete. `PalustrisApp` still needs composer and overlay extraction. Nine Plan 02 slices still need implementation. Later-series findings on codecs, capabilities, retention, links, identity, and documentation remain assigned, not resolved.
