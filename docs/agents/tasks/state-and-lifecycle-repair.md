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
| 02-G Thread overlays and projections | pending | |
| 02-H Home paging demand | pending | |
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

Slice 02-F is complete and verified. The document audit confirms:

- Feed and saved collections use the session-bound execution authority.
- Profile and thread reaction owners also reserve the same authority.
- Native Favorite and emoji React use separate families unless Favorite is reaction-backed.
- Saved collection membership removal stays hidden when refresh or paging returns a stale row.
- 02-A through 02-E remain covered by their recorded commits and focused tests.
- 02-G through 02-L and the Plan 01 skipped items remain pending.

No later slice has started.
