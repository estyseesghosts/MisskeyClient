# Task State: State And Lifecycle Repair

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
| 02-I Settings commands and routes | pending | |
| 02-J Locale catalog | pending | |
| 02-K Locale lifecycle | pending | |
| 02-L Cancellation and integration | pending | |
| 01 skipped items | pending | |

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

Slice 02-H is complete and verified.

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
| Notification ViewModel teardown | `NotificationsViewModel` still has no `stop()` | Defer to 02-L. |
| Settings ViewModel teardown | `SettingsViewModel` still has no `stop()` | Defer to 02-I and 02-L. |
| Extract `FeedHost` and `SavedCollectionsHost` | Not present | Unblocked after `aa19bc4`; extract as a Plan 01 continuation. |
| Remove production no-argument app construction used by tests | Still present | Defer to 01-H after 02 stops adding suites. |

Conclusion: the remaining skipped items are not ready. Finish 02-I through 02-L first,
then continue the Plan 01 host extraction and 01-H test construction. No skipped item is
an active defect.

Slice 02-H is complete and verified. 02-I through 02-L remain pending.
