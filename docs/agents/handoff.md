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
  at `e40ef87`. Slice `03-A2` is committed at `c6bd9ff`. Slice `03-D2` is committed at
  `0e0d8c4`. Slice `03-D3` is committed at `5d1f8b2`. Slice `03-E` is committed at `861e457`.
  Slice `03-F1` is committed at `f33607e`. Slice `03-F2` is committed at `15ba26b`. Slice
  `03-F3` is committed at `9dac59b`. Slice `03-F4` is committed at `d3e1323` and test verified.
  Slice `03-G` is committed at `136c4ae` and test verified. Slice `03-H` is committed
  at `fc5cb6e` and test verified.
  The last safe commit is `fc5cb6e`.
- The maintainer approved the 03-F reset behavior and the 03-I visibility migration on
  2026-09-15. The accepted policy is development-only discard: do not migrate old notification
  data. Discard unreadable or incompatible local state and require reauthentication when needed.
- Plan 01 and Plan 02 exit conditions are met. Device, live-server, and signed-release
  behavior stay unverified.
- Next slice: `03-I` (repair visibility separately). `03-J` follows. 03-F, 03-G, and
  03-H are complete. 03-I is approved to code.
- Unrelated documentation and archive changes appeared in the worktree during 03-E. They are
  not part of any committed Plan 03 slice and were left untouched.

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
- 03-A2 NodeInfo discovery fallback. Verification: `MastodonNodeInfoDiscoveryTest`,
  `MastodonCapabilityProbeTest`, `MastodonIntegrationTest`, `MisskeyIntegrationTest`, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-D2 activity, navigation, read-state, and delivery fixtures plus the Room fixed-JSON test.
  Verification: `NotificationJsonCodecTest`, `NotificationRoomStoreFixtureTest`, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-D3 posts, interaction counts, unread state, settings, push, checkpoints, malformed
  structure, and known-omission fixtures. Verification: `NotificationJsonCodecTest` (39 tests)
  and `NotificationRoomStoreFixtureTest` (3 tests), then `test assembleRelease` and
  `:app:lintDebug`.
- 03-E notification codec ownership. Every recursive helper moved to `NotificationJsonCodec`
  and made private. Verification: `NotificationJsonCodecTest` (40 tests) and
  `NotificationRoomStoreFixtureTest` (4 tests), a moved-text identity check, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-F1 typed notification store read result. `NotificationStore.read` returns Absent, Readable,
  Corrupt, or Unavailable. Verification: `NotificationJsonCodecTest` (43 tests),
  `NotificationRoomStoreFixtureTest` (4 tests), related repository and settings suites, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-F2 receiving-account ownership validation. A foreign notification, group, delivery,
  checkpoint, push, or dismissal origin is Corrupt. Verification: `NotificationStateOwnershipTest`
  (8 tests), `NotificationJsonCodecTest`, `NotificationRoomStoreFixtureTest`, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-F3 recoverable storage health. `NotificationStorageHealth` separates `Healthy`, `Recoverable`,
  and `Unavailable` outside the stored payload. A non-healthy account blocks page ingestion, the
  baseline, local mutations, delivery claims and finishing, settings writes, and push registration
  writes. The inbox and settings surfaces show the failure and an explicit retry. Verification:
  `NotificationStorageRecoveryTest` (5 tests), the focused notification suites, then
  `test assembleRelease` and `:app:lintDebug`.
- 03-F4 future-format refusal, account-local reset, and Room schema history.
  `NotificationStoreRead` and `NotificationStorageHealth` add `Unsupported`. Both stores keep a
  newer-format payload untouched and block writes. `NotificationRepository.reset` advances the
  account generation and writes an empty readable state so the legacy importer does not reimport.
  The settings surface adds a confirmed reset action. `NotificationDatabase` exports schema history
  to `app/schemas`. Verification: `NotificationDatabaseSchemaTest` (4 tests),
  `NotificationSettingsStorageResetTest` (2 tests), `NotificationJsonCodecTest` (46 tests),
  `NotificationRoomStoreFixtureTest` (7 tests), `NotificationStorageRecoveryTest` (9 tests), the
  related notification suites, then `test assembleRelease` and `:app:lintDebug`.
- 03-G durable write acceptance. Every mutation commits through `commitWrite`: compute from
  committed state, write on the injected IO dispatcher, publish only while the writer is still
  current. A failed write marks the account `Unavailable`, publishes nothing, and returns
  failure. Per-account serialization, explicit claim/dismiss/acknowledge/push/remove failure
  paths, and best-effort row deletion. Verification: `NotificationWriteFailureTest` (8 tests),
  the focused notification suites, then `test assembleRelease` and `:app:lintDebug`.
- 03-H restart-safe legacy import. The Room row is authoritative over the legacy file. A
  readable legacy state is saved to Room before the marker is written. Corrupt, future-format,
  and transient failures stay unmarked for retry. Deletion seals the legacy file so a removed
  account cannot resurrect old state. Cancellation propagates. Verification:
  `NotificationLegacyImportTest` (11 tests), the focused notification suites, then
  `test assembleRelease` and `:app:lintDebug`. `:app:assembleDebugAndroidTest` still fails in
  the pre-existing `Api29StartupInstrumentedTest`; the repaired
  `RoomNotificationStoreInstrumentedTest` compiles.
- Run `test assembleRelease` and `:app:lintDebug` after each remaining slice.

## Process Rules

- One slice, one behavior, one commit. Then a record commit. Committing each verified slice is
  required, not optional. The slice commit happens as soon as its tests are green. Do not commit a
  slice whose tests are not green, and do not leave a green slice uncommitted.
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
- No approval blocker remains for 03-F or 03-I.
