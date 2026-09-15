# Handoff

**Status:** current pointer. The durable record for the completed 01/02 series is
`docs/agents/tasks/decomposition-01-02-completion.md`. The durable record for the active
Plan 03 work is `docs/agents/tasks/plan03-protocol-notifications.md`.

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/plan03-protocol-notifications.md`.
3. `docs/decomposition_3/03.md`.
4. `docs/agents/tasks/decomposition-01-02-completion.md`.
5. `docs/agents/decomposition-01-02-acceptance-matrix.md`.
6. `docs/agents/app-shell-ownership.md` and `docs/agents/protocol-and-session-ownership.md`.
7. `logs/BUGS.txt`.
8. `git status` and recent commits.

## Current Position

- Completion slices C-01 through C-11, C-12a through C-12d4, C-13, C-14, and C-15 are
  committed. `L-01` is committed.
- Gate slices P-01 through P-07 are committed.
- Plan 03 is rebased at `b715430` and recorded in `docs/decomposition_3/03.md`. Slice `03-C`
  is committed at `3603ef3`. Slice `03-A1` is committed at `44b3483`. Slice `03-B1` is
  committed at `36eeeb9`. Slice `03-B2` is committed at `1c45afb`. Slice `03-D1` is committed
  in this slice. The last safe commit is `27a4b41` before the `03-D1` commit.
- Plan 01 and Plan 02 exit conditions are met. Device, live-server, and signed-release
  behavior stay unverified.
- Next slice: `03-D2` (activity, navigation, read-state, delivery fixtures, and the Room
  fixed-JSON test), then the remaining Plan 03 chunks. `03-A2` (NodeInfo discovery) stays
  open. 03-F and 03-I need maintainer approval before coding.

## Completed Plan 03 Slices

- R-01 rebase. Verification: source verified, no test ran.
- 03-C Misskey entity boundaries. Verification: `MisskeyIntegrationTest`, `CrossCuttingTest`.
- 03-A1 sentinel reaction probe removal. Verification: `MastodonCapabilityProbeTest`,
  `MastodonIntegrationTest`.
- 03-B1 runtime capability evidence and reactive publication. Verification:
  `MastodonIntegrationTest`, `MastodonCapabilityProbeTest`, `MisskeyIntegrationTest`,
  `CrossCuttingTest`, `SignInScreenTest`, then `test assembleRelease` and `:app:lintDebug`.
- 03-B2 revision-guarded capability publication and bounded refresh retry. Verification:
  `MastodonIntegrationTest`, `CrossCuttingTest`, `MisskeyIntegrationTest`,
  `ConnectedSessionContextTest`, then `test assembleRelease` and `:app:lintDebug`.
- 03-D1 notification state-envelope fixtures and file-store contract. Verification:
  `NotificationJsonCodecTest`, then `test assembleRelease` and `:app:lintDebug`.
- Run `test assembleRelease` and `:app:lintDebug` after each remaining slice.

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
- 03-F and 03-I need maintainer approval before implementation.
