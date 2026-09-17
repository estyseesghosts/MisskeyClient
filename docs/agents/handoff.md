# Handoff

**Status:** current pointer. The durable record for the completed wide detail interaction fix is
`docs/agents/tasks/wide-detail-interaction-fix.md`. The durable record for the completed 01/02 series is
`docs/agents/tasks/decomposition-01-02-completion.md`. The durable record for the completed
Plan 03 work is `docs/agents/tasks/plan03-protocol-notifications.md`. The corrected audit of
Plans 01, 02, and 03 is `docs/decomposition_3/03_corrected.md` (git-ignored planning material,
do not force-add). The durable record for the completed S1 work is
`docs/agents/tasks/palustrisapp-decomposition.md`. The durable record for
the completed P1 work is `docs/agents/tasks/ui-package-migration.md`. The
durable record for the completed Q1 work is
`docs/agents/tasks/q1-static-analysis.md`. The durable record for the
completed T1 work is `docs/agents/tasks/t1-test-mirror.md`. The active Plan 04
work is `docs/agents/tasks/plan04-utility-retention.md` (04-A through 04-D and
04-E1 through 04-E3 are complete; 04-F through 04-K remain). A
cleanup-window audit added 04-K and prerequisites to 04-E, 04-H, and 04-J. The
Plan 04 source is
`docs/decomposition_3/04.md` (git-ignored planning material, do not force-add).

## Where To Start

Read these in order. Treat the repository as the authority.

1. `AGENTS.md`.
2. `docs/agents/tasks/plan04-utility-retention.md`.
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
- 04-A (complete external-link ownership) is committed and test
  verified. `ExternalLinkHandler.open` holds the full browser operation,
  the `ui.openExternal` declaration and imports are gone, and the eight
  feature calls use the handler. `ExternalLinkHandlerTest` is new. The
  slice commit is `ee52ba9`.
- 04-B (separate the Unicode catalog from picker presentation) is
  committed and test verified. `DefaultUnicodeEmojis` moves unchanged to
  `ui/emoji/DefaultUnicodeEmoji.kt`. Both consumers stay in the
  `ui.emoji` package, so no caller changed. `DefaultUnicodeEmojiTest`
  pins the order with a fixed snapshot resource. The slice commit is
  `6a87c76`.
- 04-C (replace the emoji URL locks with fixed stripes) is committed and
  test verified. `EmojiAssetStore.urlLocks` is a fixed 64-lock array.
  `stripeIndex` uses `hashCode() and mask`, so the index stays
  nonnegative without `abs`. Canonicalization stays before
  coordination. Revised `EmojiAssetStoreTest` covers concurrent
  same-URL requests, deliberate collisions, independent stripes,
  failure and retry, cancellation, and 10,000 unique URLs against the
  constant structure. The slice also regenerates
  `app/ktlint-baseline.xml` for the shifted annotated declaration. The
  slice commit is `633cd7a`.
- 04-D (enforce emoji mapping and byte retention with reader leases) is
  committed and test verified. `EmojiAssetStore` bounds inactive URL
  mappings at 4,096 and inactive content at 128 MiB through injectable
  constructor limits. Mapping eviction uses the referenced asset
  `lastUsedEpochMillis` access order. `EmojiAssetLease` is closeable and
  `EmojiAssetFetcher` passes it as the Coil `ImageSource` closeable, so
  Coil releases it on decode success, failure, or cancellation. Open
  leases and writes are exempt from eviction, so overage is temporary.
  The wiki privacy and architecture pages record the rule. The slice
  commit is `f0735df`.
- 04-E1 (require the shared direct-message write authority) is committed
  and test verified. `DirectMessageWriteAuthority` is a required
  constructor dependency of `DirectMessageViewModel` and
  `DirectMessageRepository`. Source verified: the Hilt-assisted factory
  already injected the singleton, so production wiring was shared; the
  private defaults only fired on direct construction. The slice removes
  both. `DirectMessageViewModelTest.revokedSharedAuthorityStopsThe
  ViewModelWrite` covers the revoked path. The ownership page records the
  rule. The slice commit is `b04b2e8`.
- 04-E2 (carry a validated thread anchor and remove the DM last-post cache)
  is committed and test verified. `DirectThreadRequest` carries the
  conversation identity and a separate post anchor. The repository fills
  the anchor from the stored conversation. The Mastodon adapter loads the
  anchor through `GET /api/v1/statuses/:id` and its context, never the
  undocumented conversation GET, and normalizes 403, 404, and 410. The
  Misskey adapter keeps its reply-rooted root. `directLastPosts` and the
  send-path insertion are gone. Its slice and this record share one
  commit.
- 04-E3 (give provisional conversations an explicit identity and send mark read
  only for verified server identity) is committed and test verified.
  `ConversationIdentity { Verified, Provisional }` is in the domain.
  `DirectConversation` carries a required `identity`. The Room entity adds
  `identity TEXT NOT NULL DEFAULT 'PROVISIONAL'`; the database is version 2,
  exports its schema, registers `MIGRATION_1_2`, and keeps the released version-1
  schema as `1.json`. The repository calls `markConversationRead` only for a
  verified conversation, so no guessed identity reaches the server. A legacy or
  unrecognized identity decodes as provisional. Its slice and this record share
  one commit.
- Localization string extraction is complete through slice 4 (data layer error
  messages). `data/AppMessages.kt` is the Context-backed resolver for data-layer
  owners. `ui/SourceErrorMessage.kt` localizes feature identifiers. The durable
  record is `docs/agents/tasks/localization-string-extraction.md`. Its slice and
  this record share one commit.
- The Profile Liked tab is committed and test verified. `ProfileTimelineTab.Liked`
  and `ProfileCategory.Liked` exist. The category order is Posts, Replies, Media,
  Reposts, Liked. The Liked tab is available for the signed-in account on either
  protocol, for another Misskey account, and not for another Mastodon account.
  The orphaned global Likes page and its model are removed: `LocalPage.Likes`,
  `LikesContract`, `SavedPostsCollection`, the liked `SavedPostsViewModel`
  instance, `LargePostOrigin.Liked`, and `SocialSource.likedPosts` are gone.
  `SavedPostsViewModel` owns bookmarks only. The durable record is
  `docs/agents/tasks/profile-liked-tab.md`.
- The Profile Featured tab is committed and test verified. `ProfileCategory.Featured`
  leads the category row only when the profile has more than one pinned post. A
  single pinned post shows at the top of the Posts feed with no Featured tab.
  Pinned posts stay out of every other profile feed. Featured renders the pinned
  posts the profile already loaded, so it adds no request and no pager tab. The
  durable record is `docs/agents/tasks/profile-featured-tab.md`.

## Next Slice

1. Resume Plan 04 at 04-F (bound idle Misskey thread continuations). Then
   implement 04-G through 04-K in the recorded order. 04-K removes the dead
   profile paging authority and bounds the cursor sets. 04-J runs last and
   depends on 04-F through 04-I and 04-K. The durable record is
   `docs/agents/tasks/plan04-utility-retention.md`.

The wide detail interaction fix is complete in commit `f8a2cdc`. Its focused test passes.

V1 stays device-blocked: repair `RoomNotificationStoreInstrumentedTest` and
de-flake the two known timing tests when a device or emulator exists. The
`Api29StartupInstrumentedTest` repair is already done in `b62f8c6`. Record
blocked device checks honestly. The fully-qualified-name cleanup stays deferred
to its own task.

## Remaining Migration Queue

The S1, P1, and Q1 series are complete. T1 is complete. Plan 04 is in
progress. Do these in order. Each needs its own task-state file and
verification.

1. Q1 — Done. ktlint gate with a baseline. Wildcard imports are gone.
2. T1 — Done. T1a through T1f are complete. Merge of the two
    duplicate-named test classes (`EmojiCatalogViewModelTest`,
    `PostActionOwnerTest`) is in `7018105`. Data and domain owner moves
    are in `f65a10a`. UI feature moves are in `42e85e7`. Fixture moves
    are in `404b356`. Single-owner root test moves are in `00a49ff`.
    Adapter-specific source test moves are in `aecab82`.
3. V1 — Blocked on a device or emulator. Repair
   `RoomNotificationStoreInstrumentedTest` and de-flake the two known timing
   tests. The `Api29StartupInstrumentedTest` repair is already done in
   `b62f8c6`. Record blocked device checks honestly.
4. Plan 04 — In progress. Rebase recorded as a slice plan in
   `docs/agents/tasks/plan04-utility-retention.md`. 04-A is complete at
   `ee52ba9`, 04-B at `6a87c76`, 04-C at `633cd7a`, 04-D at `f0735df`,
   04-E1 at `b04b2e8`, 04-E2 at `92490d1`, and 04-E3 with this handoff. A
   cleanup-window audit added 04-K and prerequisites to 04-E, 04-H, and 04-J:
   private DM write-authority construction, private registration-cache
   construction, unreleased authority maps, an unremoved `writeLocks` map, and
   dead profile paging members. 04-E1 corrected the write-authority finding:
   production already shared the singleton. Implement slices 04-F through 04-K
   in the recorded order. Decisions gate 04-H, 04-J, and 04-K.
5. Device, live-server, and signed-release verification when a device and
   signing inputs exist.

## Process Rules

- One slice, one behavior, one commit. Include the task-state file, the task
  log, the handoff, and the affected documentation in that commit. Do not make a
  separate record commit. Do not commit a slice whose tests are not green, and do
  not leave a green slice uncommitted.
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
- The planning material under `docs/decomposition_3/` is git-ignored. Do not
  force-add `01.md`, `02.md`, `03.md`, `03_corrected.md`, or `04.md`. The Plan
  04 slice plan lives in the tracked `docs/agents/tasks/plan04-utility-retention.md`.
- The worktree holds the untracked `appsvg/` directory. Do not commit it.
- The last Plan 04 slice is 04-E3, and no Plan 04 slice is in progress. The
  localization task has slices 1 through 4 complete and is paused. The last safe
  commit is the current `HEAD` (`git log -1`).
- All non-English string catalogs are removed from the app for now. Two
  localization tests were relaxed to tolerate missing catalogs and must be
  tightened again when the catalogs return:
  `AppLocaleControllerTest.everyLocaleResolvesATranslatedValueOrFallback` and
  `LocalizationResourceTest.localeCatalogMatchesResourcesEnumAndAndroidConfig`.
- The residual 03-G ordering risk (disk write after revocation, before row
  deletion) stays in the Plan 03 task state.
