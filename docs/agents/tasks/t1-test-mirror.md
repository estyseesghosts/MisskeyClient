# Objective

Mirror test packages to production packages. Merge the two duplicate test class names. Keep behavior unchanged. Keep the repository green.

This task is larger than one safe implementation slice. It divides into T1a through T1f below. The fully-qualified-name cleanup stays deferred to its own task.

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
- T1c homes follow the production owner package. `ui.notifications` owns the three notification tests. `ui.settings` owns `SettingsRouteRestorationTest`. `ui.posts` owns the two post tests. `ui.shell` owns `PostProjectionCoordinatorTest` and `ShellCharacterizationTest`. `ui.feed` owns `HomePagingDemandTest` and `HomeFeedTest`. `ui.navigation` owns `NavigationTest`. Flat `ui` owns `SignInScreenTest` and `DetailActionPolicyTest` because their production owners are `ui/SignInScreen.kt` and `ui/DetailActionPolicy.kt`. `ui.composer`, `ui.search`, and `ui.large` own `ReplyComposerTest`, `SearchPanelRestorationTest`, and `WideNavigationTest`.
- `RichTextModelTest` belongs to `domain`, not `ui`. It exercises only domain classes: `MediaRequestPolicy`, `PostReactionReducer`, `PostInteractionCounts`, and `CustomEmoji`. The task file grouped it with the UI subjects, but test homes mirror production homes, so the code evidence wins.
- T1d splits from T1e. T1d moves the three shared fixtures. T1e moves the remaining root tests. The remaining root tests do not use the three fixtures, so the split stays clean.
- Fixture homes mirror the feature they stub. `AppShellFixtures` -> `ui.shell`. `ComposerFeatureFixtures` -> `ui.composer`. `HomeFeatureFixtures` -> `ui.feed`. `HomeFeatureFixtures` imports `AppShellFixtures`, so it gains an `ui.shell` import.
- T1e moves only single-owner feature tests. Each target package matches the dominant production owner. `MainActivity` stays in the root package, so the eight screen tests gain a `MainActivity` import.
- T1f holds the remaining cross-cutting and multi-adapter tests. They need an explicit home decision. The candidates are `Api29CompatibilityTest`, `CrossCuttingTest`, `DirectMessageSourceTest`, `LocalizationResourceTest`, `MastodonIntegrationTest`, `MastodonNotificationSyncTest`, `MastodonSourceContractTest`, `ModerationServiceTest`, `NotificationAdapterContractTest`, `NotificationContractTest`, `ProfileSourceContractTest`, `ProtocolFixtureValidationTest`, `ProductIdentityTest`, `SocialSourceContractTest`, and `WebSocketTransportTest`. The source-contract cluster (`SocialSourceContractTest` plus `MastodonSourceContractTest` and `MisskeyIntegrationTest` users) must move together or stay together. Tests with no single production owner may stay at the root package. `ProductIdentityTest` stays at the root package because `ProductIdentity.kt` is in the root package.

# Completed

- T1a — Merged the two duplicate test classes into mirrored packages. Commit `7018105`. `ui/emoji/EmojiCatalogViewModelTest.kt` holds 8 tests. `ui/posts/PostActionOwnerTest.kt` holds 5 tests. Focused tests, `test assembleRelease`, and `ktlintCheck` pass.
- T1b — Moved 43 data and domain owner tests into mirrored packages. Commit `f65a10a`. Each move changes the package line only. The moves exposed pre-existing style debt under new paths, so the slice also regenerates `app/ktlint-baseline.xml` (old root paths out, new mirrored paths in, zero `no-wildcard-imports` entries). `test assembleRelease` and `ktlintCheck` pass. UI subjects (`NotificationLaunchHost`, `NotificationRouteResolver`, `NotificationSettingsStorageReset`, `SettingsRouteRestoration`, `PostInteractionExecutionAuthority`) stay in root for T1c. Multi-adapter contracts stay in root for T1d.
- T1c — Moved 17 UI feature tests into mirrored packages. Commit `42e85e7`. Physical file moves plus the package line. The 8 fixture users also gain `AppShellFixtures`, `ComposerFeatureFixtures` or `HomeFeatureFixtures`, and `MainActivity` imports because those declarations stay in the root package. The slice regenerates `app/ktlint-baseline.xml` (old root paths out, new mirrored paths in, zero `no-wildcard-imports` entries). Focused tests, `test assembleRelease`, and `ktlintCheck` pass.
- T1d — Moved the three shared test fixtures into mirrored packages. Commit `404b356`. `AppShellFixtures` -> `ui.shell`. `ComposerFeatureFixtures` -> `ui.composer`. `HomeFeatureFixtures` -> `ui.feed`. The eight users point at the new packages and drop the now same-package imports. `HomeFeatureFixtures` imports `AppShellFixtures`. The slice regenerates `app/ktlint-baseline.xml` under the new paths (432 entries across 216 files, zero `no-wildcard-imports` entries). Focused tests, `test assembleRelease`, `ktlintCheck`, and `lintDebug` pass.
- T1e — Moved 34 single-owner root tests into mirrored packages. Commit `00a49ff`. UI homes: `ui.localization` (2), `ui.session` (3), `ui.directmessages` (2), `ui.feed` (2), `ui.settings` (4), `ui.large` (1), `ui.media` (3), `ui.motion` (2), `ui.notifications` (2), `ui.photogrid` (2), `ui.profile` (2), `ui.saved` (2), `ui.thread` (1), `ui.navigation` (2), `ui.composer` (1), and flat `ui` (2). Data home: `data.notifications.push` (1). Eight screen tests gain a `MainActivity` import. The slice regenerates `app/ktlint-baseline.xml` under the new paths (431 entries across 216 files, zero `no-wildcard-imports` entries). Focused tests, `test assembleRelease`, `ktlintCheck`, and `lintDebug` pass.

# Current slice

T1f is next. Give the cross-cutting and multi-adapter tests an explicit home.

# Files involved

T1f holds 15 files. Each needs a home decision before a move. The candidates:

`Api29CompatibilityTest`, `CrossCuttingTest`, `DirectMessageSourceTest`,
`LocalizationResourceTest`, `MastodonIntegrationTest`,
`MastodonNotificationSyncTest`, `MastodonSourceContractTest`,
`ModerationServiceTest`, `NotificationAdapterContractTest`,
`NotificationContractTest`, `ProfileSourceContractTest`,
`ProtocolFixtureValidationTest`, `ProductIdentityTest`,
`SocialSourceContractTest`, `WebSocketTransportTest`.

`SocialSourceContractTest.kt` declares both `SocialSourceContractTest` and
`MisskeySourceContractTest`. `MastodonSourceContractTest` and
`MisskeyIntegrationTest` extend them. Move the cluster together or keep it
together. `ProductIdentityTest` stays at the root package because
`ProductIdentity.kt` is in the root package.

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
| T1d | Move the shared test fixtures into mirrored packages. | Focused tests plus full gate plus `lintDebug` |
| T1e | Move the single-owner root tests into mirrored packages. | Focused tests plus full gate plus `lintDebug` |
| T1f | Decide and move the cross-cutting and multi-adapter tests. | Focused tests plus full gate plus `lintDebug` |

# Blockers

- No emulator or device is reachable. Connected instrumentation stays unverified.
- Live-server and signed-release behavior stay unverified.
- The Android 15 system-bar instrumentation failure stays in `logs/BUGS.txt`.
- The ktlint baseline holds 431 entries across 216 files. Moved files may expose new findings outside the baseline. Keep merged files import-clean and ordered.

# Last safe commit

`00a49ff` "Move remaining single-owner root tests into mirrored packages".
