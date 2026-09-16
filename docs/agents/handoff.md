# Handoff

**Status:** current pointer. The durable record for the completed 01/02 series is
`docs/agents/tasks/decomposition-01-02-completion.md`. The durable record for the completed
Plan 03 work is `docs/agents/tasks/plan03-protocol-notifications.md`. The corrected audit of
Plans 01, 02, and 03 is `docs/decomposition_3/03_corrected.md` (git-ignored planning material,
do not force-add). The durable record for the completed S1 work is
`docs/agents/tasks/palustrisapp-decomposition.md`. The durable record for
the completed P1 work is `docs/agents/tasks/ui-package-migration.md`. The
durable record for the completed Q1 work is
`docs/agents/tasks/q1-static-analysis.md`. The durable record for the
active T1 work is `docs/agents/tasks/t1-test-mirror.md`.

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/t1-test-mirror.md`.
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
  call. The CI repair slice is `b62f8c6`.
- `v0.2.7` is released as a GitHub pre-release with the signed
  `app-release.apk`, title `Beeline 0.2.7`, notes `Making the pain
  worth it.` Tag `v0.2.7` points at `45e2275`. Release run
  `35054197234` is green. The handoff record commit is `11d5ba1`. The
  last safe commit is `11d5ba1`.
- Q1 (ktlint gate and wildcard removal) is complete. Slices: `39cfec3`
  (gate and baseline), `6c3f4f5` (clear the icon commit findings),
  `e5c77ad` (restore the Direct messages label and cover the unfollow
  confirmation), `99bc0ed` (main wildcard imports), `37d8cac` (test
  wildcard imports). The baseline holds zero `no-wildcard-imports`
  entries. `ktlintCheck`, `test assembleRelease`, and `lintDebug` pass.
  The durable record is `docs/agents/tasks/q1-static-analysis.md`.
- Plan 01 and Plan 02 exit conditions are met except blocked device
  verification.
- T1a (merge the duplicate test classes into mirrored packages) is
  committed and test verified. `ui/emoji/EmojiCatalogViewModelTest`
  holds 8 tests and `ui/posts/PostActionOwnerTest` holds 5 tests. The
  two root copies and the misplaced `ui/EmojiCatalogViewModelTest` are
  gone. Focused tests, `test assembleRelease`, and `ktlintCheck` pass.
  The slice commit is `7018105`.
- T1b (move 43 data and domain owner tests into mirrored packages) is
  committed and test verified. Each move changes the package line only.
  The slice also regenerates `app/ktlint-baseline.xml` because the moves
  exposed pre-existing style debt under new paths. `test assembleRelease`
  and `ktlintCheck` pass. The slice commit is `f65a10a`.
- T1c (move 17 UI feature tests into mirrored packages) is committed and
  test verified. Physical files and package lines move together. The 8
  fixture users gain `AppShellFixtures`, `ComposerFeatureFixtures` or
  `HomeFeatureFixtures`, and `MainActivity` imports because those
  declarations stay in the root package. The slice also regenerates
  `app/ktlint-baseline.xml` under the new paths. `RichTextModelTest`
  moves to `domain` because it exercises only domain classes. Focused
  tests, `test assembleRelease`, and `ktlintCheck` pass. The slice commit
  is `42e85e7`.
- T1d (move the three shared test fixtures into mirrored packages) is
  committed and test verified. `AppShellFixtures` -> `ui.shell`,
  `ComposerFeatureFixtures` -> `ui.composer`, `HomeFeatureFixtures` ->
  `ui.feed`. The eight users point at the new packages and drop the now
  same-package imports. The slice also regenerates
  `app/ktlint-baseline.xml` under the new paths. Focused tests,
  `test assembleRelease`, `ktlintCheck`, and `lintDebug` pass. The slice
  commit is `404b356`.
- T1e (move 34 single-owner root tests into mirrored packages) is
  committed and test verified. The tests move to `ui.localization`,
  `ui.session`, `ui.directmessages`, `ui.feed`, `ui.settings`, `ui.large`,
  `ui.media`, `ui.motion`, `ui.notifications`, `ui.photogrid`,
  `ui.profile`, `ui.saved`, `ui.thread`, `ui.navigation`, `ui.composer`,
  flat `ui`, and `data.notifications.push`. Eight screen tests gain a
  `MainActivity` import. The slice also regenerates
  `app/ktlint-baseline.xml` under the new paths. Focused tests,
  `test assembleRelease`, `ktlintCheck`, and `lintDebug` pass. The slice
  commit is `00a49ff`.
- T1f (move the adapter-specific source tests into mirrored packages) is
  committed and test verified. `MastodonIntegrationTest`,
  `MastodonNotificationSyncTest`, and `MastodonSourceContractTest` move
  to `data.mastodon`. `MisskeyIntegrationTest` moves to `data.misskey`.
  The shared `SocialSourceContractTest` base stays at the root package.
  Twelve cross-adapter, cross-cutting, and platform tests also stay at
  the root package because no single production package owns them. The
  slice also regenerates `app/ktlint-baseline.xml`. Focused tests,
  `test assembleRelease`, `ktlintCheck`, and `lintDebug` pass. The slice
  commit is `aecab82`. T1 is complete.

## Next Slice

T1 — Done. The next unrelated task is V1: repair
`RoomNotificationStoreInstrumentedTest` and de-flake the two known timing
tests. The `Api29StartupInstrumentedTest` repair is already done in
`b62f8c6`. Record blocked device checks honestly. Then the Plan 04 rebase.
The fully-qualified-name cleanup stays deferred to its own task.

## Remaining Migration Queue

The S1, P1, and Q1 series are complete. T1 is in progress. Do these in
order. Each needs its own task-state file and verification.

1. Q1 — Done. ktlint gate with a baseline. Wildcard imports are gone.
2. T1 — Done. T1a through T1f are complete. Merge of the two
    duplicate-named test classes (`EmojiCatalogViewModelTest`,
    `PostActionOwnerTest`) is in `7018105`. Data and domain owner moves
    are in `f65a10a`. UI feature moves are in `42e85e7`. Fixture moves
    are in `404b356`. Single-owner root test moves are in `00a49ff`.
    Adapter-specific source test moves are in `aecab82`.
3. V1 — Repair `RoomNotificationStoreInstrumentedTest` and de-flake the
   two known timing tests. The `Api29StartupInstrumentedTest` repair is
   already done in `b62f8c6`. Record blocked device checks honestly.
4. Plan 04 rebase (`docs/decomposition_3/04.md`), then implementation.
5. Device, live-server, and signed-release verification when a device and
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
