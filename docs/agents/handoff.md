# Handoff

**Status:** current pointer. The durable record is
`docs/agents/tasks/decomposition-01-02-completion.md`.

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/decomposition-01-02-completion.md`.
3. `docs/agents/decomposition-01-02-acceptance-matrix.md`.
4. `docs/agents/app-shell-ownership.md` and `docs/agents/protocol-and-session-ownership.md`.
5. `logs/BUGS.txt`.
6. `git status` and recent commits.

## Current Position

- Completion slices C-01 through C-11, C-12a through C-12d4, C-13, and C-14 are committed.
  `L-01` is committed.
- The last safe commit is `PENDING` (C-14 record commit).
- Plan 01 and Plan 02 exit conditions are met. Every acceptance row is implemented and
  test verified. Device, live-server, and signed-release behavior stay unverified.
- The next slice is **C-15, dead-scaffolding cleanup**. It is behavior-neutral and not
  part of the exit. The symbol list is under Next Cleanup below.
- `test assembleRelease` and `:app:lintDebug` pass at the C-14 behavior commit.

## Next Cleanup

Recorded as slice C-15 in the task-state file. Remove the dead scaffolding that earlier extraction
waves left behind. Do not combine it with a behavior change. Every symbol must have no caller before
deletion.

- `ui/profile/ProfileScreen.kt`: `LegacyLargeProfilePresentation`, `LegacyProfileHeader`.
- `ui/media/MediaViewerScreen.kt`: `LegacyMediaTransitionImage`, `LegacyMediaTransitionImageCanvas`.
- `ui/MarkdownText.kt`: `MarkdownPostText`.
- `ui/navigation/AppBackHandler.kt` and `ui/navigation/BackNavigationState.kt`.
- `ui/Components.kt`: `SectionTabs`.
- `ui/AccountSyncCoordinator.kt`: compatibility aliases. Update the `FeedViewModel` callers to the
  `data.notifications` names, then delete the file.

Verify with the focused Compose suites, `test assembleRelease`, and `:app:lintDebug`.

## Process Rules

- One slice, one behavior, one commit. Then a record commit.
- Rewrite this handoff after each completed slice, per `AGENTS.md`.
- Keep the acceptance matrix and the ownership pages current in the same slice.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Keep protocol behavior in adapters. Keep account secrets and tokens out of presentation contracts.
- Do not change a stored format without a migration in the same slice.
- Do not use subagents unless the user asks.

## Known Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
