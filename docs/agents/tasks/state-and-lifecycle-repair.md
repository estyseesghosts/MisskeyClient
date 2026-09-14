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
| 02-B DM visible state | completed | pending commit |
| 02-C DM durable state | pending | |
| 02-D Notification request context | pending | |
| 02-E Moderation lifetimes | pending | |
| 02-F Mutation families | pending | |
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

Slice 02-B is complete and verified. The implementation commit is the next
operation. No later slice has started.
