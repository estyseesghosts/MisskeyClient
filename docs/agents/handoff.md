# Handoff

**Status:** current pointer. The durable record for the completed 01/02 series is
`docs/agents/tasks/decomposition-01-02-completion.md`. The durable record for the completed
Plan 03 work is `docs/agents/tasks/plan03-protocol-notifications.md`. The corrected audit of
Plans 01, 02, and 03 is `docs/decomposition_3/03_corrected.md` (git-ignored planning material,
do not force-add). The durable record for the active S1 work is
`docs/agents/tasks/palustrisapp-decomposition.md`.

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/palustrisapp-decomposition.md`.
3. `docs/decomposition_3/03_corrected.md`.
4. `docs/agents/app-shell-ownership.md` and `docs/agents/protocol-and-session-ownership.md`.
5. `docs/agents/decomposition-01-02-acceptance-matrix.md`.
6. `logs/BUGS.txt`.
7. `git status` and recent commits.

## Current Position

- Plan 03 is complete. Every chunk from `03-A1` through `03-J` is committed
  and test verified. The last Plan 03 slice is `5609f3c`.
- The corrected audit is written at `docs/decomposition_3/03_corrected.md`.
  Its main finding: Plan 01 Step 13 is only half met because
  `ui/PalustrisApp.kt` is still 1078 lines and still coordinates feature
  state. All other Plan 01, 02, and 03 slices are complete except blocked
  device verification.
- Stale status claims are fixed outside `docs/decomposition_3/`: the
  documentation inventory reclassifies `01.md`, `02.md`, and `03.md` as
  historical, the Plan 03 task is historical in the agent index, and the
  root README no longer calls block, mute, and report unimplemented.
  `01.md`, `02.md`, and `03.md` themselves were not touched.
- S1a (extract transient overlay state into `ui/shell/ShellOverlayPresenter.kt`)
  is committed and test verified. The slice commit is `ced43f2`.
- S1b (extract the destination tree into `ui/shell/ShellDestinationContent.kt`)
  is approved and not started. The task-state file names S1b as the current
  slice. The last safe commit is `ced43f2`.
- Plan 01 and Plan 02 exit conditions are met except the S1 remainder of
  Step 13 and blocked device verification.

## Next Slice

S1b — Extract the destination tree into
`ui/shell/ShellDestinationContent.kt` (destination scaffold, destination
branches, local pages, notifications destination, profile, search, Photo
Grid, Home wiring). Keep the `PalustrisApp` signature and
`AppShellFixtures.app()` stable. Keep saveable holders and scroll states
in the shell unless the slice proves a move safe. Run the six shell suites
plus the full gate. Commit only when green. Full scope, files, and
verification commands are in `docs/agents/tasks/palustrisapp-decomposition.md`.
Do not duplicate that file here.

## After S1

Do these in order. Each needs its own task-state file and verification.

1. S1b and S1c from the S1 task-state file.
2. P1 — Finish the `ui/` package migration. Move the flat feature files
   (`FeedViewModel`, `HomeFeed`, search, Photo Grid, saved collections,
   `AccountManager` out of `ui/`) into feature packages. Behavior-neutral.
3. Q1 — Add ktlint or detekt with a baseline. Fix the 12 wildcard imports
   and the fully-qualified names. Smallest change with the broadest payoff.
4. T1 — Mirror test packages to production packages. Merge the two
   duplicate-named test classes (`EmojiCatalogViewModelTest`,
   `PostActionOwnerTest`).
5. V1 — Repair `Api29StartupInstrumentedTest` and
   `RoomNotificationStoreInstrumentedTest`. De-flake the two known timing
   tests. Record blocked device checks honestly.
6. Plan 04 rebase (`docs/decomposition_3/04.md`), then implementation.
7. Device, live-server, and signed-release verification when a device and
   signing inputs exist.

## Process Rules

- One slice, one behavior, one commit. Then a record commit. Do not commit a
  slice whose tests are not green, and do not leave a green slice uncommitted.
- Rewrite this handoff after each completed slice, per `AGENTS.md`.
- Keep the ownership pages current in the same slice.
- Stage only files that belong to the slice. Preserve unrelated worktree changes.
- Keep `PalustrisApp`'s signature and `AppShellFixtures.app()` stable
  through S1. Change internals only.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Keep protocol behavior in adapters. Keep account secrets and tokens out of presentation contracts.
- Do not change a stored format without a migration in the same slice.
- Do not use subagents unless the user asks.

## Known Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- The worktree holds another author's uncommitted drafts (`docs/archive/`,
  wiki stubs, `documentation-inventory.md`, `gradle-no-daemon.md`, images).
  Do not commit them under S1. The S1 audit files from this task
  (`03_corrected.md`, inventory rows, index updates, README line) are also
  uncommitted for the same reason; `03_corrected.md` is git-ignored and
  must not be force-added.
- The residual 03-G ordering risk (disk write after revocation, before row
  deletion) stays in the Plan 03 task state.
