# Handoff

**Status:** current pointer. The durable record for the completed 01/02 series is
`docs/agents/tasks/decomposition-01-02-completion.md`. The durable record for the completed
Plan 03 work is `docs/agents/tasks/plan03-protocol-notifications.md`. The corrected audit of
Plans 01, 02, and 03 is `docs/decomposition_3/03_corrected.md` (git-ignored planning material,
do not force-add). The durable record for the completed S1 work is
`docs/agents/tasks/palustrisapp-decomposition.md`. The durable record for
the active P1 work is `docs/agents/tasks/ui-package-migration.md`.

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/ui-package-migration.md`.
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
  is committed and test verified. The slice commit is `101be82`.
- S1c (extract overlay, dialog, and bubble hosting into
  `ui/shell/ShellOverlayHost.kt`) is committed and test verified. The
  slice commit is `4c97d43`.
- S1 (split `PalustrisApp.kt`) is complete. The shell is 622 lines and
  owns navigation and placement. Feature state lives in the overlay
  holder, the destination content, and the overlay host.
- P1f (group the destination callbacks into post, draft, and
  navigation bundles) is committed and test verified. `ShellDestinationContent`
  takes 27 parameters instead of 38. The slice commit is `138a404`.
- P1 (finish the `ui/` package migration and group the destination
  callbacks) is complete. Every slice P1a through P1f is committed and
  test verified. The durable record is
  `docs/agents/tasks/ui-package-migration.md`.
- CI is green on run `35053164957`: unit/lint/build plus the API 29 smoke
  job, 10 of 10 instrumented tests pass. Fixes: fresh AVD, 3-attempt
  single-line retry loop, KVM hardware acceleration, default system
  image without Google APIs, repaired `Api29StartupInstrumentedTest`
  call. The slice commit is `b62f8c6`. The last safe commit is `b62f8c6`.
- Next: move tag `v0.2.7` onto the green tree and push it so the
  Release workflow builds the signed pre-release. The `release`
  environment needs a human approval before the signed job runs.
- Plan 01 and Plan 02 exit conditions are met except blocked device
  verification.

## Next Slice

Q1 — Add ktlint or detekt with a baseline. Fix the wildcard imports
and the fully-qualified names left behind by the S1 and P1 moves (12
wildcard imports and several fully-qualified names, including the
`ui.*` path/package mismatches found in P1b-P1d). Smallest change with
the broadest payoff. Open a dedicated task-state file with its own
verification before implementation. Then T1, V1, and the Plan 04 rebase
in order.

## After S1

Do these in order. Each needs its own task-state file and verification.

1. P1 — Finish the `ui/` package migration. Move the flat feature files
   (`FeedViewModel`, `HomeFeed`, search, Photo Grid, saved collections,
   `AccountManager` out of `ui/`) into feature packages. Behavior-neutral.
   Group the `ShellDestinationContent` branch callbacks into narrow param
   bundles to replace the 37-parameter signature.
2. Q1 — Add ktlint or detekt with a baseline. Fix the 12 wildcard imports
   and the fully-qualified names. Smallest change with the broadest payoff.
3. T1 — Mirror test packages to production packages. Merge the two
   duplicate-named test classes (`EmojiCatalogViewModelTest`,
   `PostActionOwnerTest`).
4. V1 — Repair `Api29StartupInstrumentedTest` and
   `RoomNotificationStoreInstrumentedTest`. De-flake the two known timing
   tests. Record blocked device checks honestly.
5. Plan 04 rebase (`docs/decomposition_3/04.md`), then implementation.
6. Device, live-server, and signed-release verification when a device and
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
