# Objective

Mirror test packages to production packages. Merge the two duplicate test class names. Keep behavior unchanged. Keep the repository green.

This task is larger than one safe implementation slice. It divides into T1a through T1d below. The fully-qualified-name cleanup stays deferred to its own task.

# Invariants

- Keep moves behavior-neutral. Move files and fix package lines and imports only. No logic change, no signature change, no string change.
- Keep protocol behavior in adapters. No stored-format change.
- Keep `PalustrisApp` signature and `AppShellFixtures.app()` stable unless a later slice requires a change.
- One slice, one behavior, one commit. Commit only when green.
- Stage only files that belong to the slice. Preserve unrelated worktree changes, including `appsvg/`.
- Use Beeline in user-facing text. Keep the codename out of user-facing content.

# Decisions

- Test homes mirror production homes. `ui/emoji/` owns emoji catalog tests. `ui/posts/` owns post action tests.
- Merge first, then move in batches. The two duplicate names block mirroring because both copies would land in the same package.
- Final homes for the duplicates:
  - `app/src/test/java/me/foxtails/palustris/ui/emoji/EmojiCatalogViewModelTest.kt` with package `me.foxtails.palustris.ui.emoji`.
  - `app/src/test/java/me/foxtails/palustris/ui/posts/PostActionOwnerTest.kt` with package `me.foxtails.palustris.ui.posts`.
- Keep all existing test methods. The root `EmojiCatalogViewModelTest` holds 2 cancellation and failure tests. The `ui` copy holds 6 catalog behavior tests. The merged class holds 8 tests. The root `PostActionOwnerTest` holds 3 retire authority tests. The `ui/posts` copy holds 2 session and mute tests. The merged class holds 5 tests.
- Batch the remaining flat tests by owner after the merges. Do not move 100 files in one commit.

# Completed

- T1a — Merged the two duplicate test classes into mirrored packages. Commit `7018105`. `ui/emoji/EmojiCatalogViewModelTest.kt` holds 8 tests. `ui/posts/PostActionOwnerTest.kt` holds 5 tests. Focused tests, `test assembleRelease`, and `ktlintCheck` pass.
- T1b — Moved 43 data and domain owner tests into mirrored packages. Commit `f65a10a`. Each move changes the package line only. The moves exposed pre-existing style debt under new paths, so the slice also regenerates `app/ktlint-baseline.xml` (old root paths out, new mirrored paths in, zero `no-wildcard-imports` entries). `test assembleRelease` and `ktlintCheck` pass. UI subjects (`NotificationLaunchHost`, `NotificationRouteResolver`, `NotificationSettingsStorageReset`, `SettingsRouteRestoration`, `PostInteractionExecutionAuthority`) stay in root for T1c. Multi-adapter contracts stay in root for T1d.

# Current slice

T1c is next. Move UI feature tests into mirrored packages.

# Files involved

T1c moves UI-subject tests. Verified UI subjects: `NotificationLaunchHostTest`,
`NotificationRouteResolverTest`, `NotificationSettingsStorageResetTest`,
`SettingsRouteRestorationTest`, `PostInteractionExecutionAuthorityTest`,
`PostInteractionMutationOwnerTest`, `PostProjectionCoordinatorTest`,
`HomePagingDemandTest`, `RichTextModelTest`. Fixture users that need new
fixture imports after a move: `HomeFeedTest`, `NavigationTest`,
`SignInScreenTest`, `DetailActionPolicyTest`, `ReplyComposerTest`,
`SearchPanelRestorationTest`, `ShellCharacterizationTest`,
`WideNavigationTest`. Remaining root tests need per-file owner mapping
before the move. T1c may split further if the fixture moves prove large.

# Verification

Run focused suites first, then the full gate:

```powershell
$env:GRADLE_OPTS="-Dorg.gradle.daemon=false"
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.ui.emoji.EmojiCatalogViewModelTest" --tests "me.foxtails.palustris.ui.posts.PostActionOwnerTest"
.\gradlew.bat --no-daemon --console=plain test assembleRelease
.\gradlew.bat --no-daemon --console=plain :app:ktlintCheck
```

Close standard input. Set an explicit timeout for each Gradle call.

# Slices

| Slice | Behavior | Verification |
| --- | --- | --- |
| T1a | Merge the two duplicate test classes into `ui.emoji` and `ui.posts`. | Focused tests plus full gate plus `ktlintCheck` |
| T1b | Move data and domain owner tests into mirrored packages. | Focused tests plus full gate |
| T1c | Move UI feature tests into mirrored packages. | Focused tests plus full gate |
| T1d | Move remaining root tests and fixtures into mirrored packages. | Full gate plus `lintDebug` |

# Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- The ktlint baseline holds 435 entries across 219 files. Moved files may expose new findings outside the baseline. Keep merged files import-clean and ordered.

# Last safe commit

`f65a10a` "Move data and domain owner tests into mirrored packages".
