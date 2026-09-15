# Historical Task State: State And Lifecycle Repair

**Status:** historical. Superseded by
[Decomposition 01 and 02 completion](decomposition-01-02-completion.md).

This file records the first Plan 02 pass. Its "all slices complete" claim is not current. The
acceptance matrix lists the remaining exit conditions.

**Plan:** `docs/decomposition_3/02.md`.

**Started:** 2026-09-14.

## Objective

Repair stale asynchronous publication across feed, direct messages, notification
checkpoints, moderation, reactions, Home paging, settings restoration, locale
availability, and cancellation.

## Invariants

- Capture ownership before launch. Reserve the operation slot synchronously.
- Check authority after every suspension before publishing state.
- Cleanup releases only the current operation's slot.
- Merge from current accepted state, never an old whole-screen snapshot.
- Keep IDs opaque. Preserve adapter order. Do not invent chronology.
- Do not change stored formats without a migration in the same slice.
- Keep protocol behavior in adapters.

## Slices

| Slice | Status | Commit |
| --- | --- | --- |
| 02-A Feed epochs | completed | `518c0c5` |
| 02-B DM visible state | completed | `e12fbd3` |
| 02-C DM durable state | completed | `2a4fa44` |
| 02-D Notification request context | completed | `175d97c` |
| 02-E Moderation lifetimes | completed | `8dd093e` |
| 02-F Mutation families | completed | `111f0c3` |
| 02-G Thread overlays and projections | completed | `7f03284` |
| 02-H Home paging demand | completed | `aa19bc4` |
| 02-I Settings commands and routes | completed | `9679fce` |
| 02-J Locale catalog | completed | |
| 02-K Locale lifecycle | completed | |
| 02-L Cancellation and integration | completed | `d5ce911`, `30a4587`, `7695886` |
| 01 skipped items | completed | `3e40d92`, `bffe418` |

## Verification

Run focused suites per slice, then `.\gradlew.bat test assembleRelease` and
`.\gradlew.bat :app:lintDebug`.

## Blockers

- Live-server, physical-device, and signed-release checks are unverified.
- Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.

## Last safe commit

`518c0c5` "Repair feed request ownership".

`e12fbd3` "Repair direct-message visible ownership".

`2a4fa44` "Repair direct-message durable writes".

`175d97c` "Repair notification request context".

`8dd093e` "Repair moderation lifetimes".

`111f0c3` "Share post mutation ownership across surfaces".

`509b3d1` "Finish mutation family ownership".

`d8fdecc` "Share mutation authority with profile and thread".

`7f03284` "Repair thread overlays and reaction projections".

`aa19bc4` "Bound Home automatic paging".

`9679fce` "Repair settings commands and route restoration".

`977bf03` "Extract saved collections host".

Slice 02-I is complete and verified. It also extracts `SavedCollectionsHost` as the requested
Plan 01 continuation. 02-J through 02-L remain pending.

- Automatic Home paging maps lazy indices to post rows and ignores error, empty, loading, and footer rows.
- A fully filtered list still pages automatically while a usable cursor exists, bounded to three pages without visible progress.
- Manual continuation resets the budget. Refresh and timeline change reset it.
- A filtered-empty state replaces the blank list and keeps the manual continuation.

Slice 02-G is complete and verified.

## Plan 01 Skipped-Item Evaluation

Checked on 2026-09-14 after `7f03284`.

| Skipped item | State | Disposition |
| --- | --- | --- |
| Delete account-scoped drafts on removal | Done in `69467c1` | Closed. |
| DM ViewModel teardown | Done in `e12fbd3` (`DirectMessageViewModel.stop()`) | Closed. |
| Moderation ViewModel teardown | Done in `8dd093e` (`ModerationViewModel.stop()`) | Closed. |
| Notification ViewModel teardown | `NotificationsViewModel` still has no `stop()` | Done in 02-L2 (`NotificationsViewModel.stop()` with host disposal) | Closed. |
| Settings ViewModel teardown | `SettingsViewModel` still has no `stop()` | No `stop()` by construction: app-scoped, target-explicit commands through repository serialization; rationale in 02-L2 and `logs/BUGS.txt` | Closed. |
| Extract `SavedCollectionsHost` | Done in `977bf03` | Closed. |
| Extract `FeedHost` | Not present | Done in `3e40d92` (`ui/FeedHost.kt`; session host keeps source, coordinator, composer assembly) | Closed. |
| Remove production no-argument app construction used by tests | Still present | Done in `bffe418` (`AppShellFixtures.app` test helper; `PalustrisApp` and `ConnectedApp` take required arguments) | Closed. |

Conclusion: all skipped items are closed. 02-L is complete and verified.

Slice 02-I is complete and verified.

- `SettingsViewModel` is the single command owner for application and post preferences.
- Post commands are granular and capture the target account, so a delayed write cannot follow
  a later active account or replace a newer field.
- The separate `settingsViewModelUpdate` path is removed; the overlay writes through the owner.
- `SettingsHost` renders load, save, and command errors with a dismiss recovery action.
- `FileAppPreferencesRepository.update` rethrows cancellation without publishing a save error.
- `SettingsRoute` has a saver that preserves origin, protocol, local ID, and moderation kind,
  rejects malformed values to `Main`, and waits for the account index before falling back.

Slice 02-J is complete and verified.

- `AppLanguage` lists all 15 concrete resource tags plus System default. Stored enum
  names are unchanged, so old `app-preferences.json` values still restore.
- `locales_config.xml` declares the same 15 BCP 47 tags. System default is a policy
  choice, not an XML locale.
- `LanguageSettingsScreen` is a scrollable radio group with one selected row.
  System-default copy comes from string resources.
- `LocalizationResourceTest` checks catalog parity across resources, enum, and XML.
  A new language qualifier fails until it has a selectable entry and XML config.
- `AppPreferencesRepositoryTest` checks persistence and restore by tag for every
  choice, plus safe fallback for unknown stored values.
- `LanguageSettingsScreenTest` checks row reachability on compact screens with
  large fonts, single-selection radio semantics, and exact regional selection.

Slice 02-K is complete and verified.

- `AppLanguage.fromNameOrDefault` is the single decoding rule for stored names.
  `FileAppPreferencesRepository` and `AppLocaleController.persistedLanguage` share
  it, so enum additions cannot diverge. The early bridge stays read-only.
- `MainActivity` never applies an unloaded System default. Locale work waits for
  `loaded` through `effectiveLanguageAfterLoad`.
- API 29 through 32 use the localized base context plus recreation on real change.
  API 33 and later reconcile through `LocaleManager`: an explicit platform locale
  wins on first upgrade, otherwise the loaded preference exports. Canonical-tag
  comparison converges without feedback loops. `onResume` picks up external
  Android App Languages changes. System default clears the override and follows
  the device language.
- `LocalizationResourceTest` requires `other`, valid locale quantities, and
  compatible placeholders instead of identical quantity sets. Partial catalogs
  stay intact under intentional fallback.
- `AppLocaleControllerTest` checks decoding, gating, reconciliation, localized
  contexts on API 29, per-locale translated resolution (including `es-419` and
  `yue-HK` keys), and default fallback for missing keys.
- The release resource table holds every required locale, including `es-419`
  (as `es-r419`) and `yue-HK` with distinct Cantonese values. Debug resolution
  is proved by the runtime tests against debug resources.
- The Language route round-trips through the 02-I saver, so locale-triggered
  recreation restores the Language page.

Slice 02-L is complete and verified.

- Cancellation rethrows in push reconcile, push disable (after local cleanup),
  the push connector, notification-settings retry and distributor loading, and
  the draft callbacks. Ordinary failures keep their mapped states.
- `NotificationsViewModel.stop()` cancels observation and request jobs, guards
  entry and publication, and runs on host disposal and clearance.
- `logs/BUGS.txt` corrects the stale stop claim and records why the settings
  models stay stop-free.
- Detail: `docs/agents/tasks/cancellation-and-shell-continuation.md`.

Plan 01 continuation is complete and verified.

- `ui/FeedHost.kt` owns the feed model, Home, Search, and Photo Grid contracts,
  post-interaction actions, composer inputs, publish, and the projection sink.
- `AppShellFixtures.app` is the explicit test-only assembly. `PalustrisApp` and
  `ConnectedApp` take required arguments. The preview passes explicit contracts.

Full gate passed: `test assembleRelease` (800+ unit tests) and `:app:lintDebug`.
Connected instrumentation, live-server, physical-device, and signed-release
checks remain unverified in this shell.
